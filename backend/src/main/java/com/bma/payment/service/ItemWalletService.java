package com.bma.payment.service;

import com.bma.payment.entity.ItemLedger;
import com.bma.payment.entity.ItemType;
import com.bma.payment.entity.UserItem;
import com.bma.payment.repository.ItemLedgerRepository;
import com.bma.payment.repository.UserItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/**
 * 소모형 이용권 지갑. 지급·사용은 모두 행 잠금 아래에서 처리하고 이력을 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemWalletService {

    private final UserItemRepository itemRepository;
    private final ItemLedgerRepository ledgerRepository;

    /**
     * 월 지급 결과.
     *
     * @param granted 실제로 늘어난 수량
     * @param expired 상한 초과로 소멸한 수량
     * @param skipped 이미 지급된 달이라 건너뛰었으면 {@code true}
     */
    public record GrantResult(int granted, int expired, boolean skipped) {
    }

    /**
     * 사용자의 잔여 행을 모두 가져온다(없는 종류는 포함되지 않는다).
     *
     * @param userId 사용자
     * @return 잔여 행
     */
    public List<UserItem> balances(Long userId) {
        return itemRepository.findByUserIdOrderByItemTypeAsc(userId);
    }

    /**
     * 특정 종류의 총 잔여 개수.
     *
     * @param userId 사용자
     * @param type   종류
     * @return 잔여 개수
     */
    public int balance(Long userId, ItemType type) {
        return balances(userId).stream()
                .filter(i -> i.getItemType().equals(type.name()))
                .mapToInt(UserItem::total)
                .findFirst()
                .orElse(0);
    }

    /**
     * 구매로 얻은 이용권을 더한다.
     *
     * @param userId    사용자
     * @param type      종류
     * @param qty       수량
     * @param paymentId 결제 ID
     * @return 갱신된 지갑
     */
    @Transactional
    public UserItem addPurchased(Long userId, ItemType type, int qty, Long paymentId) {
        UserItem item = lockOrCreate(userId, type);
        item.addPurchased(qty);
        ledgerRepository.save(ItemLedger.of(item, ItemLedger.REASON_PURCHASE, qty,
                ItemLedger.REF_PAYMENT, paymentId, null));
        return item;
    }

    /**
     * 구독 월 지급. 같은 구독·같은 달·같은 종류는 한 번만 지급된다.
     *
     * @param userId          사용자
     * @param subscriptionId  구독
     * @param month           지급 대상 월
     * @param type            종류
     * @param qty             월 지급 수량
     * @param carryOverMonths 이월 상한(개월). 지급분 잔여는 {@code qty × carryOverMonths} 를 넘지 못한다
     * @return 지급 결과
     */
    @Transactional
    public GrantResult grantMonthly(Long userId, Long subscriptionId, YearMonth month, ItemType type, int qty,
                                    int carryOverMonths) {
        String grantKey = subscriptionId + ":" + month + ":" + type.name();
        if (ledgerRepository.existsByGrantKey(grantKey)) {
            return new GrantResult(0, 0, true);
        }
        UserItem item = lockOrCreate(userId, type);
        int expired = item.addGrantedWithCap(qty, qty * carryOverMonths);
        int granted = qty - expired;
        ledgerRepository.save(ItemLedger.of(item, ItemLedger.REASON_GRANT, granted,
                ItemLedger.REF_SUBSCRIPTION, subscriptionId, grantKey));
        if (expired > 0) {
            ledgerRepository.save(ItemLedger.of(item, ItemLedger.REASON_EXPIRE, -expired,
                    ItemLedger.REF_SUBSCRIPTION, subscriptionId, null));
        }
        log.info("구독 월 지급: userId={}, subscriptionId={}, month={}, type={}, granted={}, expired={}",
                userId, subscriptionId, month, type, granted, expired);
        return new GrantResult(granted, expired, false);
    }

    /**
     * 1개를 사용한다. 지급분(소멸 대상)을 먼저 쓴다.
     *
     * @param userId  사용자
     * @param type    종류
     * @param refType 사용처 유형
     * @param refId   사용처 ID
     * @return 사용했으면 {@code true}, 잔여가 없으면 {@code false}
     */
    @Transactional
    public boolean consume(Long userId, ItemType type, String refType, Long refId) {
        UserItem item = itemRepository.findForUpdate(userId, type.name()).orElse(null);
        if (item == null || !item.consumeOne()) {
            return false;
        }
        ledgerRepository.save(ItemLedger.of(item, ItemLedger.REASON_USE, -1, refType, refId, null));
        return true;
    }

    /**
     * 사용했지만 효과가 없었던 이용권을 돌려준다(대기열 5분 타임아웃). 구매분으로 돌려준다.
     *
     * @param userId  사용자
     * @param type    종류
     * @param refType 원래 사용처 유형
     * @param refId   원래 사용처 ID
     */
    @Transactional
    public void refund(Long userId, ItemType type, String refType, Long refId) {
        UserItem item = lockOrCreate(userId, type);
        item.addPurchased(1);
        ledgerRepository.save(ItemLedger.of(item, ItemLedger.REASON_REFUND, 1, refType, refId, null));
        log.info("이용권 환불: userId={}, type={}, ref={}#{}", userId, type, refType, refId);
    }

    private UserItem lockOrCreate(Long userId, ItemType type) {
        return itemRepository.findForUpdate(userId, type.name())
                .orElseGet(() -> itemRepository.saveAndFlush(UserItem.emptyFor(userId, type)));
    }
}
