package com.bma.payment.service;

import com.bma.payment.entity.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code PY_PRODUCT.BENEFIT_JSON} 해석.
 *
 * <pre>
 * 소모형: {"itemType":"MATCH_CHANCE","quantity":1}
 * 구독형: {"periodMonths":1,"monthlyGrants":{"MATCH_CHANCE":3,"REMATCH_TICKET":2},"carryOverMonths":2,
 *          "precisionMatching":true,"revealWaitSkip":true}
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ProductBenefits {

    /** 상품 유형: 소모형 이용권. */
    public static final String TYPE_ITEM = "ITEM";

    /** 상품 유형: 구독. */
    public static final String TYPE_SUBSCRIPTION = "SUBSCRIPTION";

    private final ObjectMapper objectMapper;

    /**
     * 해석된 혜택.
     *
     * @param itemType        소모형이 주는 이용권 종류
     * @param quantity        소모형 1건당 수량
     * @param periodMonths    구독 한 주기의 개월 수
     * @param monthlyGrants   구독이 매월 지급하는 이용권 종류별 수량
     * @param carryOverMonths 지급분 이월 상한(개월)
     * @param raw             원본 맵(응답용)
     */
    public record Benefit(String itemType, int quantity, int periodMonths, Map<String, Integer> monthlyGrants,
                          int carryOverMonths, Map<String, Object> raw) {
    }

    /**
     * 상품의 혜택을 해석한다.
     *
     * @param product 상품
     * @return 혜택. 혜택 JSON 이 없으면 빈 값
     */
    public Benefit parse(Product product) {
        if (product.getBenefitJson() == null || product.getBenefitJson().isBlank()) {
            return new Benefit(null, 0, 1, Collections.emptyMap(), 1, Collections.emptyMap());
        }
        try {
            JsonNode node = objectMapper.readTree(product.getBenefitJson());
            Map<String, Object> raw = objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Integer> grants = new LinkedHashMap<>();
            JsonNode grantsNode = node.get("monthlyGrants");
            if (grantsNode != null && grantsNode.isObject()) {
                grantsNode.fields().forEachRemaining(e -> grants.put(e.getKey(), e.getValue().asInt()));
            }
            return new Benefit(
                    node.hasNonNull("itemType") ? node.get("itemType").asText() : null,
                    node.path("quantity").asInt(0),
                    Math.max(node.path("periodMonths").asInt(1), 1),
                    grants,
                    Math.max(node.path("carryOverMonths").asInt(1), 1),
                    raw);
        } catch (Exception e) {
            throw new IllegalStateException("상품 혜택 JSON 을 해석할 수 없습니다: " + product.getProductCode(), e);
        }
    }
}
