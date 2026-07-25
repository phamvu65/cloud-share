package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.SessionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SessionRepository extends MongoRepository<SessionDocument, String> {

    List<SessionDocument> findByUserIdAndRevokedFalse(String userId);

    List<SessionDocument> findByUserIdAndRevokedFalseAndIdNot(String userId, String id);
}
