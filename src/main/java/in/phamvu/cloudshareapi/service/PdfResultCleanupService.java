package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PDF job results (compress/convert/translate) are deliberately never kept as permanent
 * {@code FileMetaDataDocument}s - this service is what actually removes the temp result file
 * (and, once nothing else needs it, the source file too) from disk and Mongo. It runs in two
 * ways:
 * <ul>
 *   <li>{@link #purgeJobFiles(PdfJobDocument)} - called right after a successful one-time
 *   download (see {@link PdfJobService#downloadResult(String)}).</li>
 *   <li>{@link #purgeExpiredResults()} - a scheduled sweep that catches results nobody ever
 *   downloaded, once {@link PdfJobDocument#getResultExpiresAt()} passes.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "PDF-RESULT-CLEANUP")
public class PdfResultCleanupService {

    private static final List<PdfJobStatus> ACTIVE_STATUSES = List.of(PdfJobStatus.PENDING, PdfJobStatus.PROCESSING);
    private static final long SWEEP_INTERVAL_MILLIS = 15 * 60 * 1000L;

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;

    /**
     * Deletes the job's result file and, if no other pending/processing job still needs it,
     * the source file it was generated from. Safe to call more than once for the same job.
     */
    public void purgeJobFiles(PdfJobDocument job) {
        deleteQuietly(job.getResultFilePath());
        job.setResultFilePath(null);
        job.setResultFileName(null);
        job.setResultContentType(null);
        job.setResultSize(null);
        job.setResultExpiresAt(null);

        String inputFileId = job.getInputFileId();
        if (inputFileId != null) {
            long stillNeeded = pdfJobRepository.countByInputFileIdAndStatusInAndIdNot(inputFileId, ACTIVE_STATUSES, job.getId());
            if (stillNeeded == 0) {
                fileMetaDataRepository.findById(inputFileId).ifPresent(this::deleteSourceFile);
            }
        }
        pdfJobRepository.save(job);
    }

    private void deleteSourceFile(FileMetaDataDocument source) {
        deleteQuietly(source.getFileLocation());
        fileMetaDataRepository.deleteById(source.getId());
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS)
    public void purgeExpiredResults() {
        List<PdfJobDocument> expired = pdfJobRepository.findByResultFilePathIsNotNullAndResultExpiresAtBefore(LocalDateTime.now());
        for (PdfJobDocument job : expired) {
            purgeJobFiles(job);
        }
        if (!expired.isEmpty()) {
            log.info("Purged {} expired PDF job result(s)", expired.size());
        }
    }

    private void deleteQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("Failed to delete file at {}", path, e);
        }
    }
}
