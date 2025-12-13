package com.example.fintrack.service;

import com.example.fintrack.model.Bill;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.BillRepository;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillService {

    private final BillRepository bills;
    private final JavaMailSender mailSender;

    public BillService(BillRepository bills, JavaMailSender mailSender) {
        this.bills = bills; this.mailSender = mailSender;
    }

    public record BillView(Long id, String name, String category, BigDecimal amount,
                           int dueDay, int leadDays, boolean active, String nextDueDate) {}

    public List<BillView> list(User u) {
        var ym = YearMonth.now();
        return bills.findByUserOrderByCreatedAtDesc(u).stream().map(b ->
                new BillView(
                        b.getId(), b.getName(), b.getCategory().name(), b.getAmount(),
                        b.getDueDay(), b.getLeadDays(), b.isActive(),
                        b.dueDateForMonth(ym).toString()
                )
        ).toList();
    }

    @Transactional
    public Bill create(User u, String name, String category, BigDecimal amount, int dueDay, int leadDays) {
        var b = new Bill();
        b.setUser(u);
        b.setName(name);
        b.setCategory(com.example.fintrack.model.Category.valueOf(category));
        b.setAmount(amount);
        b.setDueDay(dueDay);
        b.setLeadDays(Math.max(0, leadDays));
        return bills.save(b);
    }

    @Transactional
    public Bill update(User u, Long id, String name, String category, BigDecimal amount, Integer dueDay, Integer leadDays, Boolean active) {
        var b = bills.findByIdAndUser(id, u).orElseThrow();
        if (name != null) b.setName(name);
        if (category != null) b.setCategory(com.example.fintrack.model.Category.valueOf(category));
        if (amount != null) b.setAmount(amount);
        if (dueDay != null) b.setDueDay(dueDay);
        if (leadDays != null) b.setLeadDays(leadDays);
        if (active != null) b.setActive(active);
        return bills.save(b);
    }

    @Transactional public void delete(User u, Long id) {
        var b = bills.findByIdAndUser(id, u).orElseThrow();
        bills.delete(b);
    }

    /** Send an email listing bills due in exactly 'leadDays' from 'today' (per bill). */
    @Transactional
    public int runDailyCheck(LocalDate today) {
        var ym = YearMonth.from(today);
        var all = bills.findByActiveTrue();
        var byUser = all.stream().collect(Collectors.groupingBy(Bill::getUser));

        int emails = 0;
        for (var entry : byUser.entrySet()) {
            var user = entry.getKey();
            var dueSoon = new ArrayList<Bill>();
            for (var b : entry.getValue()) {
                var due = b.dueDateForMonth(ym);
                var delta = java.time.temporal.ChronoUnit.DAYS.between(today, due);
                boolean hitWindow = delta == b.getLeadDays();
                boolean notAlready = (b.getLastNotifiedYm() == null || !b.getLastNotifiedYm().equals(ym.toString()));
                if (hitWindow && notAlready) {
                    dueSoon.add(b);
                }
            }
            if (!dueSoon.isEmpty()) {
                sendEmail(user.getEmail(), dueSoon, ym);
                for (var b : dueSoon) { b.setLastNotifiedYm(ym.toString()); }
                emails++;
            }
        }
        return emails;
    }

    /** Send a manual preview email (list all active bills + next due date). */
    public void sendPreview(User u) {
        var ym = YearMonth.now();
        var list = bills.findByUserAndActiveTrue(u);
        sendEmail(u.getEmail(), list, ym);
    }

    private void sendEmail(String to, List<Bill> items, YearMonth ym) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true);
            h.setTo(to);
            h.setSubject("Upcoming bills (" + ym + ")");
            StringBuilder html = new StringBuilder();
            html.append("<h3>Upcoming bills</h3><table border='1' cellpadding='6' cellspacing='0'>")
                    .append("<tr><th>Name</th><th>Category</th><th>Amount</th><th>Due date</th></tr>");
            for (var b : items) {
                html.append("<tr><td>").append(escape(b.getName()))
                        .append("</td><td>").append(b.getCategory().name())
                        .append("</td><td>₹ ").append(b.getAmount())
                        .append("</td><td>").append(b.dueDateForMonth(ym))
                        .append("</td></tr>");
            }
            html.append("</table><p>This is an automated reminder.</p>");
            h.setText(html.toString(), true);
            mailSender.send(msg);
        } catch (Exception e) {
            // swallow; reminders are best-effort
        }
    }

    private static String escape(String s){ return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
}
