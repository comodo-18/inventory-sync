package com.anurag.inventory_sync.service;

import com.anurag.inventory_sync.entity.InventoryItem;
import com.anurag.inventory_sync.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockReservationService Unit Tests")
class StockReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private StockReservationService service;

    private static final Long PRODUCT_ID = 1L;
    private static final int QUANTITY = 2;

    @BeforeEach
    void setUp() {
        when(redissonClient.getLock("lock:inventory:" + PRODUCT_ID)).thenReturn(rLock);
    }

    // ---------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("should reserve stock and decrement by exact quantity when sufficient stock exists")
    void shouldReserveStock_whenSufficientStockExists() throws InterruptedException {
        // Given
        InventoryItem item = buildItem(PRODUCT_ID, 10);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(item));

        // When
        boolean result = service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then
        assertTrue(result);
        assertEquals(8, item.getStock());   // 10 - 2 = 8
        verify(inventoryRepository).save(item);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("should allow reservation when stock exactly equals requested quantity")
    void shouldReserveStock_whenStockEqualsRequestedQuantity() throws InterruptedException {
        // Given
        InventoryItem item = buildItem(PRODUCT_ID, QUANTITY);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(item));

        // When
        boolean result = service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then
        assertTrue(result);
        assertEquals(0, item.getStock());
        verify(inventoryRepository).save(item);
    }

    // ---------------------------------------------------------------
    // Insufficient stock
    // ---------------------------------------------------------------

    @Test
    @DisplayName("should return false and not save when stock is insufficient")
    void shouldReturnFalse_whenInsufficientStock() throws InterruptedException {
        // Given — only 1 unit available but 2 requested
        InventoryItem item = buildItem(PRODUCT_ID, 1);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(item));

        // When
        boolean result = service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then
        assertFalse(result);
        assertEquals(1, item.getStock());   // stock unchanged — oversell prevented
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return false and not save when stock is zero")
    void shouldReturnFalse_whenStockIsZero() throws InterruptedException {
        // Given
        InventoryItem item = buildItem(PRODUCT_ID, 0);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(item));

        // When
        boolean result = service.reserveStock(PRODUCT_ID, 1);

        // Then
        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Product not found
    // ---------------------------------------------------------------

    @Test
    @DisplayName("should return false when product has no inventory record")
    void shouldReturnFalse_whenProductNotFound() throws InterruptedException {
        // Given
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

        // When
        boolean result = service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then
        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Distributed lock scenarios
    // ---------------------------------------------------------------

    @Test
    @DisplayName("should return false and skip DB query when lock cannot be acquired")
    void shouldReturnFalse_whenLockCannotBeAcquired() throws InterruptedException {
        // Given — simulates lock held by another instance for >5s
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(false);

        // When
        boolean result = service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then
        assertFalse(result);
        // Must not touch DB at all — no read, no write
        verify(inventoryRepository, never()).findByProductId(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("should release lock in finally block after successful reservation")
    void shouldReleaseLock_afterSuccessfulReservation() throws InterruptedException {
        // Given
        InventoryItem item = buildItem(PRODUCT_ID, 10);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(item));

        // When
        service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then — lock is always released regardless of outcome
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("should release lock in finally block even when reservation fails")
    void shouldReleaseLock_evenWhenReservationFails() throws InterruptedException {
        // Given
        InventoryItem item = buildItem(PRODUCT_ID, 0); // insufficient stock
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(item));

        // When
        boolean result = service.reserveStock(PRODUCT_ID, QUANTITY);

        // Then
        assertFalse(result);
        verify(rLock).unlock(); // lock still released
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private InventoryItem buildItem(Long productId, int stock) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setStock(stock);
        item.setStatus("AVAILABLE");
        return item;
    }
}
