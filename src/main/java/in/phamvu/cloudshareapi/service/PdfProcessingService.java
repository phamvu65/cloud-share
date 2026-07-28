package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the actual PDFBox compression work on the {@code pdfTaskExecutor} pool. Never reads
 * {@code SecurityContextHolder} (async threads don't inherit it) - userId is always
 * passed in explicitly by the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "PDF-PROCESSING")
public class PdfProcessingService {

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Async("pdfTaskExecutor")
    public void processCompressJob(String jobId, String userId, String fileId, int quality) {
        PdfJobDocument job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Compress job {} not found, aborting processing", jobId);
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
            PDDocument document;
            try {
                document = Loader.loadPDF(sourceFile);
            } catch (IOException e) {
                log.error("Compress job {}: unreadable/corrupt PDF at {}", jobId, source.getFileLocation(), e);
                throw new RuntimeException("This file could not be read as a valid PDF (it may be corrupted or in an unsupported format)");
            }
            try (document) {
                for (PDPage page : document.getPages()) {
                    recompressImages(document, page, quality);
                }

                Path uploadPath = resolveUploadPath();
                String outputName = UUID.randomUUID() + ".pdf";
                Path targetPath = uploadPath.resolve(outputName);
                document.save(targetPath.toFile());
                resultId = saveOutputFile(userId, targetPath, outputName);
            }

            job.setResultFileId(resultId);
            job.setStatus(PdfJobStatus.COMPLETED);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Compress job {} failed", jobId, e);
            markFailed(job, e);
        } finally {
            pdfJobRepository.save(job);
        }
    }

    /**
     * Only shrinks image-heavy PDFs (photos, scans) by re-encoding embedded raster images
     * at a lower JPEG quality. Text/vector-only PDFs see negligible size reduction since
     * there are no raster images to recompress.
     */
    private void recompressImages(PDDocument document, PDPage page, int quality) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return;
        }
        COSDictionary xobjects = (COSDictionary) resources.getCOSObject().getDictionaryObject(COSName.XOBJECT);
        if (xobjects == null) {
            return;
        }

        List<COSName> imageNames = new ArrayList<>();
        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject) {
                imageNames.add(name);
            }
        }

        for (COSName name : imageNames) {
            PDImageXObject image = (PDImageXObject) resources.getXObject(name);
            BufferedImage bufferedImage = image.getImage();
            PDImageXObject compressed = JPEGFactory.createFromImage(document, bufferedImage, quality / 100f);
            xobjects.setItem(name, compressed);
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
                .type("application/pdf")
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
