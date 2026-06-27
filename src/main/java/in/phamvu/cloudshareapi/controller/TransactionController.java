package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.document.PaymentTransaction;
import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.repository.PaymentTransactionRepository;
import in.phamvu.cloudshareapi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getUserTransactions() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = userDetails.getUsername();
        List<PaymentTransaction> transactionList= paymentTransactionRepository.findByClerkIdAndStatusOrderByTransactionDateDesc(userId, "SUCCESS");
        return ResponseEntity.ok(transactionList);
    }
}
