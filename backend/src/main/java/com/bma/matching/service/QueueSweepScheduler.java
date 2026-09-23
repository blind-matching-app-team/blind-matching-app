package com.bma.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 주기 처리: 타임아웃(5분) 만료와 짝 재시도.
 *
 * <p>진입 시점에 짝이 없던 사용자도 이후 들어온 상대와 맺어져야 하므로 주기적으로 훑는다.
 * 주기는 {@code app.matching.queue-sweep-ms}(기본 5초). 다중 인스턴스에서는 분산 락이 필요하다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueSweepScheduler {

    private final QueueMatchingService queueMatchingService;

    @Scheduled(fixedDelayString = "${app.matching.queue-sweep-ms:5000}", initialDelayString = "10000")
    public void sweep() {
        try {
            int matched = queueMatchingService.sweep();
            if (matched > 0) {
                log.info("대기열 스윕: 성사 {}건", matched);
            }
        } catch (Exception e) {
            log.error("대기열 스윕 실패", e);
        }
    }
}
