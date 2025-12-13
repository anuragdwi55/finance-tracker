package com.example.fintrack.controller;

import com.example.fintrack.dto.SavingsMonth;
import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.TransactionRepository;
import com.example.fintrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/insights")
public class InsightsController {

    private final TransactionRepository txRepo;
    private final UserRepository userRepo;
    private final RestClient client;

    public InsightsController(TransactionRepository txRepo, UserRepository userRepo, RestClient mlClient) {
        this.txRepo = txRepo;
        this.userRepo = userRepo;
        this.client = mlClient;
    }

    @GetMapping("/forecast")
    public ResponseEntity<?> forecast(Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();

        // Collect last 6 full months with any data (skip all-zero months)
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        List<SavingsMonth> months = new ArrayList<>();
        for (int i = 6; i >= 1; i--) {
            LocalDate start = now.minusMonths(i);
            LocalDate end = start.plusMonths(1).minusDays(1);
            List<Transaction> txs = txRepo.findByUserAndDateBetween(u, start, end);

            var incomeBD = txs.stream().filter(t -> t.getCategory().name().equals("INCOME"))
                    .map(Transaction::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            var expenseBD = txs.stream().filter(t -> !t.getCategory().name().equals("INCOME"))
                    .map(Transaction::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            double income = incomeBD.doubleValue();
            double expense = expenseBD.doubleValue();
            if (income > 0.0 || expense > 0.0) { // skip empty months
                months.add(new com.example.fintrack.dto.SavingsMonth(
                        start.getYear(), start.getMonthValue(), income, expense
                ));
            }
        }

        if (months.isEmpty()) {
            // No data → quick, friendly response
            return ResponseEntity.ok(java.util.Map.of(
                    "next_month_savings", 0,
                    "history", java.util.List.of(),
                    "note", "No transactions found in recent months"
            ));
        }

        var payload = new com.example.fintrack.dto.ForecastRequest(months);

        try {
            var resp = client.post()
                    .uri("/predict/savings")
                    .body(payload) // typed DTO → clean JSON
                    .retrieve()
                    .toEntity(java.util.Map.class);
            return ResponseEntity.ok(resp.getBody());
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            // Fallback: average of provided months
            var history = months.stream()
                    .map(m -> m.income() - m.expense())
                    .toList();
            double avg = history.stream().mapToDouble(d -> d).average().orElse(0.0);
            return ResponseEntity.ok(java.util.Map.of(
                    "next_month_savings", Math.round(avg * 100.0) / 100.0,
                    "history", history,
                    "note", "ML fallback due to upstream error: " + ex.getStatusCode().value()
            ));
        }
    }
    @GetMapping("/trend")
    public ResponseEntity<?> trend(
            @RequestParam(value = "months", defaultValue = "6") int months,
            org.springframework.security.core.Authentication auth) {
        var u = userRepo.findByEmail(auth.getName()).orElseThrow();
        months = Math.max(1, Math.min(24, months));

        var labels = new java.util.ArrayList<String>();
        var income = new java.util.ArrayList<Double>();
        var expense = new java.util.ArrayList<Double>();
        var savings = new java.util.ArrayList<Double>();

        var startAnchor = java.time.LocalDate.now().withDayOfMonth(1).minusMonths(months - 1);
        for (int i = 0; i < months; i++) {
            var start = startAnchor.plusMonths(i);
            var end = start.plusMonths(1).minusDays(1);
            var txs = txRepo.findByUserAndDateBetween(u, start, end);

            var inc = txs.stream().filter(t -> t.getCategory() == com.example.fintrack.model.Category.INCOME)
                    .map(com.example.fintrack.model.Transaction::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).doubleValue();
            var exp = txs.stream().filter(t -> t.getCategory() != com.example.fintrack.model.Category.INCOME)
                    .map(com.example.fintrack.model.Transaction::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).doubleValue();

            labels.add(start.getMonth().name().substring(0, 3) + " " + (start.getYear() % 100));
            income.add(inc);
            expense.add(exp);
            savings.add(inc - exp);
        }

        return ResponseEntity.ok(java.util.Map.of(
                "labels", labels,
                "income", income,
                "expense", expense,
                "savings", savings
        ));
    }


}
