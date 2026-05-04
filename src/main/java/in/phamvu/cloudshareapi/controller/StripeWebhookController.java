package in.phamvu.cloudshareapi.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import in.phamvu.cloudshareapi.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;

    @Value( "${stripe.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("stripe-signature") String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            switch (event.getType()){
                case "payment_intent.succeeded":
                    PaymentIntent successIntent = (PaymentIntent) event.getDataObjectDeserializer()
                            .getObject().orElseThrow();

                    String clerkId = successIntent.getMetadata().get("clerk_id");
                    String planId = successIntent.getMetadata().get("plan_id");

                    paymentService.handlePaymentSuccess(successIntent.getId(), clerkId, planId);
                    break;
                case "payment_intent.payment_failed":
                    PaymentIntent failedIntent = (PaymentIntent) event.getDataObjectDeserializer()
                            .getObject().orElseThrow();

                    paymentService.handlePaymentFailed(failedIntent.getId());
                    break;
            }
            return ResponseEntity.ok("OK");

        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }

    }
}
