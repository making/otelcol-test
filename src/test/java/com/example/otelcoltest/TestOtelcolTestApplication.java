package com.example.otelcoltest;

import org.springframework.boot.SpringApplication;

public class TestOtelcolTestApplication {

	public static void main(String[] args) {
		SpringApplication.from(OtelcolTestApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
