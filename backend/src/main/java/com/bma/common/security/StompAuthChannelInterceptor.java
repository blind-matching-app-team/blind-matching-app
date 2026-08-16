package com.bma.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 프레임 단위 인증·인가 인터셉터.
 *
 * <p>이 클래스가 해결하는 문제(기존 구현의 가장 심각한 결함)</p>
 * <ul>
 *   <li>기존에는 {@code /ws/**}가 permitAll이고 STOMP 단계에 어떤 검증도 없어
 *       <b>인증 없이 누구나 접속</b>할 수 있었다. → CONNECT 시 JWT를 필수로 검증한다.</li>
 *   <li>발신자 ID를 클라이언트 페이로드에서 받아 <b>타인 사칭</b>이 가능했다.
 *       → 세션 Principal에서만 발신자를 결정하도록 하고, 페이로드의 senderId는 무시한다.</li>
 *   <li>구독 제한이 없어 방 번호만 바꾸면 <b>남의 대화를 도청</b>할 수 있었다.
 *       → SUBSCRIBE/SEND 프레임마다 채팅방 참여자인지 확인한다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** 구독 대상 채팅방 토픽 패턴. 예) /topic/chat/12 */
    private static final Pattern TOPIC_CHAT_PATTERN = Pattern.compile("^/topic/chat/(\\d+)$");

    /** 메시지 전송 대상 패턴. 예) /app/chat/12/send */
    private static final Pattern APP_CHAT_PATTERN = Pattern.compile("^/app/chat/(\\d+)/send$");

    /** Bearer 토큰 접두사. */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final ChatRoomAccessChecker chatRoomAccessChecker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeDestination(accessor, accessor.getDestination(), TOPIC_CHAT_PATTERN);
            case SEND -> authorizeDestination(accessor, accessor.getDestination(), APP_CHAT_PATTERN);
            default -> {
                // DISCONNECT, ACK 등은 추가 검증이 필요 없다.
            }
        }
        return message;
    }

    /**
     * CONNECT 프레임의 Authorization 헤더를 검증하고 세션에 인증 주체를 붙인다.
     *
     * <p>여기서 {@code setUser}로 심어 둔 Principal은 이후 같은 세션의 모든 프레임과
     * {@code @MessageMapping} 핸들러에서 그대로 사용된다.</p>
     *
     * @param accessor CONNECT 프레임 접근자
     * @throws AccessDeniedException 토큰이 없거나 유효하지 않은 경우
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            throw new AccessDeniedException("WebSocket 연결에는 액세스 토큰이 필요합니다.");
        }

        try {
            // 리프레시 토큰으로 소켓에 붙는 것을 막기 위해 ACCESS 종류까지 확인한다.
            Claims claims = tokenProvider.parseAndRequireType(token, TokenType.ACCESS);
            CustomUserPrincipal principal = new CustomUserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class),
                    claims.get(JwtTokenProvider.CLAIM_ROLE, String.class));

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(authentication);

            log.debug("STOMP 연결 인증 성공: userId={}", principal.userId());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("STOMP 연결 인증 실패: {}", e.getMessage());
            throw new AccessDeniedException("유효하지 않은 토큰입니다.");
        }
    }

    /**
     * 목적지에서 채팅방 ID를 추출해 참여자인지 확인한다.
     *
     * @param accessor    현재 프레임 접근자
     * @param destination 목적지 경로
     * @param pattern     허용된 목적지 패턴
     * @throws AccessDeniedException 미인증 세션이거나 참여자가 아닌 방에 접근하는 경우
     */
    private void authorizeDestination(StompHeaderAccessor accessor, String destination, Pattern pattern) {
        CustomUserPrincipal principal = currentPrincipal(accessor);

        if (destination == null) {
            throw new AccessDeniedException("목적지가 없는 프레임입니다.");
        }

        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            // 채팅 이외의 목적지는 이 인터셉터가 관여하지 않는다.
            // 새 기능을 추가할 때는 여기에 명시적으로 허용 패턴을 추가해야 한다.
            throw new AccessDeniedException("허용되지 않은 목적지입니다: " + destination);
        }

        Long roomId = Long.valueOf(matcher.group(1));
        if (!chatRoomAccessChecker.canAccessRoom(principal.userId(), roomId)) {
            log.warn("채팅방 무단 접근 시도: userId={}, roomId={}, destination={}",
                    principal.userId(), roomId, destination);
            throw new AccessDeniedException("채팅방에 접근할 권한이 없습니다.");
        }
    }

    /**
     * 세션에 연결된 인증 주체를 반환한다.
     *
     * @param accessor 현재 프레임 접근자
     * @return 인증 주체
     * @throws AccessDeniedException CONNECT 인증을 거치지 않은 세션인 경우
     */
    private CustomUserPrincipal currentPrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("인증되지 않은 WebSocket 세션입니다.");
    }

    /**
     * STOMP 네이티브 헤더에서 Bearer 토큰을 꺼낸다.
     *
     * @param accessor 프레임 접근자
     * @return 토큰 문자열. 없으면 {@code null}
     */
    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
