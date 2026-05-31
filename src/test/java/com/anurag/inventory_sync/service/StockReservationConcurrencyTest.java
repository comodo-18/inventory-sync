package com.anurag.inventory_sync.service;

import com.anurag.inventory_sync.entity.InventoryItem;
import com.anurag.inventory_sync.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockReservationConcurrencyTest {

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private final Long productId = 999L;

    @BeforeEach
    void setUp() {
        // Start with exactly 5 units in stock
        inventoryRepository.findByProductId(productId)
                .ifPresent(inventoryRepository::delete);

        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setStock(5);            // only 5 available
        item.setStatus("ACTIVE");
        item.setLastUpdated(Instant.now());
        inventoryRepository.save(item);
    }

    @Test
    void shouldPreventOversellUnderConcurrentLoad() throws InterruptedException {

        int threadCount = 10;          // 10 customers
        int quantityEach = 1;          // each wants 1 unit

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 10 threads all try to reserve at the same time
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean success = stockReservationService
                            .reserveStock(productId, quantityEach);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS); // wait for all threads
        executor.shutdown();

        // ASSERTIONS — the proof of oversell prevention
        assertEquals(5, successCount.get(),
                "Exactly 5 reservations should succeed");
        assertEquals(5, failureCount.get(),
                "Exactly 5 reservations should fail");

        // Final stock must be 0 — never negative
        InventoryItem finalItem = inventoryRepository
                .findByProductId(productId).orElseThrow();
        assertEquals(0, finalItem.getStock(),
                "Stock should be exactly 0, never negative");
    }
}