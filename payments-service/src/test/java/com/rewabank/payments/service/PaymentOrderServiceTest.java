package com.rewabank.payments.service;

import com.rewabank.payments.client.AccountsFeignClient;
import com.rewabank.payments.client.dto.AccountResponse;
import com.rewabank.payments.config.RazorpayConfig;
import com.rewabank.payments.dto.PaymentOrderRequest;
import com.rewabank.payments.dto.PaymentOrderResponse;
import com.rewabank.payments.entity.PaymentOrder;
import com.rewabank.payments.exception.PaymentException;
import com.rewabank.payments.repository.PaymentOrderRepository;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentOrderServiceTest {

    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock IdempotencyService     idempotencyService;
    @Mock OutboxPublisherService outboxPublisherService;
    @Mock RazorpayClient         razorpayClient;
    @Mock RazorpayConfig         razorpayConfig;
    @Mock AccountsFeignClient    accountsFeignClient;

    @InjectMocks PaymentOrderService paymentOrderService;

    private static final String USER_ID        = "kc-user-001";
    private static final String IDEM_KEY       = "idem-key-001";
    private static final UUID   SOURCE_ACCT_ID = UUID.randomUUID();
    private static final UUID   ORDER_ID       = UUID.randomUUID();

    @BeforeEach
    void setUp() throws RazorpayException {
        // Set up RazorpayClient.orders public field via mock injection
        OrderClient mockOrders = mock(OrderClient.class);
        razorpayClient.orders = mockOrders;

        Order mockOrder = mock(Order.class);
        when(mockOrder.get("id")).thenReturn("rzp_order_test_123");
        when(mockOrders.create(any())).thenReturn(mockOrder);

        when(razorpayConfig.getKeyId()).thenReturn("test_key_id");
    }

    private AccountResponse activeOwnedAccount() {
        return new AccountResponse(SOURCE_ACCT_ID, "111111111111", USER_ID, "ACTIVE");
    }

    private PaymentOrderRequest validRequest() {
        return new PaymentOrderRequest(
                SOURCE_ACCT_ID,
                new BigDecimal("500.00"),
                PaymentOrder.PaymentMethod.UPI
        );
    }

    private PaymentOrder savedOrder() {
        return PaymentOrder.builder()
                .id(ORDER_ID)
                .idempotencyKey(IDEM_KEY)
                .keycloakUserId(USER_ID)
                .sourceAccountId(SOURCE_ACCT_ID)
                .sourceAccountNumber("111111111111")
                .amount(new BigDecimal("500.00"))
                .paymentMethod(PaymentOrder.PaymentMethod.UPI)
                .razorpayOrderId("rzp_order_test_123")
                .status(PaymentOrder.PaymentStatus.CREATED)
                .currency("INR")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_ReturnsResponse_WhenValid() throws RazorpayException {
        when(idempotencyService.exists(IDEM_KEY)).thenReturn(false);
        when(accountsFeignClient.getById(SOURCE_ACCT_ID)).thenReturn(activeOwnedAccount());
        when(paymentOrderRepository.save(any())).thenReturn(savedOrder());

        PaymentOrderResponse response =
                paymentOrderService.createOrder(USER_ID, IDEM_KEY, validRequest());

        assertNotNull(response);
        verify(paymentOrderRepository).save(any(PaymentOrder.class));
        verify(outboxPublisherService).save(any(), any(), any(), any());
        verify(idempotencyService).store(eq(IDEM_KEY), any());
    }

    @Test
    void createOrder_ReturnsCachedResponse_WhenIdempotencyKeyAlreadyExists() throws RazorpayException {
        when(idempotencyService.exists(IDEM_KEY)).thenReturn(true);
        when(idempotencyService.get(IDEM_KEY)).thenReturn(ORDER_ID.toString());
        when(paymentOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(savedOrder()));

        PaymentOrderResponse response =
                paymentOrderService.createOrder(USER_ID, IDEM_KEY, validRequest());

        assertNotNull(response);
        // Must NOT call Razorpay or save a new order
        verify(razorpayClient.orders, never()).create(any());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void createOrder_ThrowsPymt008_WhenAccountNotOwnedByUser() {
        when(idempotencyService.exists(IDEM_KEY)).thenReturn(false);
        // Account belongs to a different user
        AccountResponse wrongUser = new AccountResponse(
                SOURCE_ACCT_ID, "111111111111", "other-user-999", "ACTIVE");
        when(accountsFeignClient.getById(SOURCE_ACCT_ID)).thenReturn(wrongUser);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentOrderService.createOrder(USER_ID, IDEM_KEY, validRequest()));

        assertEquals("PYMT_008", ex.getErrorCode());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void createOrder_ThrowsPymt009_WhenAccountNotActive() {
        when(idempotencyService.exists(IDEM_KEY)).thenReturn(false);
        AccountResponse frozen = new AccountResponse(
                SOURCE_ACCT_ID, "111111111111", USER_ID, "FROZEN");
        when(accountsFeignClient.getById(SOURCE_ACCT_ID)).thenReturn(frozen);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentOrderService.createOrder(USER_ID, IDEM_KEY, validRequest()));

        assertEquals("PYMT_009", ex.getErrorCode());
    }

    @Test
    void createOrder_ThrowsPymt007_WhenAccountLookupFails() {
        when(idempotencyService.exists(IDEM_KEY)).thenReturn(false);
        when(accountsFeignClient.getById(SOURCE_ACCT_ID))
                .thenThrow(new RuntimeException("Feign timeout"));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentOrderService.createOrder(USER_ID, IDEM_KEY, validRequest()));

        assertEquals("PYMT_007", ex.getErrorCode());
    }

    @Test
    void createOrder_ThrowsPymt002_WhenRazorpayFails() throws RazorpayException {
        when(idempotencyService.exists(IDEM_KEY)).thenReturn(false);
        when(accountsFeignClient.getById(SOURCE_ACCT_ID)).thenReturn(activeOwnedAccount());
        when(razorpayClient.orders.create(any())).thenThrow(new RazorpayException("Gateway error"));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentOrderService.createOrder(USER_ID, IDEM_KEY, validRequest()));

        assertEquals("PYMT_002", ex.getErrorCode());
        verify(paymentOrderRepository, never()).save(any());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_ReturnsResponse_WhenCallerIsOwner() {
        when(paymentOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(savedOrder()));

        PaymentOrderResponse response = paymentOrderService.getById(ORDER_ID, USER_ID);

        assertNotNull(response);
        assertEquals(ORDER_ID, response.id());
    }

    @Test
    void getById_ThrowsPymt003_WhenCallerIsNotOwner() {
        when(paymentOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(savedOrder()));

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentOrderService.getById(ORDER_ID, "different-user"));

        assertEquals("PYMT_003", ex.getErrorCode());
    }

    @Test
    void getById_ThrowsPymt003_WhenOrderNotFound() {
        when(paymentOrderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class,
                () -> paymentOrderService.getById(ORDER_ID, USER_ID));

        assertEquals("PYMT_003", ex.getErrorCode());
    }
}
