package com.example.fintrack.controller;

import com.example.fintrack.model.User;
import com.example.fintrack.service.BillService;
import com.example.fintrack.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bills")
public class BillController {
    private final BillService service;
    private final UserRepository users;

    public BillController(BillService service, UserRepository users) {
        this.service = service; this.users = users;
    }

    @GetMapping
    public List<BillService.BillView> list(Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        return service.list(u);
    }

    record CreateReq(String name, String category, BigDecimal amount, Integer dueDay, Integer leadDays) {}

    @PostMapping
    public Map<String,Object> create(@RequestBody CreateReq req, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        var b = service.create(u, req.name(), req.category(), req.amount(), req.dueDay(), req.leadDays() == null ? 3 : req.leadDays());
        return Map.of("id", b.getId());
    }

    record UpdateReq(String name, String category, BigDecimal amount, Integer dueDay, Integer leadDays, Boolean active) {}

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateReq req, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        service.update(u, id, req.name(), req.category(), req.amount(), req.dueDay(), req.leadDays(), req.active());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        service.delete(u, id);
        return ResponseEntity.noContent().build();
    }

    /** Manual preview email (to your user), useful for testing */
    @PostMapping("/send-preview")
    public ResponseEntity<?> sendPreview(Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        service.sendPreview(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Manually run today's check (for all users) */
    @PostMapping("/run-today")
    public Map<String,Object> runToday() {
        int emails = service.runDailyCheck(LocalDate.now());
        return Map.of("emails", emails);
    }
}
