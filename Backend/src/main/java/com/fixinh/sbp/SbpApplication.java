package com.fixinh.sbp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SbpApplication {

	public static void main(String[] args) {
		SpringApplication.run(SbpApplication.class, args);
	}

}
