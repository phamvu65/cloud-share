package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetaDataRepository extends MongoRepository<FileMetaDataDocument, String> {
    List<FileMetaDataDocument> findByUserId(String userId);

    Optional<FileMetaDataDocument> findByIdAndIsPublic(String id, Boolean isPublic);
}
