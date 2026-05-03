package in.phamvu.cloudshareapi.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import in.phamvu.cloudshareapi.document.PaymentTransaction;
import in.phamvu.cloudshareapi.document.ProfileDocument;
import in.phamvu.cloudshareapi.dto.PaymentDTO;
import in.phamvu.cloudshareapi.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public PaymentDTO createOrder(PaymentDTO paymentDTO) {
        try {
            ProfileDocument currentProfile = profileService.getCurrenProfile();
            String clerkId = currentProfile.getClerkId();
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", paymentDTO.getAmount());
            orderRequest.put("currency", paymentDTO.getCurrency());
            orderRequest.put("receipt", "order_"+System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");

            //create pending transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .clerkId(clerkId)
                    .orderId(orderId)
                    .planId(paymentDTO.getPlanId())
                    .amount(paymentDTO.getAmount())
                    .currency(paymentDTO.getCurrency())
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now())
                    .userEmail(currentProfile.getEmail())
                    .userName(currentProfile.getFirstName()+" "+currentProfile.getLastName())
                    .build();

            paymentTransactionRepository.save(transaction);

            return PaymentDTO.builder()
                    .orderId(orderId)
                    .success(true)
                    .message("Order created successfully")
                    .build();

        }catch (Exception e) {
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error creating order: "+e.getMessage())
                    .build();
        }
    }
}
