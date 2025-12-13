package com.example.fintrack.jobs;

import com.example.fintrack.service.BillService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BillReminderJob implements Job {
    private final BillService service;
    public BillReminderJob(BillService service){ this.service = service; }

    @Override
    public void execute(JobExecutionContext context) {
        service.runDailyCheck(LocalDate.now());
    }
}
