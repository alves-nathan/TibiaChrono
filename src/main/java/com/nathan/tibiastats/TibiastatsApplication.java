package com.nathan.tibiastats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TibiastatsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TibiastatsApplication.class, args);
    }
}