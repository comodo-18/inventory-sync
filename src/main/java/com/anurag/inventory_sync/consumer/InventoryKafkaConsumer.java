package com.anurag.inventory_sync.consumer;

import com.anurag.inventory_sync.entity.InventoryItem;
import com.anurag.inventory_sync.event.CacheInvalidationEvent;
import com.anurag.inventory_sync.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryKafkaConsumer {

    private final InventoryRepository inventoryRepository;

    @KafkaListener(
            topics = "product-cache-invalidation",
            groupId = "inventory-group"
    )
    public void consume(CacheInvalidationEvent event) {
        log.info("Received cache invalidation event: productId={} eventType={}",
                event.productId(), event.eventType());

        if (event.eventType().equals("UPDATED")) {
            handleProductUpdated(event.productId());
        } else if (event.eventType().equals("DELETED")) {
            handleProductDeleted(event.productId());
        }
    }

    private void handleProductUpdated(Long productId) {
        InventoryItem item = inventoryRepository
                .findByProductId(productId)
                .orElse(createNewItem(productId));

        item.setStatus("NEEDS_RECHECK");
        item.setLastUpdated(Instant.now());
        inventoryRepository.save(item);

        log.info("Marked product {} as NEEDS_RECHECK", productId);
    }

    private void handleProductDeleted(Long productId) {
        inventoryRepository.findByProductId(productId).ifPresent(item -> {
            item.setStatus("DISCONTINUED");
            item.setLastUpdated(Instant.now());
            inventoryRepository.save(item);
            log.info("Marked product {} as DISCONTINUED", productId);
        });
    }

    private InventoryItem createNewItem(Long productId) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setStock(0);
        item.setStatus("ACTIVE");
        item.setLastUpdated(Instant.now());
        return item;
    }
}
