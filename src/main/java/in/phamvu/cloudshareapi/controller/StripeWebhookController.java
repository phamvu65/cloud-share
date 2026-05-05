package in.phamvu.cloudshareapi.controller;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import in.phamvu.cloudshareapi.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            System.out.println("Event type: " + event.getType());

                JSONObject jsonObject = new JSONObject(payload);
            JSONObject dataObject = jsonObject.getJSONObject("data").getJSONObject("object");

            switch (event.getType()) {
                case "checkout.session.completed":
                    String sessionId = dataObject.getString("id");
                    JSONObject metadata = dataObject.getJSONObject("metadata");

                    String clerkId = metadata.getString("clerk_id");
                    String planId = metadata.getString("plan_id");

                    System.out.println("Session ID: " + sessionId);
                    System.out.println("ClerkId: " + clerkId + ", PlanId: " + planId);

                    paymentService.handlePaymentSuccess(sessionId, clerkId, planId);
                    break;

                case "checkout.session.expired":
                    String expiredSessionId = dataObject.getString("id");
                    paymentService.handlePaymentFailed(expiredSessionId);
                    break;
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            System.err.println("Webhook error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("OK");
        }
    }
}