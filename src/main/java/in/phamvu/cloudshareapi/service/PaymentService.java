package in.phamvu.cloudshareapi.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import in.phamvu.cloudshareapi.document.PaymentTransaction;
import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.PaymentDTO;
import in.phamvu.cloudshareapi.repository.PaymentTransactionRepository;
import in.phamvu.cloudshareapi.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserCreditsService userCreditsService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public PaymentDTO createOrder(PaymentDTO paymentDTO) {
        try {
            UserDetails userDetails = (UserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String clerkId = userDetails.getUsername();

            UserDocument userDocument = userRepository.findById(clerkId).orElseThrow(() -> new RuntimeException("User not found"));

            // Tạo Checkout Session (thay cho PaymentIntent)
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(frontendUrl + "/subscriptions?status=success")
                    .setCancelUrl(frontendUrl + "/subscriptions?status=cancel")
                    .setCustomerEmail(userDocument.getEmail())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(paymentDTO.getCurrency())
                                                    .setUnitAmount(paymentDTO.getAmount().longValue() * 100)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("CloudShare " + paymentDTO.getPlanId().substring(0, 1).toUpperCase() + paymentDTO.getPlanId().substring(1) + " Plan")
                                                                    .setDescription(getCreditsForPlan(paymentDTO.getPlanId()) + " credits")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("clerk_id", clerkId)
                    .putMetadata("plan_id", paymentDTO.getPlanId())
                    .putMetadata("user_email", userDocument.getEmail())
                    .build();

            Session session = Session.create(params);

            // Tạo pending transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .clerkId(clerkId)
                    .orderId(session.getId())
                    .planId(paymentDTO.getPlanId())
                    .amount(paymentDTO.getAmount())
                    .currency(paymentDTO.getCurrency())
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now())
                    .userEmail(userDocument.getEmail())
                    .userName(userDocument.getFirstName() + " " + userDocument.getLastName())
                    .build();

            paymentTransactionRepository.save(transaction);

            return PaymentDTO.builder()
                    .orderId(session.getId())
                    .checkoutUrl(session.getUrl())
                    .success(true)
                    .message("Checkout session created successfully")
                    .build();

        } catch (Exception e) {
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error creating order: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Được gọi từ StripeWebhookController khi thanh toán thành công
     */
    public void handlePaymentSuccess(String sessionId, String clerkId, String planId) {
        int creditsToAdd = getCreditsForPlan(planId);
        String plan = planId.toUpperCase();

        if (creditsToAdd > 0) {
            userCreditsService.addCredits(clerkId, creditsToAdd, plan);
            updateTransactionStatus(sessionId, "SUCCESS", creditsToAdd);
        }
    }

    public void handlePaymentFailed(String sessionId) {
        updateTransactionStatus(sessionId, "FAILED", null);
    }

    private int getCreditsForPlan(String planId) {
        switch (planId.toLowerCase()) {
            case "premium": return 500;
            case "ultimate": return 5000;
            default: return 0;
        }
    }

    private void updateTransactionStatus(String sessionId, String status, Integer creditsToAdd) {
        paymentTransactionRepository.findByOrderId(sessionId)
                .ifPresent(transaction -> {
                    transaction.setStatus(status);
                    transaction.setPaymentId(sessionId);
                    if (creditsToAdd != null) {
                        transaction.setCreditsAdded(creditsToAdd);
                    }
                    paymentTransactionRepository.save(transaction);
                });
    }
}