package com.bma.payment.controller;

import com.bma.common.response.ApiResponse;
import com.bma.common.security.CustomUserPrincipal;
import com.bma.payment.dto.PaymentDtos.PaymentRequest;
import com.bma.payment.dto.PaymentDtos.PaymentResponse;
import com.bma.payment.dto.PaymentDtos.ProductResponse;
import com.bma.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 결제 API.
 *
 * <p>{@code /webhook}만 비인증 경로다. 외부 PG가 호출하므로 JWT를 가질 수 없고,
 * 대신 서명 검증으로 신뢰성을 확보한다. 기존에는 이 경로가 인증 대상 안에 있어
 * PG 콜백이 401로 튕기는 상태였다.</p>
 */
@Tag(name = "Payment", description = "상품 조회 및 결제")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 판매 중인 상품 목록.
     *
     * @return 상품 목록
     */
    @Operation(summary = "상품 목록")
    @GetMapping("/products")
    public ApiResponse<List<ProductResponse>> products() {
        return ApiResponse.ok(paymentService.getProducts());
    }

    /**
     * 결제 승인.
     *
     * @param principal 인증 주체
     * @param request   결제 요청(금액은 서버가 결정하므로 포함하지 않는다)
     * @return 결제 결과
     */
    @Operation(summary = "결제 승인",
            description = "동일 orderId 재요청은 기존 결제를 그대로 반환한다(멱등). "
                    + "승인 금액은 서버의 상품 가격과 대조된다.")
    @PostMapping
    public ApiResponse<PaymentResponse> pay(@AuthenticationPrincipal CustomUserPrincipal principal,
                                            @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(paymentService.pay(principal.userId(), request));
    }

    /**
     * 내 결제 내역.
     *
     * @param principal 인증 주체
     * @return 결제 목록
     */
    @Operation(summary = "내 결제 내역")
    @GetMapping("/me")
    public ApiResponse<List<PaymentResponse>> myPayments(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(paymentService.getMyPayments(principal.userId()));
    }

    /**
     * PG 웹훅 수신(비인증).
     *
     * <p>본문을 {@code String}으로 받는 이유: 서명은 원문 바이트에 대해 계산되므로,
     * 객체로 역직렬화한 뒤 다시 문자열로 만들면 공백·필드 순서가 달라져 검증이 실패한다.</p>
     *
     * @param rawBody   요청 본문 원문
     * @param signature 서명 헤더
     * @return 빈 성공 응답
     */
    @Operation(summary = "결제 웹훅 수신", description = "서명 검증 후 결제 상태에 반영한다. 인증이 필요 없다.")
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Payment-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawBody, signature);
        return ApiResponse.ok();
    }
}
