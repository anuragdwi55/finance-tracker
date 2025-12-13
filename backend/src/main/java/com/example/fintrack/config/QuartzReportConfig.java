
package com.example.fintrack.config;

import com.example.fintrack.job.MonthlyReportJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class QuartzReportConfig {

    @Value("${app.reports.cron:0 0 9 * * ?}")
    private String cron;

    @Bean
    public JobDetail monthlyReportJobDetail() {
        return JobBuilder.newJob(MonthlyReportJob.class)
                .withIdentity("monthlyReportJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger monthlyReportTrigger(JobDetail monthlyReportJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(monthlyReportJobDetail)
                .withIdentity("monthlyReportTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(TimeZone.getTimeZone("Asia/Kolkata")))
                .build();
    }
}
