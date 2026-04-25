package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.UserCredits;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserCreditsRepository extends MongoRepository<UserCredits, String> {
}
