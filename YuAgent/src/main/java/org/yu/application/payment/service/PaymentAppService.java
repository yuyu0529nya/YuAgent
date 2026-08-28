package org.yu.application.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.yu.application.payment.assembler.PaymentAssembler;
import org.yu.domain.order.constant.OrderStatus;
import org.yu.domain.order.constant.OrderType;
import org.yu.domain.order.constant.PaymentPlatform;
import org.yu.domain.order.constant.PaymentType;
import org.yu.domain.order.event.PurchaseSuccessEvent;
import org.yu.domain.order.model.OrderEntity;
import org.yu.domain.order.service.OrderDomainService;
import org.yu.infrastructure.auth.UserContext;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.payment.constant.PaymentMethod;
import org.yu.infrastructure.ratelimit.service.RateLimitService;
import org.yu.infrastructure.payment.factory.PaymentProviderFactory;
import org.yu.infrastructure.payment.model.PaymentCallback;
import org.yu.infrastructure.payment.model.PaymentRequest;
import org.yu.infrastructure.payment.model.PaymentResult;
import org.yu.infrastructure.payment.provider.PaymentProvider;
import org.yu.interfaces.dto.account.request.RechargeRequest;
import org.yu.interfaces.dto.account.response.PaymentResponseDTO;
import org.yu.interfaces.dto.account.response.OrderStatusResponseDTO;
import org.yu.interfaces.dto.account.response.PaymentMethodDTO;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/** 鏀粯搴旂敤鏈嶅姟 */
@Service
public class PaymentAppService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAppService.class);

    private final OrderDomainService orderDomainService;
    private final PaymentProviderFactory paymentProviderFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimitService rateLimitService;
    private final String paymentBaseUrl;

    public PaymentAppService(OrderDomainService orderDomainService, PaymentProviderFactory paymentProviderFactory,
            ApplicationEventPublisher eventPublisher, RateLimitService rateLimitService,
            @Value("${payment.base-url:http://localhost:8088/api}") String paymentBaseUrl) {
        this.orderDomainService = orderDomainService;
        this.paymentProviderFactory = paymentProviderFactory;
        this.eventPublisher = eventPublisher;
        this.rateLimitService = rateLimitService;
        this.paymentBaseUrl = paymentBaseUrl;
    }

    /** 鍒涘缓鍏呭€艰鍗曞苟鍙戣捣鏀粯
     *
     * @param request 鍏呭€艰姹?
     * @return 鏀粯鍝嶅簲 */
    @Transactional
    public PaymentResponseDTO createRechargePayment(RechargeRequest request) {
        String userId = UserContext.getCurrentUserId();

        // 闄愭祦妫€鏌?
        rateLimitService.checkRechargeRateLimit(userId);

        // 杞崲鏀粯骞冲彴鍜岀被鍨?
        PaymentPlatform paymentPlatform = PaymentPlatform.fromCode(request.getPaymentPlatform());
        PaymentType paymentType = PaymentType.fromCode(request.getPaymentType());

        if (!paymentProviderFactory.isAvailable(paymentPlatform)) {
            throw new BusinessException("鏀粯骞冲彴鏆備笉鍙敤: " + paymentPlatform.getName());
        }

        // 鍒涘缓鍏呭€艰鍗?
        OrderEntity order = createRechargeOrder(userId, request, paymentPlatform, paymentType);

        try {
            // 鍙戣捣鏀粯
            PaymentResult paymentResult = createPaymentWithProvider(order, request, paymentPlatform);

            if (!paymentResult.isSuccess()) {
                logger.error("鍏呭€兼敮浠樺垱寤哄け璐? userId={}, orderId={}, error={}", userId, order.getId(),
                        paymentResult.getErrorMessage());
                throw new BusinessException("鏀粯鍒涘缓澶辫触: " + paymentResult.getErrorMessage());
            }

            // 鏇存柊璁㈠崟鐨勬敮浠樺钩鍙颁俊鎭?
            updateOrderProviderInfo(order, paymentResult);

            // 鏋勫缓骞惰繑鍥炲搷搴?
            PaymentResponseDTO response = PaymentAssembler.toPaymentResponseDTO(order, paymentResult);

            logger.info(
                    "鍏呭€兼敮浠樺垱寤烘垚鍔? userId={}, orderId={}, amount={}, platform={}, type={}, providerOrderId={}, providerPaymentId={}",
                    userId, order.getId(), request.getAmount(), paymentPlatform, paymentType,
                    order.getProviderOrderId(), order.getProviderOrderId());

            return response;

        } catch (Exception e) {
            logger.error("鍏呭€兼敮浠樺鐞嗗紓甯? userId={}, orderId={}", userId, order.getId(), e);
            throw new BusinessException("鏀粯澶勭悊澶辫触: " + e.getMessage());
        }
    }

    /** 鍒涘缓鍏呭€艰鍗? */
    private OrderEntity createRechargeOrder(String userId, RechargeRequest request, PaymentPlatform paymentPlatform,
            PaymentType paymentType) {
        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setOrderType(OrderType.RECHARGE);
        order.setTitle("璐︽埛鍏呭€?");
        order.setDescription("璐︽埛浣欓鍏呭€?楼" + request.getAmount());
        order.setAmount(request.getAmount());
        order.setCurrency("CNY");
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentPlatform(paymentPlatform);
        order.setPaymentType(paymentType);

        // 璁剧疆璁㈠崟鍏冩暟鎹紝鍖呭惈涓氬姟鐩稿叧淇℃伅
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("businessType", "balance_recharge");
        metadata.put("rechargeAmount", request.getAmount().toString());
        metadata.put("paymentPlatform", paymentPlatform.getCode());
        metadata.put("paymentType", paymentType.getCode());

        if (request.getRemark() != null && !request.getRemark().trim().isEmpty()) {
            metadata.put("remark", request.getRemark().trim());
            order.setDescription("璐︽埛浣欓鍏呭€?楼" + request.getAmount() + " - " + request.getRemark().trim());
        }

        order.setMetadata(metadata);
        return orderDomainService.createOrder(order);
    }

    /** 閫氳繃鏀粯鎻愪緵鍟嗗垱寤烘敮浠? */
    private PaymentResult createPaymentWithProvider(OrderEntity order, RechargeRequest request,
            PaymentPlatform paymentPlatform) {
        PaymentProvider provider = paymentProviderFactory.getProvider(paymentPlatform);
        PaymentRequest paymentRequest = buildPaymentRequest(order, request);
        return provider.createPayment(paymentRequest);
    }

    /** 鏇存柊璁㈠崟鐨勬敮浠樺钩鍙颁俊鎭? */
    private void updateOrderProviderInfo(OrderEntity order, PaymentResult paymentResult) {
        if (paymentResult.getProviderOrderId() == null && paymentResult.getProviderPaymentId() == null) {
            return;
        }

        logger.info("鏇存柊璁㈠崟鐨勬敮浠樺钩鍙颁俊鎭? orderId={}, providerOrderId={}, providerPaymentId={}", order.getId(),
                paymentResult.getProviderOrderId(), paymentResult.getProviderPaymentId());

        orderDomainService.updateOrderStatusAndProviderInfo(order.getId(), order.getStatus(),
                paymentResult.getProviderOrderId());

        if (paymentResult.getProviderOrderId() != null) {
            order.setProviderOrderId(paymentResult.getProviderOrderId());
        }
    }

    /** 鏋勫缓鏀粯璇锋眰 */
    private PaymentRequest buildPaymentRequest(OrderEntity order, RechargeRequest request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(order.getId());
        paymentRequest.setPaymentId(order.getId());
        paymentRequest.setOrderNo(order.getOrderNo());
        paymentRequest.setTitle(order.getTitle());
        paymentRequest.setDescription(order.getDescription());
        paymentRequest.setAmount(order.getAmount());
        paymentRequest.setCurrency(order.getCurrency());
        paymentRequest.setUserId(order.getUserId());
        paymentRequest.setPaymentType(order.getPaymentType().getCode());

        String platformCode = order.getPaymentPlatform().getCode();
        paymentRequest.setNotifyUrl(joinUrl(paymentBaseUrl, "/payments/callback/" + platformCode));
        paymentRequest.setSuccessUrl(joinUrl(paymentBaseUrl, "/payments/success"));
        paymentRequest.setCancelUrl(joinUrl(paymentBaseUrl, "/payments/cancel"));

        return paymentRequest;
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost:8088/api";
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return normalizedBaseUrl + path;
    }

    /** 鏋勫缓鏀粯璇锋眰锛堜粎鐢ㄤ簬鏌ヨ锛? */
    private PaymentRequest buildPaymentRequest(OrderEntity order) {
        return buildPaymentRequest(order, null);
    }

    /** 鐢熸垚璁㈠崟鍙? */
    private String generateOrderNo() {
        return "RCH" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /** 澶勭悊鏀粯鍥炶皟锛堟柊鎺ュ彛锛岀洿鎺ュ鐞咹TTP璇锋眰锛?
     *
     * @param paymentPlatform 鏀粯骞冲彴浠ｇ爜
     * @param request HTTP璇锋眰瀵硅薄
     * @return 鍥炶皟鍝嶅簲瀛楃涓? */
    @Transactional
    public String handlePaymentCallback(PaymentPlatform paymentPlatform, HttpServletRequest request) {
        try {
            PaymentProvider provider = paymentProviderFactory.getProvider(paymentPlatform);
            PaymentCallback callback = provider.handleCallback(request);

            if (callback.isSignatureValid()) {
                updateOrderStatus(callback);
                logger.info("鏀粯鍥炶皟澶勭悊鎴愬姛: platform={}, orderNo={}, success={}", paymentPlatform, callback.getOrderNo(),
                        callback.isPaymentSuccess());
            } else {
                logger.warn("鏀粯鍥炶皟楠岀澶辫触: platform={}, orderNo={}", paymentPlatform, callback.getOrderNo());
            }

            return provider.getCallbackResponse(callback.isSignatureValid() && callback.isPaymentSuccess());

        } catch (Exception e) {
            logger.error("鏀粯鍥炶皟澶勭悊寮傚父: platform={}", paymentPlatform, e);
            return "failure";
        }
    }

    /** 鏇存柊璁㈠崟鐘舵€?
     *
     * @param callback 鏀粯鍥炶皟瀵硅薄 */
    private void updateOrderStatus(PaymentCallback callback) {
        try {
            String orderNo = callback.getOrderNo();
            if (orderNo == null || orderNo.trim().isEmpty()) {
                logger.warn("鍥炶皟涓病鏈夎鍗曞彿淇℃伅");
                return;
            }

            OrderEntity order = orderDomainService.getOrderByOrderNo(orderNo);
            if (order == null) {
                logger.warn("璁㈠崟涓嶅瓨鍦? orderNo={}", orderNo);
                return;
            }

            if (order.getStatus() != OrderStatus.PENDING) {
                logger.info("璁㈠崟鐘舵€佸凡鏇存柊锛岃烦杩囧鐞? orderNo={}, currentStatus={}", orderNo, order.getStatus());
                return;
            }

            OrderStatus newStatus;
            if (callback.isPaymentSuccess()) {
                newStatus = OrderStatus.PAID;
                logger.info("璁㈠崟鏀粯鎴愬姛: orderNo={}, amount={}, providerOrderId={}, providerPaymentId={}", orderNo,
                        callback.getAmount(), callback.getProviderOrderId(), callback.getProviderPaymentId());
            } else {
                newStatus = OrderStatus.CANCELLED;
                logger.info("璁㈠崟鏀粯澶辫触: orderNo={}", orderNo);
            }

            boolean transitioned = orderDomainService.transitionOrderStatusAndProviderInfo(order.getId(),
                    OrderStatus.PENDING, newStatus, callback.getProviderOrderId());
            if (!transitioned) {
                return;
            }

            order.setStatus(newStatus);
            if (callback.getProviderOrderId() != null) {
                order.setProviderOrderId(callback.getProviderOrderId());
            }
            if (newStatus == OrderStatus.PAID) {
                logger.info("璁㈠崟鏀粯鎴愬姛锛屽彂甯冭喘涔版垚鍔熶簨浠? orderNo={}, orderType={}, amount={}", orderNo,
                        order.getOrderType(), order.getAmount());
                PurchaseSuccessEvent event = new PurchaseSuccessEvent(order);
                eventPublisher.publishEvent(event);
            }

        } catch (Exception e) {
            logger.error("鏇存柊璁㈠崟鐘舵€佸け璐? orderNo={}", callback.getOrderNo(), e);
            throw new BusinessException("璁㈠崟鐘舵€佹洿鏂板け璐? " + e.getMessage());
        }
    }

    /** 鏌ヨ璁㈠崟鐘舵€侊紙鏍规嵁璁㈠崟鍙凤級
     *
     * @param orderNo 璁㈠崟鍙?
     * @return 璁㈠崟鐘舵€佸搷搴? */
    public OrderStatusResponseDTO queryOrderStatus(String orderNo) {
        logger.info("鏌ヨ璁㈠崟鐘舵€? orderNo={}", orderNo);

        try {
            OrderEntity order = getOrderOrThrow(orderNo);

            if (shouldSyncWithProvider(order)) {
                syncOrderStatusFromProvider(order);
            }

            return buildOrderStatusResponse(order);

        } catch (Exception e) {
            logger.error("鏌ヨ璁㈠崟鐘舵€佸け璐? orderNo={}", orderNo, e);
            throw new BusinessException("鏌ヨ璁㈠崟鐘舵€佸け璐? " + e.getMessage());
        }
    }

    /** 鑾峰彇璁㈠崟鎴栨姏鍑哄紓甯? */
    private OrderEntity getOrderOrThrow(String orderNo) {
        OrderEntity order = orderDomainService.findOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("璁㈠崟涓嶅瓨鍦? " + orderNo);
        }
        return order;
    }

    /** 鍒ゆ柇鏄惁闇€瑕佸悓姝ユ敮浠樺钩鍙扮姸鎬? */
    private boolean shouldSyncWithProvider(OrderEntity order) {
        return order.getStatus() == OrderStatus.PENDING;
    }

    /** 鑾峰彇鐢ㄤ簬鏌ヨ鐨勭涓夋柟骞冲彴璁㈠崟ID */
    private String getProviderOrderIdForQuery(PaymentProvider provider, OrderEntity order) {
        return provider.getProviderOrderIdForQuery(order.getOrderNo(), order.getProviderOrderId());
    }

    /** 鍚屾鏀粯骞冲彴鐘舵€? */
    private void syncOrderStatusFromProvider(OrderEntity order) {
        try {
            PaymentProvider provider = paymentProviderFactory.getProvider(order.getPaymentPlatform());
            String providerOrderId = getProviderOrderIdForQuery(provider, order);
            PaymentResult platformResult = provider.queryPayment(providerOrderId);

            if (!platformResult.isSuccess() && platformResult.getStatus() == null) {
                logger.warn("鏌ヨ鏀粯骞冲彴璁㈠崟鐘舵€佸け璐? orderNo={}, error={}", order.getOrderNo(),
                        platformResult.getErrorMessage());
                return;
            }

            OrderStatus platformStatus = provider.convertToOrderStatus(platformResult.getStatus());
            if (platformStatus == order.getStatus()) {
                return;
            }

            updateOrderWithProviderResult(order, platformStatus, platformResult);
            publishPaymentSuccessEventIfNeeded(order, platformStatus);

        } catch (Exception e) {
            logger.warn("鏌ヨ鏀粯骞冲彴璁㈠崟鐘舵€佸紓甯? orderNo={}", order.getOrderNo(), e);
        }
    }

    /** 浣跨敤鏀粯骞冲彴缁撴灉鏇存柊璁㈠崟 */
    private void updateOrderWithProviderResult(OrderEntity order, OrderStatus newStatus, PaymentResult platformResult) {
        OrderStatus oldStatus = order.getStatus();

        logger.info(
                "璁㈠崟鐘舵€佷笉涓€鑷达紝鏇存柊鏈湴鐘舵€? orderNo={}, localStatus={}, platformStatus={}, rawStatus={}, providerOrderId={}, providerPaymentId={}",
                order.getOrderNo(), oldStatus, newStatus, platformResult.getStatus(),
                platformResult.getProviderOrderId(), platformResult.getProviderPaymentId());

        boolean transitioned = orderDomainService.transitionOrderStatusAndProviderInfo(order.getId(), oldStatus,
                newStatus, platformResult.getProviderOrderId());
        if (!transitioned) {
            return;
        }

        order.setStatus(newStatus);
        if (platformResult.getProviderOrderId() != null) {
            order.setProviderOrderId(platformResult.getProviderOrderId());
        }
    }

    /** 濡傛灉鏀粯鎴愬姛鍒欏彂甯冩敮浠樻垚鍔熶簨浠? */
    private void publishPaymentSuccessEventIfNeeded(OrderEntity order, OrderStatus status) {
        if (status != OrderStatus.PAID) {
            return;
        }

        logger.info(
                "璁㈠崟鏀粯鎴愬姛锛屽彂甯冭喘涔版垚鍔熶簨浠? orderNo={}, orderType={}, amount={}, providerOrderId={}, providerPaymentId={}",
                order.getOrderNo(), order.getOrderType(), order.getAmount(), order.getProviderOrderId(),
                order.getProviderOrderId());

        PurchaseSuccessEvent event = new PurchaseSuccessEvent(order);
        eventPublisher.publishEvent(event);
    }

    /** 鏋勫缓璁㈠崟鐘舵€佸搷搴? */
    private OrderStatusResponseDTO buildOrderStatusResponse(OrderEntity order) {
        OrderStatusResponseDTO response = new OrderStatusResponseDTO();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setStatus(order.getStatus());
        response.setPaymentPlatform(order.getPaymentPlatform());
        response.setPaymentType(order.getPaymentType());
        response.setAmount(order.getAmount());
        response.setTitle(order.getTitle());

        response.setCreatedAt(order.getCreatedAt().toString());
        response.setUpdatedAt(order.getUpdatedAt().toString());
        if (order.getExpiredAt() != null) {
            response.setExpiredAt(order.getExpiredAt().toString());
        }

        return response;
    }

    /** 获取可用的支付方法列表 */
    @Transactional(readOnly = true)
    public List<PaymentMethodDTO> getAvailablePaymentMethods() {
        logger.info("Loading available payment methods");

        List<PaymentMethodDTO> methods = new ArrayList<>();

        try {
            List<PaymentPlatform> availablePlatforms = paymentProviderFactory.getAvailablePaymentPlatforms();

            for (PaymentPlatform platform : availablePlatforms) {
                PaymentMethodDTO methodDTO = new PaymentMethodDTO();
                methodDTO.setPlatformCode(platform.getCode());
                methodDTO.setPlatformName(platform.getName());
                methodDTO.setAvailable(true);
                methodDTO.setDescription(getPaymentPlatformDescription(platform));

                List<PaymentMethodDTO.PaymentTypeDTO> paymentTypes = getSupportedPaymentTypes(platform);
                methodDTO.setPaymentTypes(paymentTypes);

                methods.add(methodDTO);
            }

            logger.info("Loaded payment methods successfully: count={}", methods.size());
            return methods;

        } catch (Exception e) {
            logger.error("Failed to load payment methods", e);
            return new ArrayList<>();
        }
    }

    private String getPaymentPlatformDescription(PaymentPlatform platform) {
        switch (platform) {
            case ALIPAY :
                return "支持扫码支付";
            case STRIPE :
                return "支持信用卡支付";
            case WECHAT :
                return "支持扫码支付";
            default :
                return platform.getName() + "支付";
        }
    }

    private List<PaymentMethodDTO.PaymentTypeDTO> getSupportedPaymentTypes(PaymentPlatform platform) {
        List<PaymentMethodDTO.PaymentTypeDTO> types = new ArrayList<>();

        switch (platform) {
            case ALIPAY :
                types.add(new PaymentMethodDTO.PaymentTypeDTO("QR_CODE", "扫码支付", false));
                break;
            case WECHAT :
                types.add(new PaymentMethodDTO.PaymentTypeDTO("QR_CODE", "扫码支付", false));
                break;
            case STRIPE :
                break;
            default :
                break;
        }

        for (PaymentMethodDTO.PaymentTypeDTO type : types) {
            type.setDescription(getPaymentTypeDescription(type.getTypeCode()));
        }

        return types;
    }

    private String getPaymentTypeDescription(String typeCode) {
        switch (typeCode) {
            case "WEB" :
                return "跳转到支付平台网页完成支付";
            case "QR_CODE" :
                return "扫描二维码完成支付";
            case "MOBILE" :
                return "移动端应用内支付";
            case "H5" :
                return "移动端网页支付";
            case "MINI_PROGRAM" :
                return "小程序内支付";
            default :
                return "在线支付";
        }
    }
}
