package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.PdfJobDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PdfJobRepository extends MongoRepository<PdfJobDocument, String> {
    List<PdfJobDocument> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<PdfJobDocument> findByIdAndUserId(String id, String userId);
}
