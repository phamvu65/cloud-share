package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the actual DOCX translation work on the {@code pdfTaskExecutor} pool. Never reads
 * {@code SecurityContextHolder} (async threads don't inherit it).
 *
 * <p>The result is written to a temp file referenced from the job document, never persisted as
 * a {@code FileMetaDataDocument} - it is deleted after a single download or once it expires.
 * See {@link PdfResultCleanupService}.
 *
 * The original document is edited in place: each paragraph's combined text is translated
 * and written back into its first text-bearing run (which carries the paragraph's
 * formatting), and the paragraph's remaining runs are dropped.
 * This keeps fonts, alignment, lists, tables and images intact - only per-run formatting
 * that varies within a single paragraph (e.g. a bolded word mid-sentence) is not preserved.
 *
 * Every translatable paragraph in the document is collected first and sent to
 * {@link TranslationClient} as one batched call (internally chunked) instead of one call
 * per paragraph - the self-hosted model runs on CPU, so per-call overhead needs to be
 * amortized across many paragraphs rather than paid per paragraph.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "DOCX-TRANSLATION")
public class DocxTranslationService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int RESULT_TTL_HOURS = 1;

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final TranslationClient translationClient;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private record TranslatableParagraph(XWPFParagraph paragraph, String originalText, int keepRunIndex) {
    }

    @Async("pdfTaskExecutor")
    public void processTranslateJob(String jobId, String fileId, String sourceLanguage, String targetLanguage) {
        PdfJobDocument job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Translate job {} not found, aborting processing", jobId);
            return;
        }
        markProcessing(job);
        try {
            FileMetaDataDocument source = fileMetaDataRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

            File sourceFile = new File(source.getFileLocation());
            if (!sourceFile.isFile()) {
                throw new RuntimeException("Source file is missing on disk: " + source.getFileLocation());
            }

            XWPFDocument document;
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                document = new XWPFDocument(fis);
            } catch (IOException e) {
                log.error("Translate job {}: unreadable/corrupt DOCX at {}", jobId, source.getFileLocation(), e);
                throw new RuntimeException("This file could not be read as a valid DOCX (it may be corrupted or in an unsupported format)");
            }
            try (document) {
                List<TranslatableParagraph> units = new ArrayList<>();
                collectParagraphs(document.getParagraphs(), units);
                collectTables(document.getTables(), units);
                for (XWPFHeader header : document.getHeaderList()) {
                    collectParagraphs(header.getParagraphs(), units);
                    collectTables(header.getTables(), units);
                }
                for (XWPFFooter footer : document.getFooterList()) {
                    collectParagraphs(footer.getParagraphs(), units);
                    collectTables(footer.getTables(), units);
                }

                if (!units.isEmpty()) {
                    List<String> originalTexts = units.stream().map(TranslatableParagraph::originalText).toList();
                    List<String> translatedTexts = translationClient.translateBatch(originalTexts, sourceLanguage, targetLanguage);
                    for (int i = 0; i < units.size(); i++) {
                        applyTranslation(units.get(i), translatedTexts.get(i));
                    }
                }

                Path uploadPath = resolveUploadPath();
                String outputName = UUID.randomUUID() + ".docx";
                Path targetPath = uploadPath.resolve(outputName);
                try (OutputStream os = Files.newOutputStream(targetPath)) {
                    document.write(os);
                }
                applyResult(job, source.getName(), targetPath, "docx");
            }

            job.setStatus(PdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Translate job {} failed", jobId, e);
            markFailed(job, e);
        } finally {
            pdfJobRepository.save(job);
        }
    }

    private void collectTables(List<XWPFTable> tables, List<TranslatableParagraph> out) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    collectParagraphs(cell.getParagraphs(), out);
                    collectTables(cell.getTables(), out);
                }
            }
        }
    }

    private void collectParagraphs(List<XWPFParagraph> paragraphs, List<TranslatableParagraph> out) {
        for (XWPFParagraph paragraph : paragraphs) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs.isEmpty()) {
                continue;
            }
            String originalText = paragraph.getText();
            if (!StringUtils.hasText(originalText)) {
                continue;
            }
            int keepIndex = -1;
            for (int i = 0; i < runs.size(); i++) {
                if (StringUtils.hasText(runs.get(i).getText(0))) {
                    keepIndex = i;
                    break;
                }
            }
            if (keepIndex == -1) {
                continue;
            }
            out.add(new TranslatableParagraph(paragraph, originalText, keepIndex));
        }
    }

    private void applyTranslation(TranslatableParagraph unit, String translatedText) {
        List<XWPFRun> runs = unit.paragraph().getRuns();
        runs.get(unit.keepRunIndex()).setText(translatedText, 0);

        for (int i = runs.size() - 1; i >= 0; i--) {
            if (i == unit.keepRunIndex()) {
                continue;
            }
            XWPFRun run = runs.get(i);
            if (run.getEmbeddedPictures().isEmpty()) {
                unit.paragraph().removeRun(i);
            } else if (StringUtils.hasText(run.getText(0))) {
                run.setText("", 0);
            }
        }
    }

    private Path resolveUploadPath() throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        return uploadPath;
    }

    private void applyResult(PdfJobDocument job, String sourceName, Path targetPath, String extension) throws IOException {
        job.setResultFilePath(targetPath.toString());
        job.setResultFileName(buildResultFileName(sourceName, extension));
        job.setResultContentType(DOCX_CONTENT_TYPE);
        job.setResultSize(Files.size(targetPath));
        job.setResultExpiresAt(LocalDateTime.now().plusHours(RESULT_TTL_HOURS));
    }

    private String buildResultFileName(String sourceName, String extension) {
        String base = (sourceName != null && !sourceName.isBlank()) ? sourceName : "result";
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + "." + extension;
    }

    private void markProcessing(PdfJobDocument job) {
        job.setStatus(PdfJobStatus.PROCESSING);
        pdfJobRepository.save(job);
    }

    private void markFailed(PdfJobDocument job, Exception e) {
        job.setStatus(PdfJobStatus.FAILED);
        job.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }
}
