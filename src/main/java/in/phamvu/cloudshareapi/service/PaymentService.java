package in.phamvu.cloudshareapi.service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import in.phamvu.cloudshareapi.document.PaymentTransaction;
import in.phamvu.cloudshareapi.document.ProfileDocument;
import in.phamvu.cloudshareapi.dto.PaymentDTO;
import in.phamvu.cloudshareapi.repository.PaymentTransactionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public PaymentDTO createOrder(PaymentDTO paymentDTO) {
        try {
            ProfileDocument currentProfile = profileService.getCurrenProfile();
            String clerkId = currentProfile.getClerkId();
//            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
//
//            JSONObject orderRequest = new JSONObject();
//            orderRequest.put("amount", paymentDTO.getAmount());
//            orderRequest.put("currency", paymentDTO.getCurrency());
//            orderRequest.put("receipt", "order_"+System.currentTimeMillis());
//
//            Order order = razorpayClient.orders.create(orderRequest);
//            String orderId = order.get("id");

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(paymentDTO.getAmount())
                    .setCurrency(paymentDTO.getCurrency())
                    .setDescription("Payment for "+paymentDTO.getPlanId())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("clerk_id", clerkId)
                    .putMetadata("plan_id", paymentDTO.getPlanId())
                    .putMetadata("user_email", currentProfile.getEmail())
                    .setReceiptEmail(currentProfile.getEmail())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            //create pending transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .clerkId(clerkId)
                    .orderId(paymentIntent.getId())
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
                    .orderId(paymentIntent.getId())
                    .clientSecret(paymentIntent.getClientSecret())
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

    public void handlePaymentSuccess(String paymentIntentId, String clerkId, String planId) {
        int creditsToadd = 0;
        String plan = "BASIC";

        switch (planId) {
            case "premium":
                creditsToadd = 500;
                plan = "PREMIUM";
                break;
            case "ultimate":
                creditsToadd = 5000;
                plan = "ULTIMATE";
                break;
        }

        if(creditsToadd > 0) {
            userCreditsService.addCredits(clerkId, creditsToadd, plan);
        }
    }

    public void handlePaymentFailed(String paymentIntentId){

    }

    private void updateTransactionStatus(String paymentIntentId, String status, Integer creditsToAdd) {
        paymentTransactionRepository.findByOrderId(paymentIntentId)
                .ifPresent(transaction -> {
                    transaction.setStatus(status);
                    transaction.setPaymentId(paymentIntentId);
                    if (creditsToAdd != null) {
                        transaction.setCreditsAdded(creditsToAdd);
                    }
                    paymentTransactionRepository.save(transaction);
                });
    }
}
