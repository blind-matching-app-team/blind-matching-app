package com.bma.payment.dto;

import com.bma.payment.entity.BillingKey;
import com.bma.payment.entity.Payment;
import com.bma.payment.entity.Product;
import com.bma.payment.entity.Subscription;
import com.bma.payment.entity.UserItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 결제·구독·이용권 API DTO 모음 (BMA-84, 공통컴포넌트 사양서 v1.7 CM-13~17).
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * 상품 (CM-15 소모형 카드 / CM-16 구독형 카드).
     *
     * @param productId   상품 ID
     * @param productCode 상품 코드(MATCH_CHANCE/REMATCH_TICKET/PREMIUM_MONTHLY)
     * @param productName 표시명
     * @param productType ITEM(소모형) / SUBSCRIPTION(구독형) — CM-14 탭 구분
     * @param price       가격(임의치)
     * @param currency    통화
     * @param benefit     혜택 구성. 소모형은 itemType/quantity, 구독형은 monthlyGrants/carryOverMonths 등
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProductResponse(Long productId,
                                  String productCode,
                                  String productName,
                                  String productType,
                                  BigDecimal price,
                                  String currency,
                                  Map<String, Object> benefit) {

        public static ProductResponse of(Product product, Map<String, Object> benefit) {
            return new ProductResponse(product.getId(), product.getProductCode(), product.getProductName(),
                    product.getProductType(), product.getPrice(), product.getCurrencyCode(), benefit);
        }
    }

    /**
     * 소모형 단건 결제 요청 (CM-15 '구매').
     *
     * <p>결제창(토스 결제위젯)으로 결제했으면 {@code orderId}+{@code paymentKey} 를 보낸다.
     * 둘 다 없으면 저장된 카드(빌링키)로 서버가 바로 청구한다.</p>
     *
     * @param productCode 상품 코드(소모형만)
     * @param orderId     결제창에 넘겼던 주문 ID(6~64자, 영문/숫자/-/_)
     * @param paymentKey  결제창이 준 결제 키
     */
    public record ConsumablePurchaseRequest(
            @NotBlank(message = "상품 코드는 필수입니다.") String productCode,
            @Size(min = 6, max = 64, message = "주문 ID는 6~64자여야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "주문 ID는 영문, 숫자, -, _ 만 쓸 수 있습니다.")
            String orderId,
            @Size(max = 200, message = "결제 키는 200자를 넘을 수 없습니다.") String paymentKey
    ) {
        public boolean hasWidgetPayment() {
            return paymentKey != null && !paymentKey.isBlank();
        }
    }

    /**
     * 카드 직접 등록 정보(테스트 환경·API 개별 연동). 결제창(authKey) 방식이 기본이다.
     *
     * @param cardNumber     카드번호
     * @param expiryYear     유효기간 연도 2자리
     * @param expiryMonth    유효기간 월 2자리
     * @param identityNumber 생년월일 6자리 또는 사업자번호 10자리
     * @param password       비밀번호 앞 2자리(선택)
     */
    public record CardRequest(
            @NotBlank(message = "카드번호는 필수입니다.") @Pattern(regexp = "^\\d{13,19}$", message = "카드번호 형식이 올바르지 않습니다.")
            String cardNumber,
            @NotBlank(message = "유효기간 연도는 필수입니다.") @Pattern(regexp = "^\\d{2}$", message = "연도는 2자리입니다.")
            String expiryYear,
            @NotBlank(message = "유효기간 월은 필수입니다.") @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "월은 01~12 입니다.")
            String expiryMonth,
            @NotBlank(message = "생년월일 또는 사업자번호는 필수입니다.") @Pattern(regexp = "^(\\d{6}|\\d{10})$", message = "6자리 또는 10자리 숫자입니다.")
            String identityNumber,
            @Pattern(regexp = "^\\d{2}$", message = "비밀번호는 앞 2자리입니다.") String password
    ) {
    }

    /**
     * 구독 등록 요청 (CM-17 '구독하기').
     *
     * <p>{@code authKey}(결제창 빌링 인증) 또는 {@code card}(직접 입력) 중 하나로 빌링키를 발급한다.
     * 둘 다 없으면 이미 저장된 카드를 쓴다.</p>
     *
     * @param productCode 구독 상품 코드. 생략하면 PREMIUM_MONTHLY
     * @param authKey     결제창이 준 authKey
     * @param card        카드 직접 입력
     */
    public record SubscriptionRequest(String productCode,
                                      @Size(max = 300) String authKey,
                                      @Valid CardRequest card) {
    }

    /**
     * 결제 1건.
     *
     * @param paymentId    결제 ID
     * @param orderId      주문 ID
     * @param orderName    주문명
     * @param productId    상품 ID
     * @param paymentKind  CONSUMABLE/SUBSCRIPTION
     * @param payMethod    WIDGET/BILLING
     * @param status       READY/DONE/CANCELED/FAILED
     * @param amount       금액
     * @param currency     통화
     * @param approvedDate 승인 일시
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PaymentResponse(Long paymentId,
                                  String orderId,
                                  String orderName,
                                  Long productId,
                                  String paymentKind,
                                  String payMethod,
                                  String status,
                                  BigDecimal amount,
                                  String currency,
                                  LocalDateTime approvedDate) {

        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getOrderName(),
                    payment.getProductId(), payment.getPaymentKind(), payment.getPayMethod(),
                    payment.getPaymentStatus(), payment.getAmount(), payment.getCurrencyCode(),
                    payment.getApprovedDate());
        }
    }

    /**
     * 이용권 종류별 잔여.
     *
     * @param itemType  MATCH_CHANCE/REMATCH_TICKET
     * @param purchased 구매분(소멸 없음)
     * @param granted   구독 지급분(최대 2개월치)
     * @param total     합계
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ItemBalance(String itemType, int purchased, int granted, int total) {

        public static ItemBalance from(UserItem item) {
            return new ItemBalance(item.getItemType(), item.getPurchasedQty(), item.getGrantedQty(), item.total());
        }

        public static ItemBalance zero(String itemType) {
            return new ItemBalance(itemType, 0, 0, 0);
        }
    }

    /**
     * 오늘의 무료 매칭 기회.
     *
     * @param limit     하루 무료 횟수
     * @param used      오늘 사용한 횟수
     * @param remaining 남은 무료 횟수
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FreeChances(int limit, int used, int remaining) {
    }

    /**
     * 내 이용권 현황 (S5-12/S9/S10-07 소진 판정, S8-19).
     *
     * @param items           종류별 잔여(항상 2종 모두 포함)
     * @param freeChancesToday 오늘의 무료 매칭 기회
     * @param subscribed      이용 중인 구독이 있는지
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ItemsResponse(List<ItemBalance> items, FreeChances freeChancesToday, boolean subscribed) {
    }

    /**
     * 소모형 결제 결과 (CM-15 → "결제가 완료됐어요" 토스트, 모달 유지).
     *
     * @param payment 결제
     * @param items   결제 후 잔여
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConsumablePurchaseResponse(PaymentResponse payment, List<ItemBalance> items) {
    }

    /**
     * 저장된 카드 요약.
     *
     * @param company      카드사
     * @param numberMasked 마스킹 카드번호
     * @param registeredAt 등록 일시
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CardSummary(String company, String numberMasked, LocalDateTime registeredAt) {

        public static CardSummary from(BillingKey key) {
            return key == null ? null
                    : new CardSummary(key.getCardCompany(), key.getCardNumberMasked(), key.getIssuedDate());
        }
    }

    /**
     * 구독 상세.
     *
     * @param subscriptionId     구독 ID
     * @param status             ACTIVE/PAST_DUE/CANCELED/EXPIRED
     * @param productCode        상품 코드
     * @param price              월 요금
     * @param currency           통화
     * @param startedDate        최초 시작 일시
     * @param currentPeriodStart 현재 기간 시작일
     * @param currentPeriodEnd   현재 기간 종료일(이날까지 이용)
     * @param nextBillingDate    다음 청구일. 해지/종료면 null
     * @param canceledDate       해지 요청 일시
     * @param endedDate          종료 일시
     * @param card               청구 카드
     * @param monthlyGrants      매월 지급 이용권
     * @param carryOverMonths    이월 상한(개월)
     * @param lastPaymentId      마지막 결제 ID
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubscriptionResponse(Long subscriptionId,
                                       String status,
                                       String productCode,
                                       BigDecimal price,
                                       String currency,
                                       LocalDateTime startedDate,
                                       LocalDate currentPeriodStart,
                                       LocalDate currentPeriodEnd,
                                       LocalDate nextBillingDate,
                                       LocalDateTime canceledDate,
                                       LocalDateTime endedDate,
                                       CardSummary card,
                                       Map<String, Integer> monthlyGrants,
                                       int carryOverMonths,
                                       Long lastPaymentId) {

        public static SubscriptionResponse of(Subscription s, Product product, BillingKey key,
                                              Map<String, Integer> monthlyGrants, int carryOverMonths) {
            return new SubscriptionResponse(s.getId(), s.getSubStatus(), product.getProductCode(),
                    product.getPrice(), product.getCurrencyCode(), s.getStartedDate(), s.getCurrentPeriodStart(),
                    s.getCurrentPeriodEnd(), s.getNextBillingDate(), s.getCanceledDate(), s.getEndedDate(),
                    CardSummary.from(key), monthlyGrants, carryOverMonths, s.getLastPaymentId());
        }
    }

    /**
     * 내 구독 상태 (S8-19). 없으면 {@code subscribed=false, subscription=null}.
     *
     * @param subscribed   혜택을 받는 중인지(해지했어도 기간이 남았으면 true)
     * @param subscription 최근 구독. 한 번도 구독한 적 없으면 null
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubscriptionStatusResponse(boolean subscribed, SubscriptionResponse subscription) {
    }

    /**
     * 청구 배치 실행 결과.
     *
     * @param asOf     기준일
     * @param due      청구 대상 수
     * @param renewed  청구 성공
     * @param failed   청구 실패(유예)
     * @param expired  종료(유예 초과 또는 해지 기간 만료)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RenewResult(LocalDate asOf, int due, int renewed, int failed, int expired) {
    }
}
