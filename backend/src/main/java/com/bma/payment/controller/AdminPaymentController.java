package com.bma.payment.controller;

import com.bma.common.response.ApiResponse;
import com.bma.payment.dto.PaymentDtos.RenewResult;
import com.bma.payment.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 결제 운영 API. {@code /api/v1/admin/**} 는 SecurityConfig 에서 ROLE_ADMIN 을 요구한다.
 */
@Tag(name = "Admin/Payment", description = "구독 청구 배치 수동 실행")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "구독 청구 배치 수동 실행",
            description = "asOf(기본 오늘) 기준으로 청구일이 지난 구독을 청구하고 해지 후 기간이 끝난 구독을 종료한다. "
                    + "같은 달 지급은 멱등이라 여러 번 실행해도 이용권이 중복 지급되지 않는다.")
    @PostMapping("/subscriptions/renew")
    public ApiResponse<RenewResult> renew(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.ok(subscriptionService.renewDue(asOf == null ? LocalDate.now() : asOf));
    }
}
