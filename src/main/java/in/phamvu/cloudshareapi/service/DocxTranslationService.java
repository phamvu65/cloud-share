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
import java.util.List;
import java.util.UUID;

/**
 * Runs the actual DOCX translation work on the {@code pdfTaskExecutor} pool. Never reads
 * {@code SecurityContextHolder} (async threads don't inherit it) - userId is always
 * passed in explicitly by the caller.
 *
 * The original document is edited in place: each paragraph's combined text is translated
 * and written back into its first text-bearing run (which carries the paragraph's
 * formatting), and the paragraph's remaining runs are dropped.
 * This keeps fonts, alignment, lists, tables and images intact - only per-run formatting
 * that varies within a single paragraph (e.g. a bolded word mid-sentence) is not preserved.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "DOCX-TRANSLATION")
public class DocxTranslationService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final TranslationClient translationClient;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Async("pdfTaskExecutor")
    public void processTranslateJob(String jobId, String userId, String fileId, String sourceLanguage, String targetLanguage) {
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

            String resultId;
            XWPFDocument document;
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                document = new XWPFDocument(fis);
            } catch (IOException e) {
                log.error("Translate job {}: unreadable/corrupt DOCX at {}", jobId, source.getFileLocation(), e);
                throw new RuntimeException("This file could not be read as a valid DOCX (it may be corrupted or in an unsupported format)");
            }
            try (document) {
                translateParagraphs(document.getParagraphs(), sourceLanguage, targetLanguage);
                translateTables(document.getTables(), sourceLanguage, targetLanguage);
                for (XWPFHeader header : document.getHeaderList()) {
                    translateParagraphs(header.getParagraphs(), sourceLanguage, targetLanguage);
                    translateTables(header.getTables(), sourceLanguage, targetLanguage);
                }
                for (XWPFFooter footer : document.getFooterList()) {
                    translateParagraphs(footer.getParagraphs(), sourceLanguage, targetLanguage);
                    translateTables(footer.getTables(), sourceLanguage, targetLanguage);
                }

                Path uploadPath = resolveUploadPath();
                String outputName = UUID.randomUUID() + ".docx";
                Path targetPath = uploadPath.resolve(outputName);
                try (OutputStream os = Files.newOutputStream(targetPath)) {
                    document.write(os);
                }
                resultId = saveOutputFile(userId, targetPath, outputName);
            }

            job.setResultFileId(resultId);
            job.setStatus(PdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Translate job {} failed", jobId, e);
            markFailed(job, e);
        } finally {
            pdfJobRepository.save(job);
        }
    }

    private void translateTables(List<XWPFTable> tables, String sourceLanguage, String targetLanguage) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    translateParagraphs(cell.getParagraphs(), sourceLanguage, targetLanguage);
                    translateTables(cell.getTables(), sourceLanguage, targetLanguage);
                }
            }
        }
    }

    private void translateParagraphs(List<XWPFParagraph> paragraphs, String sourceLanguage, String targetLanguage) {
        for (XWPFParagraph paragraph : paragraphs) {
            translateParagraph(paragraph, sourceLanguage, targetLanguage);
        }
    }

    private void translateParagraph(XWPFParagraph paragraph, String sourceLanguage, String targetLanguage) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return;
        }
        String originalText = paragraph.getText();
        if (!StringUtils.hasText(originalText)) {
            return;
        }

        int keepIndex = -1;
        for (int i = 0; i < runs.size(); i++) {
            if (StringUtils.hasText(runs.get(i).getText(0))) {
                keepIndex = i;
                break;
            }
        }
        if (keepIndex == -1) {
            return;
        }

        String translatedText = translationClient.translate(originalText, sourceLanguage, targetLanguage);
        runs.get(keepIndex).setText(translatedText, 0);

        for (int i = runs.size() - 1; i >= 0; i--) {
            if (i == keepIndex) {
                continue;
            }
            XWPFRun run = runs.get(i);
            if (run.getEmbeddedPictures().isEmpty()) {
                paragraph.removeRun(i);
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

    private String saveOutputFile(String userId, Path filePath, String displayName) throws IOException {
        FileMetaDataDocument document = FileMetaDataDocument.builder()
                .userId(userId)
                .fileLocation(filePath.toString())
                .name(displayName)
                .size(Files.size(filePath))
                .type(DOCX_CONTENT_TYPE)
                .isPublic(false)
                .build();
        return fileMetaDataRepository.save(document).getId();
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