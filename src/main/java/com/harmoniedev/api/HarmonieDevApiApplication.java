package com.harmoniedev.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling lives here (not inside the sync module) because ConnectivityMonitor needs
// the scheduling infrastructure even when sync.client is disabled — which is the default, before
// any cloud deployment exists.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HarmonieDevApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HarmonieDevApiApplication.class, args);
	}

}
