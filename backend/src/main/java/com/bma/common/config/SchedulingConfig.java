package com.bma.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화. 현재 사용처: 구독 정기 청구({@code SubscriptionScheduler}).
 *
 * <p>인스턴스를 여러 대로 늘리면 같은 배치가 중복 실행될 수 있다. 월 지급은 멱등 키로 막혀 있지만
 * 청구 자체는 그렇지 않으므로, 다중 인스턴스 전에는 분산 락(ShedLock 등)을 붙여야 한다.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
