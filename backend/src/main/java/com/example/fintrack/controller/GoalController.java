package com.example.fintrack.controller;

import com.example.fintrack.model.Goal;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.GoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goals;
    private final UserRepository users;

    public GoalController(GoalService goals, UserRepository users) {
        this.goals = goals; this.users = users;
    }

    @GetMapping
    public List<GoalService.GoalView> list(Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        return goals.list(u);
    }

    @GetMapping("/plan")
    public GoalService.Plan plan(@RequestParam(required = false) BigDecimal monthly,
                                 Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        BigDecimal avail = (monthly != null) ? monthly : goals.estimateMonthlySavings(u);
        return goals.plan(u, avail);
    }

    record CreateReq(String name, BigDecimal targetAmount, LocalDate targetDate) {}

    @PostMapping
    public Map<String,Object> create(@RequestBody CreateReq req, Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        var g = goals.create(u, req.name(), req.targetAmount(), req.targetDate());
        return Map.of("id", g.getId());
    }

    record ContribReq(BigDecimal amount, LocalDate date, String note, Boolean affectsBudget) {}

    @PostMapping("/{id}/contrib")
    public ResponseEntity<?> contribute(@PathVariable Long id, @RequestBody ContribReq req, Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        boolean affects= Boolean.TRUE.equals(req.affectsBudget());
        goals.contribute(u, id, req.amount(), req.date(), req.note(), affects);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id, @RequestParam Goal.Status value, Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        goals.changeStatus(u, id, value);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        var u = users.findByEmail(auth.getName()).orElseThrow();
        goals.delete(u, id);
        return ResponseEntity.noContent().build();
    }

}
