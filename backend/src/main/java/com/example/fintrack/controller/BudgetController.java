package com.example.fintrack.controller;

import com.example.fintrack.dto.BudgetDtos;
import com.example.fintrack.model.Budget;
import com.example.fintrack.model.Category;
import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.BudgetRepository;
import com.example.fintrack.repository.TransactionRepository;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetRepository budgetRepo;
    private final UserRepository userRepo;
    private final TransactionRepository txRepo;
    @Autowired
    private EventPublisher eventPublisher;

    public BudgetController(BudgetRepository budgetRepo, UserRepository userRepo, TransactionRepository txRepo) {
        this.budgetRepo = budgetRepo;
        this.userRepo = userRepo;
        this.txRepo = txRepo;
    }

    @GetMapping
    public ResponseEntity<?> listForMonth(@RequestParam int year, @RequestParam int month,
                                          org.springframework.security.core.Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        var budgets = budgetRepo.findByUserAndYearAndMonth(u, year, month).stream()
                .map(b -> new BudgetDtos.BudgetView(b.getId(), b.getCategory().name(), b.getYear(), b.getMonth(), b.getLimitAmount()))
                .toList();
        return ResponseEntity.ok(budgets);
    }

    @PutMapping
    public ResponseEntity<?> upsertForMonth(@RequestParam int year, @RequestParam int month,
                                            @RequestBody BudgetDtos.BudgetUpsertList payload,
                                            org.springframework.security.core.Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();

        List<Budget> result = new ArrayList<>();
        for (var item : payload.items()) {
            Category cat = Category.valueOf(item.category());
            var existing = budgetRepo.findByUserAndYearAndMonthAndCategory(u, year, month, cat);
            Budget b = existing.orElseGet(() -> new Budget(u, cat, year, month, BigDecimal.ZERO));
            b.setLimitAmount(item.limit() == null ? BigDecimal.ZERO : item.limit());
            result.add(budgetRepo.save(b));
            eventPublisher.publish("budget.upserted", u, Map.of(
                    "year", year, "month", month,
                    "count", result.size()
            ));
        }
        var out = result.stream()
                .map(b -> new BudgetDtos.BudgetView(b.getId(), b.getCategory().name(), b.getYear(), b.getMonth(), b.getLimitAmount()))
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(@RequestParam int year, @RequestParam int month,
                                      org.springframework.security.core.Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<Transaction> txs = txRepo.findByUserAndDateBetween(u, start, end);
        BigDecimal income = txs.stream().filter(t -> t.getCategory() == Category.INCOME)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = txs.stream().filter(t -> t.getCategory() != Category.INCOME)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            if (c == Category.INCOME) continue;
            BigDecimal sum = txs.stream().filter(t -> t.getCategory() == c)
                    .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            byCategory.put(c.name(), sum);
        }

        var budgets = budgetRepo.findByUserAndYearAndMonth(u, year, month);
        Map<String, BigDecimal> limits = budgets.stream().collect(Collectors.toMap(
                b -> b.getCategory().name(), Budget::getLimitAmount, (a, b) -> b, LinkedHashMap::new));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (var e : byCategory.entrySet()) {
            BigDecimal spent = e.getValue();
            BigDecimal limit = limits.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal remaining = limit.subtract(spent);
            double pct = limit.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                    spent.divide(limit, 4, java.math.RoundingMode.HALF_UP).doubleValue();
            rows.add(Map.of("category", e.getKey(), "limit", limit, "spent", spent, "remaining", remaining, "pct", pct));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", Map.of(
                "income", income,
                "expense", expense,
                "savings", income.subtract(expense),
                "budgeted", budgets.stream().map(Budget::getLimitAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
        ));
        out.put("byCategory", rows);
        return ResponseEntity.ok(out);
    }
    @PostMapping("/copy")
    public ResponseEntity<?> copyBudgets(
            @RequestParam("fromYear") int fromYear,
            @RequestParam("fromMonth") int fromMonth,
            @RequestParam("toYear") int toYear,
            @RequestParam("toMonth") int toMonth,
            org.springframework.security.core.Authentication auth) {

        var u = userRepo.findByEmail(auth.getName()).orElseThrow();

        var source = budgetRepo.findByUserAndYearAndMonth(u, fromYear, fromMonth);
        if (source.isEmpty()) {
            return ResponseEntity.ok(List.of()); // nothing to copy
        }

        var out = new java.util.ArrayList<BudgetDtos.BudgetView>();
        for (var s : source) {
            var existing = budgetRepo.findByUserAndYearAndMonthAndCategory(u, toYear, toMonth, s.getCategory());
            var b = existing.orElseGet(() -> new com.example.fintrack.model.Budget(u, s.getCategory(), toYear, toMonth, java.math.BigDecimal.ZERO));
            b.setLimitAmount(s.getLimitAmount());
            var saved = budgetRepo.save(b);
            out.add(new BudgetDtos.BudgetView(saved.getId(), saved.getCategory().name(), saved.getYear(), saved.getMonth(), saved.getLimitAmount()));
        }
        return ResponseEntity.ok(out);
    }
}
