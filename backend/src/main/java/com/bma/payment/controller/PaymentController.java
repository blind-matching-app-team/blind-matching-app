package com.bma.payment.controller;

import com.bma.common.config.AppProperties;
import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.matching.service.MatchingService;
import com.bma.payment.dto.PaymentDtos.ConsumablePurchaseRequest;
import com.bma.payment.dto.PaymentDtos.ConsumablePurchaseResponse;
import com.bma.payment.dto.PaymentDtos.FreeChances;
import com.bma.payment.dto.PaymentDtos.ItemsResponse;
import com.bma.payment.dto.PaymentDtos.PaymentResponse;
import com.bma.payment.dto.PaymentDtos.ProductResponse;
import com.bma.payment.dto.PaymentDtos.SubscriptionRequest;
import com.bma.payment.dto.PaymentDtos.SubscriptionResponse;
import com.bma.payment.dto.PaymentDtos.SubscriptionStatusResponse;
import com.bma.payment.service.PaymentService;
import com.bma.payment.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 이용권 구매·구독 API (BMA-84, 공통컴포넌트 CM-13~17).
 */
@Tag(name = "Payment", description = "상품, 소모형 이용권 결제, 구독, 이용권 잔여")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final MatchingService matchingService;
    private final AppProperties properties;

    @Operation(summary = "상품 목록 (CM-14/15/16)",
            description = "productType=ITEM 은 소모형 탭, SUBSCRIPTION 은 구독형 탭. 가격은 임의치.")
    @GetMapping("/products")
    public ApiResponse<List<ProductResponse>> products() {
        return ApiResponse.ok(paymentService.getProducts());
    }

    @Operation(summary = "소모형 이용권 결제 (CM-15 구매)",
            description = "결제창 승인이면 orderId+paymentKey, 저장 카드 청구면 productCode 만. "
                    + "같은 orderId 재요청은 기존 결제를 그대로 반환한다(멱등, 이용권 중복 지급 없음).")
    @PostMapping("/consumable")
    public ApiResponse<ConsumablePurchaseResponse> purchaseConsumable(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody ConsumablePurchaseRequest request) {
        return ApiResponse.ok(paymentService.purchaseConsumable(principal.userId(), request));
    }

    @Operation(summary = "구독 등록 (CM-17 구독하기)",
            description = "authKey(결제창) 또는 card(직접 입력)로 빌링키를 발급하고 첫 달을 즉시 청구한다. "
                    + "둘 다 없으면 저장된 카드를 쓴다. 이용 중인 구독이 있으면 409.")
    @PostMapping("/subscription")
    public ApiResponse<SubscriptionResponse> subscribe(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                       @Valid @RequestBody(required = false) SubscriptionRequest request) {
        SubscriptionRequest body = request == null ? new SubscriptionRequest(null, null, null) : request;
        return ApiResponse.ok(subscriptionService.subscribe(principal.userId(), body));
    }

    @Operation(summary = "내 구독 상태 (S8-19)")
    @GetMapping("/subscription")
    public ApiResponse<SubscriptionStatusResponse> mySubscription(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(subscriptionService.getMy(principal.userId()));
    }

    @Operation(summary = "구독 해지",
            description = "다음 청구만 멈추고 남은 기간은 그대로 이용한다(환불 없음). 재호출은 멱등.")
    @DeleteMapping("/subscription")
    public ApiResponse<SubscriptionResponse> cancelSubscription(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(subscriptionService.cancel(principal.userId()));
    }

    @Operation(summary = "내 이용권 현황",
            description = "종류별 잔여(구매분/지급분), 오늘의 무료 매칭 기회, 구독 여부. S5-12/S9/S10-07 소진 판정에 쓴다.")
    @GetMapping("/me/items")
    public ApiResponse<ItemsResponse> myItems(@AuthenticationPrincipal CustomUserPrincipal principal) {
        int limit = properties.matching().dailyFreeChances();
        int used = matchingService.freeChancesUsedToday(principal.userId());
        return ApiResponse.ok(new ItemsResponse(
                paymentService.balances(principal.userId()),
                new FreeChances(limit, used, Math.max(limit - used, 0)),
                subscriptionService.isSubscribed(principal.userId())));
    }

    @Operation(summary = "내 결제 내역")
    @GetMapping("/me")
    public ApiResponse<List<PaymentResponse>> myPayments(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(paymentService.getMyPayments(principal.userId()));
    }

    @Operation(summary = "결제 웹훅 수신", description = "서명 검증 후 결제 상태에 반영한다. 인증이 필요 없다.")
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Payment-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawBody, signature);
        return ApiResponse.ok();
    }
}
