package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.document.PdfJobType;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs Word/PPTX/Excel/HTML &lt;-&gt; PDF conversions via headless LibreOffice
 * ({@code soffice --convert-to}) on the {@code pdfTaskExecutor} pool. Never reads
 * {@code SecurityContextHolder} (async threads don't inherit it).
 *
 * <p>The result is written to a temp file referenced from the job document, never persisted as
 * a {@code FileMetaDataDocument} - it is deleted after a single download or once it expires.
 * See {@link PdfResultCleanupService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "LIBREOFFICE-CONVERSION")
public class LibreOfficeConversionService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private static final Map<PdfJobType, String> TARGET_EXTENSION = new EnumMap<>(PdfJobType.class);
    private static final Map<PdfJobType, String> TARGET_CONTENT_TYPE = new EnumMap<>(PdfJobType.class);

    /**
     * LibreOffice imports a standalone PDF as a Draw document by default, which has no
     * export filter into Writer-family formats (docx/html) - "no export filter" errors
     * result. Forcing the Writer PDF import filter makes it reconstruct an editable Writer
     * document instead, which docx/html import must both go through. PPTX/XLSX targets are
     * intentionally not supported here since neither Draw nor Writer has any export path
     * into slide/spreadsheet formats.
     *
     * <p>Known limitation: {@code writer_pdf_import} reconstructs text using LibreOffice's own
     * PDF-parsing engine, which is unreliable for PDFs whose fonts are embedded as subsetted
     * Type0/CID fonts (the norm for PDFs exported from Word/Google Docs/Chrome "Print to PDF").
     * Confirmed via PDFBox, which extracts such PDFs correctly - the source PDFs are not at
     * fault. Symptoms range from garbled characters (PDF_TO_HTML) to a blank document
     * (PDF_TO_WORD). There is no soffice CLI flag that fixes this; a reliable fix would require
     * replacing this filter with a custom PDFBox-based text extraction path for these two job
     * types, which is a larger change deliberately deferred for now.
     */
    private static final Set<PdfJobType> PDF_SOURCE_JOB_TYPES = EnumSet.of(PdfJobType.PDF_TO_WORD, PdfJobType.PDF_TO_HTML);

    static {
        TARGET_EXTENSION.put(PdfJobType.PDF_TO_WORD, "docx");
        TARGET_EXTENSION.put(PdfJobType.PDF_TO_HTML, "html");
        TARGET_EXTENSION.put(PdfJobType.WORD_TO_PDF, "pdf");
        TARGET_EXTENSION.put(PdfJobType.PPTX_TO_PDF, "pdf");
        TARGET_EXTENSION.put(PdfJobType.EXCEL_TO_PDF, "pdf");
        TARGET_EXTENSION.put(PdfJobType.HTML_TO_PDF, "pdf");

        TARGET_CONTENT_TYPE.put(PdfJobType.PDF_TO_WORD, DOCX_CONTENT_TYPE);
        TARGET_CONTENT_TYPE.put(PdfJobType.PDF_TO_HTML, HTML_CONTENT_TYPE);
        TARGET_CONTENT_TYPE.put(PdfJobType.WORD_TO_PDF, PDF_CONTENT_TYPE);
        TARGET_CONTENT_TYPE.put(PdfJobType.PPTX_TO_PDF, PDF_CONTENT_TYPE);
        TARGET_CONTENT_TYPE.put(PdfJobType.EXCEL_TO_PDF, PDF_CONTENT_TYPE);
        TARGET_CONTENT_TYPE.put(PdfJobType.HTML_TO_PDF, PDF_CONTENT_TYPE);
    }

    /**
     * Each soffice invocation spawns a fairly heavy OS process - bound how many run
     * concurrently regardless of how many async threads are queued.
     */
    private static final int MAX_CONCURRENT_CONVERSIONS = 2;
    private static final int RESULT_TTL_HOURS = 1;
    private final Semaphore sofficeSemaphore = new Semaphore(MAX_CONCURRENT_CONVERSIONS);

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${libreoffice.soffice-path}")
    private String sofficePath;

    @Value("${libreoffice.timeout-seconds}")
    private long timeoutSeconds;

    @Async("pdfTaskExecutor")
    public void processConversionJob(String jobId, String fileId, PdfJobType jobType) {
        PdfJobDocument job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Conversion job {} not found, aborting processing", jobId);
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

            String targetExtension = TARGET_EXTENSION.get(jobType);
            String targetContentType = TARGET_CONTENT_TYPE.get(jobType);
            if (targetExtension == null) {
                throw new IllegalStateException("Unsupported LibreOffice conversion job type: " + jobType);
            }
            boolean useWriterPdfImportFilter = PDF_SOURCE_JOB_TYPES.contains(jobType);

            Path outDir = runSoffice(sourceFile, targetExtension, useWriterPdfImportFilter);
            try {
                File[] produced = outDir.toFile().listFiles();
                if (produced == null || produced.length == 0) {
                    throw new RuntimeException("LibreOffice did not produce any output file");
                }
                Path uploadPath = resolveUploadPath();
                if (produced.length == 1) {
                    // Single self-contained output file (e.g. docx, pdf).
                    String outputName = UUID.randomUUID() + "." + targetExtension;
                    Path targetPath = uploadPath.resolve(outputName);
                    Files.move(produced[0].toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    applyResult(job, source.getName(), targetPath, targetExtension, targetContentType);
                } else {
                    // Some exports (e.g. HTML) split into a main file plus separate embedded
                    // image assets - package everything together so nothing gets dropped.
                    String outputName = UUID.randomUUID() + ".zip";
                    Path targetPath = uploadPath.resolve(outputName);
                    zipFiles(produced, targetPath);
                    applyResult(job, source.getName(), targetPath, "zip", ZIP_CONTENT_TYPE);
                }
            } finally {
                deleteQuietly(outDir.toFile());
            }

            job.setStatus(PdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Conversion job {} failed", jobId, e);
            markFailed(job, e);
        } finally {
            pdfJobRepository.save(job);
        }
    }

    /**
     * Converts {@code sourceFile} to {@code targetExtension} via headless LibreOffice and
     * returns the temp output directory (may contain more than one produced file). Each
     * call gets its own isolated user profile dir so concurrent soffice instances never
     * collide on a shared profile lock (the #1 cause of headless LibreOffice hangs).
     */
    private Path runSoffice(File sourceFile, String targetExtension, boolean useWriterPdfImportFilter)
            throws IOException, InterruptedException {
        if (!sofficeSemaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
            throw new RuntimeException("Conversion service is busy, please try again shortly");
        }
        Path profileDir = Files.createTempDirectory("lo-profile-");
        Path outDir = Files.createTempDirectory("lo-out-");
        try {
            String profileUrl = "file:///" + profileDir.toString().replace('\\', '/');
            List<String> command = new ArrayList<>();
            command.add(sofficePath);
            command.add("--headless");
            command.add("--norestore");
            command.add("-env:UserInstallation=" + profileUrl);
            if (useWriterPdfImportFilter) {
                command.add("--infilter=writer_pdf_import");
            }
            command.add("--convert-to");
            command.add(targetExtension);
            command.add("--outdir");
            command.add(outDir.toString());
            command.add(sourceFile.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes());
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("LibreOffice conversion timed out after " + timeoutSeconds + "s");
            }
            if (process.exitValue() != 0) {
                log.error("soffice exited with {}: {}", process.exitValue(), output);
                throw new RuntimeException("LibreOffice conversion failed: " + output.trim());
            }

            File[] produced = outDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith("." + targetExtension));
            if (produced == null || produced.length == 0) {
                throw new RuntimeException("LibreOffice did not produce an output file: " + output.trim());
            }
            return outDir;
        } finally {
            sofficeSemaphore.release();
            deleteQuietly(profileDir.toFile());
        }
    }

    private void zipFiles(File[] files, Path targetZipPath) throws IOException {
        try (OutputStream os = Files.newOutputStream(targetZipPath);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (File file : files) {
                zos.putNextEntry(new ZipEntry(file.getName()));
                try (InputStream in = new FileInputStream(file)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    private void deleteQuietly(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private Path resolveUploadPath() throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        return uploadPath;
    }

    private void applyResult(PdfJobDocument job, String sourceName, Path targetPath, String extension, String contentType) throws IOException {
        job.setResultFilePath(targetPath.toString());
        job.setResultFileName(buildResultFileName(sourceName, extension));
        job.setResultContentType(contentType);
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
