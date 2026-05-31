package com.anurag.inventory_sync.service;

import com.anurag.inventory_sync.entity.InventoryItem;
import com.anurag.inventory_sync.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationService {

    private final InventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;

    public boolean reserveStock(Long productId, int quantity) {

        // One lock per product — different products don't block each other
        String lockKey = "lock:inventory:" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Wait up to 5s to acquire, hold for max 10s
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("Could not acquire lock for product {}", productId);
                return false;
            }

            // CRITICAL SECTION — only one thread here at a time
            InventoryItem item = inventoryRepository
                    .findByProductId(productId)
                    .orElse(null);

            if (item == null) {
                log.warn("No inventory for product {}", productId);
                return false;
            }

            if (item.getStock() < quantity) {
                log.warn("Insufficient stock for product {}: have {}, need {}",
                        productId, item.getStock(), quantity);
                return false; // oversell prevented
            }

            // Safe to decrement
            item.setStock(item.getStock() - quantity);
            item.setLastUpdated(Instant.now());
            inventoryRepository.save(item);

            log.info("Reserved {} units of product {}. Remaining: {}",
                    quantity, productId, item.getStock());
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted for product {}", productId);
            return false;
        } finally {
            // Always release — only if this thread holds it
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}