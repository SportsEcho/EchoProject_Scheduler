package com.sportsecho.sportsechoscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SportsEchoSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SportsEchoSchedulerApplication.class, args);
    }

}
