package com.bjtu.dining_simulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiningSimulationApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiningSimulationApplication.class, args);
	}

}
