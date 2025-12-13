package com.example.fintrack.controller;

import com.example.fintrack.model.Alert;
import com.example.fintrack.repository.AlertRepository;
import com.example.fintrack.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
public class AlertController {
    private final AlertRepository repo;
    private final UserRepository users;
    public AlertController(AlertRepository repo, UserRepository users) { this.repo = repo; this.users = users; }

    record AlertView(Long id, String type, String severity, String title, String message,
                     Long txId, String createdAt, boolean read) {
        static AlertView of(Alert a) {
            return new AlertView(a.getId(), a.getType(), a.getSeverity().name(), a.getTitle(),
                    a.getMessage(), a.getTxId(), a.getCreatedAt().toString(), a.isReadFlag());
        }
    }

    @GetMapping
    public List<AlertView> list(@RequestParam(defaultValue = "false") boolean unreadOnly, Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        var all = repo.findTop50ByUserOrderByCreatedAtDesc(u);
        var filtered = unreadOnly ? all.stream().filter(a -> !a.isReadFlag()).toList() : all;
        return filtered.stream().sorted(Comparator.comparing(Alert::getCreatedAt).reversed())
                .map(AlertView::of).toList();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        var a = repo.findByIdAndUser(id, u).orElse(null);
        if (a == null) return ResponseEntity.notFound().build();
        a.setReadFlag(true);
        repo.save(a);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            // happens briefly before login / token attach — just report 0, not 500
            return Map.of("count", 0L);
        }
        var u = users.findByEmail(auth.getName()).orElseThrow();
        long c = repo.countUnread(u);
        return Map.of("count", c);
    }

}
