package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.document.PdfJobType;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

/**
 * Runs PDF &lt;-&gt; PNG/JPG conversions with PDFBox (no external process) on the
 * {@code pdfTaskExecutor} pool. Never reads {@code SecurityContextHolder} (async
 * threads don't inherit it) - userId is always passed in explicitly by the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "PDF-IMAGE-CONVERSION")
public class PdfImageConversionService {

    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final int RENDER_DPI = 150;

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Async("pdfTaskExecutor")
    public void processPdfToImageJob(String jobId, String userId, String fileId, PdfJobType jobType) {
        PdfJobDocument job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("PDF-to-image job {} not found, aborting processing", jobId);
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

            String formatName = jobType == PdfJobType.PDF_TO_JPG ? "jpg" : "png";

            String resultId;
            PDDocument document;
            try {
                document = Loader.loadPDF(sourceFile);
            } catch (IOException e) {
                log.error("PDF-to-image job {}: unreadable/corrupt PDF at {}", jobId, source.getFileLocation(), e);
                throw new RuntimeException("This file could not be read as a valid PDF (it may be corrupted or in an unsupported format)");
            }
            try (document) {
                PDFRenderer renderer = new PDFRenderer(document);
                Path uploadPath = resolveUploadPath();
                String outputName = UUID.randomUUID() + ".zip";
                Path targetPath = uploadPath.resolve(outputName);

                try (OutputStream os = Files.newOutputStream(targetPath);
                     ZipOutputStream zos = new ZipOutputStream(os)) {
                    int pageCount = document.getNumberOfPages();
                    for (int i = 0; i < pageCount; i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);
                        zos.putNextEntry(new ZipEntry("page-" + (i + 1) + "." + formatName));
                        ImageIO.write(image, formatName, zos);
                        zos.closeEntry();
                    }
                }
                resultId = saveOutputFile(userId, targetPath, outputName, ZIP_CONTENT_TYPE);
            }

            job.setResultFileId(resultId);
            job.setStatus(PdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("PDF-to-image job {} failed", jobId, e);
            markFailed(job, e);
        } finally {
            pdfJobRepository.save(job);
        }
    }

    @Async("pdfTaskExecutor")
    public void processImageToPdfJob(String jobId, String userId, String fileId, PdfJobType jobType) {
        PdfJobDocument job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Image-to-PDF job {} not found, aborting processing", jobId);
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

            BufferedImage image = ImageIO.read(sourceFile);
            if (image == null) {
                throw new RuntimeException("This file could not be read as a valid image (it may be corrupted or in an unsupported format)");
            }

            String resultId;
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
                document.addPage(page);

                PDImageXObject pdImage = jobType == PdfJobType.JPG_TO_PDF
                        ? JPEGFactory.createFromImage(document, image)
                        : LosslessFactory.createFromImage(document, image);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
                }

                Path uploadPath = resolveUploadPath();
                String outputName = UUID.randomUUID() + ".pdf";
                Path targetPath = uploadPath.resolve(outputName);
                document.save(targetPath.toFile());
                resultId = saveOutputFile(userId, targetPath, outputName, PDF_CONTENT_TYPE);
            }

            job.setResultFileId(resultId);
            job.setStatus(PdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Image-to-PDF job {} failed", jobId, e);
            markFailed(job, e);
        } finally {
            pdfJobRepository.save(job);
        }
    }

    private Path resolveUploadPath() throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        return uploadPath;
    }

    private String saveOutputFile(String userId, Path filePath, String displayName, String contentType) throws IOException {
        FileMetaDataDocument document = FileMetaDataDocument.builder()
                .userId(userId)
                .fileLocation(filePath.toString())
                .name(displayName)
                .size(Files.size(filePath))
                .type(contentType)
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
