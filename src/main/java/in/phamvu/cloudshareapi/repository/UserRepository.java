package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProfileRepository extends MongoRepository<UserDocument, String> {

    Optional<UserDocument> findByEmail(String email);

    UserDocument findByClerkId(String clerkId);

    Boolean existsByClerkId(String clerkId);
}