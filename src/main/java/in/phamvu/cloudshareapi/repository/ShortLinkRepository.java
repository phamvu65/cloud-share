package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.ShortLinkDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository extends MongoRepository<ShortLinkDocument, String> {

    Optional<ShortLinkDocument> findByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);

    List<ShortLinkDocument> findByUserIdOrderByCreatedAtDesc(String userId);
}
