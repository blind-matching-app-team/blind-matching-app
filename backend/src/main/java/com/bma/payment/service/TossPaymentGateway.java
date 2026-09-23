package com.bma.payment.service;

import com.bma.common.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 토스페이먼츠 코어 API 연동. {@code app.payment.gateway=toss} 일 때 등록된다.
 *
 * <p>인증은 모든 요청에 동일하다: 시크릿 키 뒤에 콜론을 붙여 Base64 로 인코딩한 Basic 인증
 * (docs/TOSS_SANDBOX_VERIFICATION.md 6절). 테스트 키({@code test_})면 가상 승인이라 실제 출금이 없다.
 * 자동결제(빌링)는 테스트 환경에서만 열려 있고 라이브 전환에는 별도 계약이 필요하다.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "toss")
public class TossPaymentGateway implements PaymentGateway {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TossPaymentGateway(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        AppProperties.Payment.Toss toss = properties.payment().toss();
        String credentials = Base64.getEncoder()
                .encodeToString((toss.secretKey().trim() + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(toss.apiBaseUrl())
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Approval approve(String orderId, String paymentKey, BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);
        JsonNode response = post("/v1/payments/confirm", body, null);
        return toApproval(response);
    }

    @Override
    public IssuedBillingKey issueBillingKey(String customerKey, String authKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authKey", authKey);
        body.put("customerKey", customerKey);
        JsonNode response = post("/v1/billing/authorizations/issue", body, null);
        return toBillingKey(response, customerKey);
    }

    @Override
    public IssuedBillingKey issueBillingKeyByCard(String customerKey, CardInfo card) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerKey", customerKey);
        body.put("cardNumber", card.cardNumber());
        body.put("cardExpirationYear", card.expiryYear());
        body.put("cardExpirationMonth", card.expiryMonth());
        body.put("customerIdentityNumber", card.identityNumber());
        if (card.password() != null && !card.password().isBlank()) {
            body.put("cardPassword", card.password());
        }
        JsonNode response = post("/v1/billing/authorizations/card", body, null);
        return toBillingKey(response, customerKey);
    }

    @Override
    public Approval chargeBillingKey(String billingKey, String customerKey, String orderId, String orderName,
                                     BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerKey", customerKey);
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderName", orderName);
        // 같은 주문을 두 번 청구하지 않도록 주문 ID 를 멱등 키로 쓴다(토스: 15일 유효).
        JsonNode response = post("/v1/billing/" + billingKey, body, orderId);
        return toApproval(response);
    }

    @Override
    public Approval cancel(String paymentKey, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", reason);
        JsonNode response = post("/v1/payments/" + paymentKey + "/cancel", body, UUID.randomUUID().toString());
        return toApproval(response);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return WebhookSignatures.verify(properties.payment().webhookSecret(), rawBody, signature);
    }

    private JsonNode post(String path, Map<String, Object> body, String idempotencyKey) {
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(path);
            if (idempotencyKey != null) {
                spec = spec.header("Idempotency-Key", idempotencyKey);
            }
            JsonNode response = spec.body(body).retrieve().body(JsonNode.class);
            if (response == null) {
                throw new GatewayException("EMPTY_RESPONSE", "토스 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientResponseException e) {
            // 토스 오류 본문: {"code":"...","message":"..."}. 카드번호 등 민감 값은 요청에만 있고 응답에는 없다.
            String code = "HTTP_" + e.getStatusCode().value();
            String message = e.getMessage();
            try {
                JsonNode error = objectMapper.readTree(e.getResponseBodyAsString());
                if (error.hasNonNull("code")) {
                    code = error.get("code").asText();
                }
                if (error.hasNonNull("message")) {
                    message = error.get("message").asText();
                }
            } catch (Exception ignored) {
                // 본문이 JSON 이 아니면 HTTP 상태만 남긴다.
            }
            log.warn("토스 API 실패: path={}, code={}, message={}", path, code, message);
            throw new GatewayException(code, message, e);
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("토스 API 통신 오류: path={}", path, e);
            throw new GatewayException("GATEWAY_ERROR", e.getMessage(), e);
        }
    }

    private Approval toApproval(JsonNode response) {
        BigDecimal amount = response.hasNonNull("totalAmount")
                ? response.get("totalAmount").decimalValue() : null;
        return new Approval(text(response, "paymentKey"), text(response, "status"), amount, response.toString());
    }

    private IssuedBillingKey toBillingKey(JsonNode response, String customerKey) {
        String billingKey = text(response, "billingKey");
        if (billingKey == null) {
            throw new GatewayException("NO_BILLING_KEY", "응답에 billingKey 가 없습니다.");
        }
        String company = text(response, "cardCompany");
        String number = text(response, "cardNumber");
        JsonNode card = response.get("card");
        if (card != null) {
            if (company == null) {
                company = text(card, "company");
            }
            if (number == null) {
                number = text(card, "number");
            }
        }
        // 원문에는 빌링키가 들어 있으므로 저장·로그용으로는 가린다.
        String raw = response.toString().replace(billingKey, "***");
        return new IssuedBillingKey(billingKey, customerKey, company, number, raw);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }
}
