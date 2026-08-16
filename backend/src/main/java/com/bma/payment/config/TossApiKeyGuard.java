package com.bma.payment.config;

import com.bma.common.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 토스페이먼츠 API 키 조합을 기동 시점에 검증한다.
 *
 * <p>결제 키 설정 실수는 런타임에야 드러나고, 그때는 이미 실결제가 발생한 뒤일 수 있다.
 * {@code JwtTokenProvider}가 운영 프로파일에서 기본 시크릿을 거부하는 것과 같은 방식으로
 * 생성자에서 검증해 잘못된 조합이면 기동 자체를 막는다.</p>
 *
 * <p>막는 경우는 넷이다.</p>
 * <ol>
 *   <li>{@code gateway=toss}인데 키가 비어 있는 경우 — 호출 시점에 401로 터진다.</li>
 *   <li>키가 {@code test_}/{@code live_} 어느 쪽으로도 시작하지 않는 경우 — 환경 판별이 불가능하다.</li>
 *   <li>클라이언트 키와 시크릿 키의 환경이 다른 경우 — 토스가 {@code INVALID_API_KEY}를 반환한다.</li>
 *   <li>운영 프로파일이 아닌데 라이브 키가 들어온 경우 — <b>실결제 위험</b>이다.</li>
 * </ol>
 */
@Slf4j
@Component
public class TossApiKeyGuard {

    /** 테스트 키 접두사. 이 키로는 결제가 가상으로만 이뤄진다. */
    private static final String TEST_PREFIX = "test_";

    /** 라이브 키 접두사. 이 키로는 실제 금액이 청구된다. */
    private static final String LIVE_PREFIX = "live_";

    /** 토스 게이트웨이를 사용하겠다는 설정 값. */
    private static final String TOSS_GATEWAY = "toss";

    /**
     * 설정을 검증한다. 문제가 있으면 예외를 던져 기동을 중단시킨다.
     *
     * @param properties  바인딩된 애플리케이션 설정
     * @param environment 활성 프로파일 판별용
     * @throws IllegalStateException 키 조합이 잘못된 경우
     */
    public TossApiKeyGuard(AppProperties properties, Environment environment) {
        AppProperties.Payment payment = properties.payment();
        AppProperties.Payment.Toss toss = payment.toss();

        String clientKey = trimToNull(toss == null ? null : toss.clientKey());
        String secretKey = trimToNull(toss == null ? null : toss.secretKey());
        boolean useToss = TOSS_GATEWAY.equalsIgnoreCase(payment.gateway());

        if (useToss && (clientKey == null || secretKey == null)) {
            throw new IllegalStateException(
                    "app.payment.gateway=toss 인데 토스 API 키가 비어 있습니다. "
                            + "TOSS_CLIENT_KEY 와 TOSS_SECRET_KEY 를 설정하세요.");
        }

        if (clientKey == null && secretKey == null) {
            // 스텁으로 개발하는 환경. 검증할 키가 없다.
            return;
        }

        requireKnownPrefix("TOSS_CLIENT_KEY", clientKey);
        requireKnownPrefix("TOSS_SECRET_KEY", secretKey);

        boolean clientLive = isLive(clientKey);
        boolean secretLive = isLive(secretKey);

        if (clientKey != null && secretKey != null && clientLive != secretLive) {
            throw new IllegalStateException(
                    "클라이언트 키와 시크릿 키의 환경이 서로 다릅니다(테스트/라이브 혼용). "
                            + "두 키는 세트로만 동작하며 섞어 쓰면 토스가 INVALID_API_KEY 를 반환합니다. "
                            + "clientKey=" + mask(clientKey) + ", secretKey=" + mask(secretKey));
        }

        boolean productionProfile = environment.matchesProfiles("prod", "production");

        if ((clientLive || secretLive) && !productionProfile) {
            throw new IllegalStateException(
                    "운영 프로파일이 아닌데 라이브 키(live_)가 설정되어 있습니다. "
                            + "실제 결제가 발생할 수 있어 기동을 중단합니다. "
                            + "테스트 키(test_)를 사용하세요. key=" + mask(clientLive ? clientKey : secretKey));
        }

        if (productionProfile && !clientLive && !secretLive) {
            log.warn("운영 프로파일인데 테스트 키(test_)가 설정되어 있습니다. "
                    + "결제가 가상으로만 처리되어 실제 매출이 발생하지 않습니다.");
        }

        log.info("토스페이먼츠 키 검증 통과. 환경={}, gateway={}",
                clientLive || secretLive ? "라이브" : "테스트", payment.gateway());
    }

    /**
     * 키가 알려진 접두사로 시작하는지 확인한다.
     *
     * <p>오타나 값 잘림을 조기에 잡는다. 접두사를 모르면 테스트/라이브 판별이 불가능해
     * 실결제 차단 자체가 무력해지므로 통과시키지 않는다.</p>
     *
     * @param name 설정 이름(오류 메시지용)
     * @param key  검사할 키
     * @throws IllegalStateException 접두사가 {@code test_}/{@code live_} 둘 다 아닌 경우
     */
    private void requireKnownPrefix(String name, String key) {
        if (key == null) {
            return;
        }
        if (!key.startsWith(TEST_PREFIX) && !key.startsWith(LIVE_PREFIX)) {
            throw new IllegalStateException(
                    name + " 값이 test_ 또는 live_ 로 시작하지 않습니다. "
                            + "토스 개발자센터에서 발급한 키를 그대로 넣었는지 확인하세요. value=" + mask(key));
        }
    }

    /**
     * 라이브 키인지 판별한다.
     *
     * @param key 검사할 키
     * @return {@code live_}로 시작하면 {@code true}
     */
    private boolean isLive(String key) {
        return key != null && key.startsWith(LIVE_PREFIX);
    }

    /**
     * 공백뿐인 값을 {@code null}로 바꾼다.
     *
     * @param value 원본 값
     * @return 비어 있지 않으면 앞뒤 공백을 제거한 값, 아니면 {@code null}
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 로그와 예외 메시지에 키 전문이 남지 않도록 가린다.
     *
     * @param key 원본 키
     * @return 접두사와 끝 4자리만 남긴 문자열
     */
    private String mask(String key) {
        if (key == null) {
            return "(없음)";
        }
        if (key.length() <= 12) {
            return "***";
        }
        return key.substring(0, 8) + "***" + key.substring(key.length() - 4);
    }
}
