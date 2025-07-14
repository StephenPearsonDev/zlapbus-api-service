package dev.stephenpearson.ztm_tracker_api_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ZtmTrackerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZtmTrackerApiApplication.class, args);
	}

}
