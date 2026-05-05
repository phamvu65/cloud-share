package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.document.PaymentTransaction;
import in.phamvu.cloudshareapi.document.ProfileDocument;
import in.phamvu.cloudshareapi.repository.PaymentTransactionRepository;
import in.phamvu.cloudshareapi.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<?> getUserTransactions() {
        ProfileDocument currenProfile= profileService.getCurrenProfile();
        String clerkId = currenProfile.getClerkId();

        List<PaymentTransaction> transactionList= paymentTransactionRepository.findByClerkIdAndStatusOrderByTransactionDateDesc(clerkId, "SUCCESS");
        return ResponseEntity.ok(transactionList);
    }
}
