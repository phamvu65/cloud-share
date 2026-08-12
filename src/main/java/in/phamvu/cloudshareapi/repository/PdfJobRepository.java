package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PdfJobRepository extends MongoRepository<PdfJobDocument, String> {
    List<PdfJobDocument> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Used to check whether another job still needs the same source file before it is deleted
     * as part of the "don't retain files" cleanup - see {@link in.phamvu.cloudshareapi.service.PdfResultCleanupService}.
     */
    long countByInputFileIdAndStatusInAndIdNot(String inputFileId, Collection<PdfJobStatus> statuses, String id);

    List<PdfJobDocument> findByResultFilePathIsNotNullAndResultExpiresAtBefore(LocalDateTime cutoff);
}
