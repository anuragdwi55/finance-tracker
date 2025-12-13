package com.example.fintrack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfig implements WebMvcConfigurer {

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${ALLOWED_ORIGINS:*}") String allowed) {
        var origins = allowed.split(",");
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry reg) {
                reg.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                        .allowCredentials(true);
            }
        };
    }



}
