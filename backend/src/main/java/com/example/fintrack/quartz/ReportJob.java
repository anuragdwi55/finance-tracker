package com.example.fintrack.quartz;

import com.example.fintrack.model.User;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.service.EmailService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class ReportJob implements Job {

    private final UserRepository userRepo;
    private final EmailService email;

    public ReportJob(UserRepository userRepo, EmailService email) {
        this.userRepo = userRepo;
        this.email = email;
    }

    @Override
    public void execute(JobExecutionContext context) {
        // For demo, email the first user a simple summary (extend as needed)
        userRepo.findAll().stream().findFirst().ifPresent(u -> {
            email.send(u.getEmail(), "Your Daily Finance Report", "Hello! Your daily summary is ready. (Customize me)");
        });
    }
}
