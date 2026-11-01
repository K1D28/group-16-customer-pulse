package com.customerpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class CustomerPulseApplication {

    /** "Today" and every stored date-time use Thailand time (UTC+7), as the SRS requires. */
    public static final String ZONE = "Asia/Bangkok";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }

    public static void main(String[] args) {
        SpringApplication.run(CustomerPulseApplication.class, args);
    }
}
