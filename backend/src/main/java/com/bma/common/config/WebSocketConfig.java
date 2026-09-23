package com.bma.common.config;

import com.bma.common.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li>{@code setAllowedOriginPatterns("*")} 하드코딩을 설정 파일 기반으로 바꿔
 *       환경별로 허용 Origin을 통제할 수 있게 했다.</li>
 *   <li>인바운드 채널에 {@link StompAuthChannelInterceptor}를 등록해
 *       CONNECT 인증과 방 단위 인가가 반드시 거쳐지도록 했다.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /** 하트비트 주기(ms). 서버→클라이언트, 클라이언트→서버 모두 이 값이다. 클라이언트는 CONNECT 에 heart-beat 헤더를 보내야 한다. */
    public static final long HEARTBEAT_MS = 10_000L;

    /**
     * 하트비트 전용 스케줄러. 빈으로 등록하지 않는다 — 빈이면 @Scheduled 배치(대기열 스윕, 구독 갱신)까지
     * 이 1스레드 풀을 같이 쓰게 되어 하트비트가 밀릴 수 있다.
     */
    private final ThreadPoolTaskScheduler heartbeatScheduler = createHeartbeatScheduler();

    private static ThreadPoolTaskScheduler createHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독 목적지 접두사. 실제 방 단위 구독 권한은 인터셉터가 검사한다.
        registry.enableSimpleBroker("/topic", "/queue")
                // 심플 브로커는 하트비트를 보내려면 스케줄러가 필요하다. 없으면 heart-beat 헤더가 0,0 으로 협상된다.
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
        // 클라이언트 → 서버 전송 목적지 접두사(@MessageMapping 매핑 대상).
        registry.setApplicationDestinationPrefixes("/app");
        // 사용자 개인 알림 목적지 접두사(/user/queue/...).
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        properties.websocket().allowedOriginPatterns().toArray(String[]::new))
                .withSockJS();
    }

    /**
     * 클라이언트에서 서버로 들어오는 모든 STOMP 프레임에 인증·인가 인터셉터를 건다.
     *
     * <p>여기에 등록하지 않으면 CONNECT 검증이 전혀 동작하지 않으므로 반드시 필요하다.</p>
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
