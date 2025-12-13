package com.example.fintrack.config;

import com.example.fintrack.quartz.ReportJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail reportJobDetail() {
        return JobBuilder.newJob(ReportJob.class)
                .withIdentity("reportJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger reportJobTrigger() {
        // Cron: 0 0 9 * * ? -> every day at 09:00
        CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule("0 0 9 * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(reportJobDetail())
                .withIdentity("reportTrigger")
                .withSchedule(cron)
                .build();
    }
    @Bean
    public JobDetail billReminderJobDetail() {
        return JobBuilder.newJob(com.example.fintrack.jobs.BillReminderJob.class)
                .withIdentity("billReminderJob")
                .storeDurably()
                .build();
    }
    @Bean
    public Trigger billReminderTrigger() {
        // 9:00 AM every day
        CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule("0 0 9 * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(billReminderJobDetail())
                .withIdentity("billReminderTrigger")
                .withSchedule(cron)
                .build();
    }
}
