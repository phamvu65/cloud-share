package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.UserCredits;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserCreditsRepository extends MongoRepository<UserCredits, String> {
    Optional<UserCredits> findById(String id);
}
