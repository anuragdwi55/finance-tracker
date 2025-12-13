package com.example.fintrack.controller;

import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.AlertRepository;
import com.example.fintrack.repository.TransactionRepository;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.AlertService;
import com.example.fintrack.service.EventPublisher;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionRepository txRepo;
    private final UserRepository userRepo;
    private final EventPublisher eventPublisher;


    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertRepository alertRepo;

    public TransactionController(TransactionRepository txRepo,
                                 UserRepository userRepo,
                                 EventPublisher eventPublisher) {
        this.txRepo = txRepo;
        this.userRepo = userRepo;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public List<Transaction> list(Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        return txRepo.findByUser(u);
    }

    @PostMapping
    public Transaction create(@Valid @RequestBody Transaction t, Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        t.setUser(u);
        Transaction saved = txRepo.save(t);

        eventPublisher.publish("transaction.created", u, Map.of(
                "id", saved.getId(),
                "category", saved.getCategory().name(),
                "amount", saved.getAmount(),
                "date", saved.getDate().toString()
        ));
        alertService.maybeCreateAnomaly(saved);

        return saved;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam("start") String start,
                                       @RequestParam("end") String end,
                                       Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        var list = txRepo.findByUserAndDateBetween(u, LocalDate.parse(start), LocalDate.parse(end));
        var total = list.stream().map(tr -> tr.getAmount())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return Map.of("count", list.size(), "total", total);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        var tx = txRepo.findById(id).orElse(null);
        if (tx == null || !tx.getUser().getId().equals(u.getId())) return ResponseEntity.notFound().build();

        txRepo.deleteById(id);
        System.out.println("deleting transaction id " + id);
        alertRepo.deleteByTxIdAndUser(id, u);
        System.out.println("Deleted alerts for tx id " + id);

        // NEW: clean up any related alerts for this user/tx
       // try { alertRepo.deleteByTxIdAndUser(id, u); } catch (Exception ignored) {System.println(ignored);}




        eventPublisher.publish("transaction.deleted", u, Map.of("id", id));
        return ResponseEntity.noContent().build();
    }
}
