package com.vasu.bastionServer;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeContorller {
	@RequestMapping("/")
	public String name() {
		return "index.html";
	}
}