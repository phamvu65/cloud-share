package in.phamvu.cloudshareapi.repository;

import in.phamvu.cloudshareapi.document.PaymentTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {

    List<PaymentTransaction> findByUserIdAndStatusOrderByTransactionDateDesc(String userId, String status);

    Optional<PaymentTransaction> findByOrderId(String orderId);
}
