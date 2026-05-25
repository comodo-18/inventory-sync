package com.anurag.inventory_sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class InventorySyncApplication {
	public static void main(String[] args) {
		SpringApplication.run(InventorySyncApplication.class, args);
	}
}
