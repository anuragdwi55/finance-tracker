
package com.example.fintrack.job;

import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.ReportService;
import jakarta.mail.internet.MimeMessage;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

@Component
public class MonthlyReportJob implements Job {

    private final UserRepository userRepo;
    private final ReportService reportService;
    private final JavaMailSender mailSender;

    public MonthlyReportJob(UserRepository userRepo, ReportService reportService, JavaMailSender mailSender) {
        this.userRepo = userRepo;
        this.reportService = reportService;
        this.mailSender = mailSender;
    }

    @Override
    public void execute(JobExecutionContext context) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String subject = "Monthly Finance Report — " + Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;

        userRepo.findAll().forEach(user -> {
            try {
                String html = reportService.renderMonthlyHtml(user, year, month);
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
                helper.setTo(user.getEmail());
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(msg);
            } catch (Exception ex) {
                System.err.println("Failed to send report to " + user.getEmail() + ": " + ex.getMessage());
            }
        });
    }
}
