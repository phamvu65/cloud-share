package in.phamvu.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDTO {
    private String planId;
    private Long amount;
    private String currency;
    private String clientSecret;
    private Integer credits;
    private Boolean success;
    private String message;
    private String orderId;
}
