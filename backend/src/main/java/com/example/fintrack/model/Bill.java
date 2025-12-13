package com.example.fintrack.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bills", indexes = {
        @Index(name="idx_bills_user_active", columnList = "user_id,active")
})
public class Bill {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false) private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category = Category.UTILITIES; // reuse your existing enum

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Day of month 1..28 (simple monthly recurrence) */
    @Column(name = "due_day", nullable = false)
    private int dueDay; // 1..28

    /** Send reminder X days before due date (default 3) */
    @Column(name = "lead_days", nullable = false)
    private int leadDays = 3;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Prevent duplicate notifications within a month (format: yyyy-MM) */
    @Column(name = "last_notified_ym", length = 7)
    private String lastNotifiedYm;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters/Setters
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public int getDueDay() { return dueDay; }
    public void setDueDay(int dueDay) { this.dueDay = dueDay; }
    public int getLeadDays() { return leadDays; }
    public void setLeadDays(int leadDays) { this.leadDays = leadDays; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getLastNotifiedYm() { return lastNotifiedYm; }
    public void setLastNotifiedYm(String lastNotifiedYm) { this.lastNotifiedYm = lastNotifiedYm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Compute this month’s due date (clamps to 28). */
    @Transient
    public LocalDate dueDateForMonth(java.time.YearMonth ym) {
        int day = Math.max(1, Math.min(28, dueDay));
        return ym.atDay(day);
    }
}
