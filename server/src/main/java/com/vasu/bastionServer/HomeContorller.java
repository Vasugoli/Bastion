package com.vasu.bastionServer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class HomeContorller {
	
	@Value("${spring.mode}")
	private String DevolopmentMode;
	
	@GetMapping("/health")
	public String health() {
		return DevolopmentMode;
	}
}