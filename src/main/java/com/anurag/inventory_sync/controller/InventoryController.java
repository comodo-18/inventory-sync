package com.anurag.inventory_sync.controller;

import com.anurag.inventory_sync.entity.InventoryItem;
import com.anurag.inventory_sync.repository.InventoryRepository;
import com.anurag.inventory_sync.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final StockReservationService stockReservationService;
    private final InventoryRepository inventoryRepository;

    @PostMapping("/reserve")
    public ResponseEntity<String> reserveStock(
            @RequestParam Long productId,
            @RequestParam int quantity) {

        boolean success = stockReservationService
                .reserveStock(productId, quantity);

        if (success) {
            return ResponseEntity.ok(
                    "Reserved " + quantity + " units of product " + productId);
        } else {
            return ResponseEntity.badRequest().body(
                    "Could not reserve — insufficient stock or lock timeout");
        }
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seed(
            @RequestParam Long productId,
            @RequestParam int stock) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setStock(stock);
        item.setStatus("AVAILABLE");
        item.setLastUpdated(java.time.Instant.now());
        inventoryRepository.save(item);
        return ResponseEntity.ok("Seeded product " + productId
                + " with stock " + stock);
    }
}