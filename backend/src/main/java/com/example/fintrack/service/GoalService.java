package com.example.fintrack.service;

import com.example.fintrack.model.*;
import com.example.fintrack.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.fintrack.service.EventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class GoalService {

    private final GoalRepository goals;
    private final GoalContributionRepository contribs;
    private final TransactionRepository txRepo;

    @Autowired
    private EventPublisher events;
    public GoalService(GoalRepository goals, GoalContributionRepository contribs, TransactionRepository txRepo) {
        this.goals = goals; this.contribs = contribs; this.txRepo = txRepo;
    }

    public record GoalView(Long id, String name, BigDecimal targetAmount, LocalDate targetDate,
                           String status, BigDecimal contributed, BigDecimal remaining,
                           int progressPct, BigDecimal monthlyNeeded, Integer monthsLeft) {}

    public List<GoalView> list(User u) {
        var list = goals.findByUserOrderByCreatedAtDesc(u);
        List<GoalView> out = new ArrayList<>();
        for (var g : list) {
            var cons = contribs.findByGoal(g);
            BigDecimal contributed = cons.stream()
                    .map(GoalContribution::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal remaining = g.getTargetAmount().subtract(contributed).max(BigDecimal.ZERO);
            int monthsLeft = Math.max(1, monthsBetweenInclusive(YearMonth.now(), YearMonth.from(g.getTargetDate())));
            BigDecimal monthlyNeeded = remaining.divide(new BigDecimal(monthsLeft), 2, RoundingMode.HALF_UP);

            int progress = g.getTargetAmount().compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    contributed.multiply(new BigDecimal(100))
                            .divide(g.getTargetAmount(), 0, RoundingMode.DOWN).intValue();

            out.add(new GoalView(
                    g.getId(), g.getName(), g.getTargetAmount(), g.getTargetDate(),
                    g.getStatus().name(), contributed, remaining, progress, monthlyNeeded, monthsLeft
            ));
        }
        return out;
    }

    private int monthsBetweenInclusive(YearMonth from, YearMonth to) {
        int diff = (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue());
        return diff < 0 ? 0 : diff + 1;
    }

    /** Estimate net savings using last full month (income - expenses). */
    public BigDecimal estimateMonthlySavings(User u) {
        YearMonth prev = YearMonth.now().minusMonths(1);
        LocalDate s = prev.atDay(1);
        LocalDate e = prev.atEndOfMonth();
        var txs = txRepo.findByUserAndDateBetween(u, s, e);
        BigDecimal income = txs.stream().filter(t -> t.getCategory() == Category.INCOME)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = txs.stream().filter(t -> t.getCategory() != Category.INCOME)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = income.subtract(expense);
        if (net.compareTo(BigDecimal.ZERO) <= 0) return new BigDecimal("20000"); // friendly default
        return net;
    }

    /** Proportional allocation: scale monthlyNeeded to fit within available. */
    public record Plan(BigDecimal available, BigDecimal totalNeed, List<Map<String, Object>> items) {}
    public Plan plan(User u, BigDecimal available) {
        var views = list(u);
        BigDecimal totalNeed = views.stream()
                .filter(v -> "ACTIVE".equals(v.status()))
                .map(GoalView::monthlyNeeded).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String,Object>> items = new ArrayList<>();
        if (totalNeed.compareTo(BigDecimal.ZERO) == 0) {
            for (var v : views) {
                items.add(Map.of("goalId", v.id(), "name", v.name(), "allocated", BigDecimal.ZERO));
            }
            return new Plan(available, totalNeed, items);
        }

        BigDecimal scale = totalNeed.compareTo(available) <= 0
                ? BigDecimal.ONE
                : available.divide(totalNeed, 6, RoundingMode.HALF_UP);

        for (var v : views) {
            BigDecimal alloc = "ACTIVE".equals(v.status())
                    ? v.monthlyNeeded().multiply(scale).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(Map.of("goalId", v.id(), "name", v.name(), "allocated", alloc));
        }

        return new Plan(available, totalNeed, items);
    }

    @Transactional
    public Goal create(User u, String name, BigDecimal targetAmount, LocalDate targetDate) {
        var g = new Goal();
        g.setUser(u); g.setName(name);
        g.setTargetAmount(targetAmount); g.setTargetDate(targetDate);
        return goals.save(g);
    }

    @Transactional
    public GoalContribution contribute(User u, Long goalId, BigDecimal amount, LocalDate date, String note) {
        var g = goals.findByIdAndUser(goalId, u).orElseThrow();
        var c = new GoalContribution();
        c.setGoal(g); c.setAmount(amount); c.setDate(date != null ? date : LocalDate.now()); c.setNote(note);
        return contribs.save(c);
    }

    @Transactional
    public void contribute(User u, Long goalId, BigDecimal amount, LocalDate date, String note, boolean affectsBudget) {
        Goal g = goals.findByIdAndUser(goalId, u).orElseThrow();

        // Update goal progress (however you store contributions)
        g.setContributed(g.getContributed().add(amount));
        goals.save(g);

        if (affectsBudget) {
            Transaction t = new Transaction();
            t.setUser(u);
            t.setCategory(Category.INVESTMENT);
            t.setAmount(amount);
            t.setDate(date != null ? date : LocalDate.now());
            t.setNote(((note == null) ? "" : (note + " ")) + "(Goal: " + g.getName() + ")");
            Transaction saved = txRepo.save(t);

            // optional: emit your existing events
            try {
                events.publish("transaction.created", u, Map.of(
                        "id", saved.getId(),
                        "category", saved.getCategory().name(),
                        "amount", saved.getAmount(),
                        "date", saved.getDate().toString()
                ));
            } catch (Exception ignored) {}
        }
    }

        @Transactional
    public void changeStatus(User u, Long id, Goal.Status status) {
        var g = goals.findByIdAndUser(id, u).orElseThrow();
        g.setStatus(status); goals.save(g);
    }

    @Transactional
    public void delete(User u, Long id) {
        var g = goals.findByIdAndUser(id, u).orElseThrow();
        contribs.deleteByGoal(g);
        goals.delete(g);
    }
}
