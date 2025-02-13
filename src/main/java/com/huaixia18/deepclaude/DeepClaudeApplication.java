package com.huaixia18.deepclaude;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class DeepClaudeApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeepClaudeApplication.class, args);
	}

}
