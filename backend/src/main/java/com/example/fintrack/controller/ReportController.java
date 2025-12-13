
package com.example.fintrack.controller;

import com.example.fintrack.model.User;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.EventPublisher;
import com.example.fintrack.service.ReportService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;
    private final UserRepository userRepo;
    private final JavaMailSender mailSender;
    @Autowired
    private EventPublisher eventPublisher;

    public ReportController(ReportService reportService, UserRepository userRepo, JavaMailSender mailSender) {
        this.reportService = reportService;
        this.userRepo = userRepo;
        this.mailSender = mailSender;
    }

    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@RequestParam int year,
                                          @RequestParam int month,
                                          org.springframework.security.core.Authentication auth) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        String html = reportService.renderMonthlyHtml(u, year, month);
        return ResponseEntity.ok(html);
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestParam int year,
                                  @RequestParam int month,
                                  org.springframework.security.core.Authentication auth) throws Exception {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();
        String html = reportService.renderMonthlyHtml(u, year, month);

        String subject = "Monthly Finance Report " + Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + year;
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
        helper.setTo(u.getEmail());
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        eventPublisher.publish("report.sent", u, Map.of("year", year, "month", month));

        return ResponseEntity.ok().build();
    }
}
