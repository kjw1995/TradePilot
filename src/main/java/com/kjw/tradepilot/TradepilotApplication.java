package com.kjw.tradepilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradepilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradepilotApplication.class, args);
    }

}
