package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableMethodSecurity
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DemoApplication.class);
        application.addInitializers(context -> {
            ZoneId zoneId = ZoneId.of(context.getEnvironment().getProperty("app.time-zone", "Asia/Ho_Chi_Minh"));
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        });
        application.run(args);
    }

}
