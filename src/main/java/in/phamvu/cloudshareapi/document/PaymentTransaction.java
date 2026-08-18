package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "payment_transactions")
public class PaymentTransaction {
    private String id;
    @Indexed
    private String userId;
    @Indexed(unique = true)
    private String orderId;
    private String paymentId;
    private String planId;
    private long amount;
    private String currency;
    private int creditsAdded;
    private String status;
    private LocalDateTime transactionDate;

    private String userEmail;
    private String userName;
}
