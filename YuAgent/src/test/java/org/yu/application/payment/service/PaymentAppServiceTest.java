package org.yu.application.payment.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.yu.domain.order.constant.OrderStatus;
import org.yu.domain.order.constant.OrderType;
import org.yu.domain.order.constant.PaymentPlatform;
import org.yu.domain.order.event.PurchaseSuccessEvent;
import org.yu.domain.order.model.OrderEntity;
import org.yu.domain.order.service.OrderDomainService;
import org.yu.infrastructure.payment.model.PaymentCallback;
import org.yu.infrastructure.payment.factory.PaymentProviderFactory;
import org.yu.infrastructure.payment.provider.PaymentProvider;
import org.yu.infrastructure.ratelimit.service.RateLimitService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAppServiceTest {

    @Test
    void shouldNotPublishPurchaseEventWhenPendingOrderWasAlreadyTransitioned() {
        OrderDomainService orderDomainService = mock(OrderDomainService.class);
        PaymentProviderFactory paymentProviderFactory = mock(PaymentProviderFactory.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        PaymentProvider paymentProvider = mock(PaymentProvider.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        PaymentAppService service = new PaymentAppService(orderDomainService, paymentProviderFactory, eventPublisher,
                rateLimitService, "http://localhost:8088/api");

        OrderEntity order = new OrderEntity();
        order.setId("order-1");
        order.setUserId("user-1");
        order.setOrderNo("order-no-1");
        order.setOrderType(OrderType.RECHARGE);
        order.setAmount(new BigDecimal("10.00"));
        order.setStatus(OrderStatus.PENDING);

        PaymentCallback callback = new PaymentCallback();
        callback.setOrderNo("order-no-1");
        callback.setPaymentSuccess(true);
        callback.setSignatureValid(true);
        callback.setProviderOrderId("provider-order-1");
        callback.setAmount(new BigDecimal("10.00"));

        when(paymentProviderFactory.getProvider(PaymentPlatform.ALIPAY)).thenReturn(paymentProvider);
        when(paymentProvider.handleCallback(request)).thenReturn(callback);
        when(paymentProvider.getCallbackResponse(true)).thenReturn("success");
        when(orderDomainService.getOrderByOrderNo("order-no-1")).thenReturn(order);
        when(orderDomainService.transitionOrderStatusAndProviderInfo("order-1", OrderStatus.PENDING, OrderStatus.PAID,
                "provider-order-1")).thenReturn(false);

        String result = service.handlePaymentCallback(PaymentPlatform.ALIPAY, request);

        assertEquals("success", result);
        verify(eventPublisher, never()).publishEvent(any(PurchaseSuccessEvent.class));
    }
}
