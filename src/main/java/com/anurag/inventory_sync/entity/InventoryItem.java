package com.anurag.inventory_sync.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.Instant;

@Entity
@Data
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private Integer stock;
    private String status; // ACTIVE, NEEDS_RECHECK, DISCONTINUED
    private Instant lastUpdated;
}