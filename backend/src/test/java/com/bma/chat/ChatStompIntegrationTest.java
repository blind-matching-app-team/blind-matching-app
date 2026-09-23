package com.bma.chat;

import com.bma.chat.controller.ChatSocketController;
import com.bma.chat.dto.ChatDtos.MessageResponse;
import com.bma.chat.dto.ChatDtos.SendMessageRequest;
import com.bma.chat.dto.ChatDtos.SendResult;
import com.bma.chat.service.ChatMessagePublisher;
import com.bma.chat.service.ChatService;
import com.bma.common.config.AppProperties;
import com.bma.common.config.WebSocketConfig;
import com.bma.common.security.JwtTokenProvider;
import com.bma.common.security.StompAuthChannelInterceptor;
import com.bma.support.TestProperties;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BMA-73 WebSocket 인프라를 실제 소켓으로 검증한다 (DB 없이 STOMP 계층만).
 *
 * <p>내장 톰캣을 띄우고 {@link WebSocketStompClient}(표준 WebSocket) 두 개로 붙어
 * 인증(CONNECT JWT), 방 단위 구독 인가, 둘 이상 클라이언트의 실시간 주고받기, 하트비트 협상,
 * 숨김 메시지의 발신자 전용 에코, 재접속 후 수신 복구를 확인한다. 저장 계층({@link ChatService})은 모의 객체다 —
 * DB 저장까지 포함한 종단 검증은 {@code backend/scripts/verify-ws-chat.py} 가 컨테이너 환경에서 수행한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ChatStompIntegrationTest.SocketOnlyApp.class)
class ChatStompIntegrationTest {

    private static final long ROOM = 7L;
    private static final long FORBIDDEN_ROOM = 9L;
    private static final long USER_A = 1L;
    private static final long USER_B = 2L;

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class,
            WebSocketMessagingAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            ValidationAutoConfiguration.class,
            TaskExecutionAutoConfiguration.class
    })
    @Import({WebSocketConfig.class, StompAuthChannelInterceptor.class, ChatSocketController.class,
            ChatMessagePublisher.class, JwtTokenProvider.class})
    static class SocketOnlyApp {
        @Bean
        AppProperties appProperties() {
            return TestProperties.defaults();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private ChatService chatService;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;
    private final AtomicLong messageSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.initialize();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // 서버는 LocalDateTime 을 ISO 문자열로 내보내므로 클라이언트 ObjectMapper 에도 JavaTimeModule 이 있어야 한다.
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(JsonMapper.builder().addModule(new JavaTimeModule()).build());
        stompClient.setMessageConverter(converter);
        stompClient.setTaskScheduler(clientScheduler);
        stompClient.setDefaultHeartbeat(new long[]{10_000, 10_000});

        when(chatService.canAccessRoom(anyLong(), eq(ROOM))).thenReturn(true);
        when(chatService.canAccessRoom(anyLong(), eq(FORBIDDEN_ROOM))).thenReturn(false);
        // 저장 계층은 모의: 요청 본문을 그대로 메시지로 돌려준다. 'hidden:' 으로 시작하면 숨김(차단 상대) 메시지로 취급.
        when(chatService.sendMessage(anyLong(), eq(ROOM), any())).thenAnswer(invocation -> {
            Long sender = invocation.getArgument(0);
            SendMessageRequest request = invocation.getArgument(2);
            boolean hidden = request.content().startsWith("hidden:");
            MessageResponse message = new MessageResponse(messageSeq.incrementAndGet(), ROOM, sender,
                    "TEXT", request.content(), null, LocalDateTime.now());
            return new SendResult(message, hidden);
        });
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        sessions.clear();
        stompClient.stop();
        clientScheduler.shutdown();
    }

    @Test
    @DisplayName("토큰 없이/잘못된 토큰으로 CONNECT 하면 세션이 열리지 않는다")
    void connectRequiresValidAccessToken() {
        assertThatThrownBy(() -> connect(null, new AtomicReference<>()))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("not-a-jwt", new AtomicReference<>()))
                .isInstanceOf(ExecutionException.class);
        // 리프레시 토큰으로도 붙을 수 없다.
        String refresh = tokenProvider.createRefreshToken(USER_A, "a@bma.test", "USER");
        assertThatThrownBy(() -> connect(refresh, new AtomicReference<>()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("하트비트가 10초/10초로 협상된다")
    void heartbeatNegotiated() throws Exception {
        AtomicReference<StompHeaders> connected = new AtomicReference<>();
        connect(accessToken(USER_A), connected);
        assertThat(connected.get().getHeartbeat()).containsExactly(WebSocketConfig.HEARTBEAT_MS, WebSocketConfig.HEARTBEAT_MS);
    }

    @Test
    @DisplayName("둘 이상의 클라이언트가 같은 방을 구독하면 한쪽이 보낸 메시지를 양쪽이 즉시 받는다 (REST 전송도 같은 토픽)")
    void twoClientsExchangeMessagesInRealTime() throws Exception {
        StompSession a = connect(accessToken(USER_A), new AtomicReference<>());
        StompSession b = connect(accessToken(USER_B), new AtomicReference<>());
        BlockingQueue<MessageResponse> inboxA = subscribeRoom(a, ROOM);
        BlockingQueue<MessageResponse> inboxB = subscribeRoom(b, ROOM);

        a.send("/app/chat/" + ROOM + "/send", new SendMessageRequest("TEXT", "안녕하세요", null));
        MessageResponse receivedByB = inboxB.poll(5, TimeUnit.SECONDS);
        MessageResponse echoedToA = inboxA.poll(5, TimeUnit.SECONDS);

        assertThat(receivedByB).isNotNull();
        assertThat(receivedByB.content()).isEqualTo("안녕하세요");
        // 발신자는 페이로드가 아니라 세션 주체에서 정해진다.
        assertThat(receivedByB.senderUserId()).isEqualTo(USER_A);
        assertThat(echoedToA).isNotNull();
        assertThat(echoedToA.messageId()).isEqualTo(receivedByB.messageId());

        b.send("/app/chat/" + ROOM + "/send", new SendMessageRequest(null, "반가워요", null));
        MessageResponse reply = inboxA.poll(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(reply.senderUserId()).isEqualTo(USER_B);
        assertThat(reply.content()).isEqualTo("반가워요");
    }

    @Test
    @DisplayName("참여자가 아닌 방을 구독하면 ERROR 프레임으로 거부된다")
    void subscribeToForeignRoomIsRejected() throws Exception {
        BlockingQueue<StompHeaders> errors = new LinkedBlockingQueue<>();
        StompSession c = connect(accessToken(3L), new AtomicReference<>(), errors);
        c.subscribe("/topic/chat/" + FORBIDDEN_ROOM, new NoopFrameHandler());

        // 인터셉터 예외는 채널 전송 실패로 감싸여 ERROR 프레임이 되고, 서버는 이후 세션을 닫는다.
        StompHeaders error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.getFirst("message")).isNotBlank();
    }

    @Test
    @DisplayName("차단 상대에게 보낸(숨김) 메시지는 방 토픽으로 나가지 않고 발신자 개인 큐로만 돌아온다 (S11-08)")
    void hiddenMessageEchoesOnlyToSender() throws Exception {
        StompSession a = connect(accessToken(USER_A), new AtomicReference<>());
        StompSession b = connect(accessToken(USER_B), new AtomicReference<>());
        BlockingQueue<MessageResponse> roomInboxA = subscribeRoom(a, ROOM);
        BlockingQueue<MessageResponse> roomInboxB = subscribeRoom(b, ROOM);
        BlockingQueue<MessageResponse> echoA = subscribe(a, "/user/queue/chat");

        a.send("/app/chat/" + ROOM + "/send", new SendMessageRequest("TEXT", "hidden:잘 지내?", null));

        MessageResponse echo = echoA.poll(5, TimeUnit.SECONDS);
        assertThat(echo).isNotNull();
        assertThat(echo.content()).isEqualTo("hidden:잘 지내?");
        assertThat(roomInboxB.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(roomInboxA.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("연결이 끊긴 뒤 같은 토큰으로 재접속·재구독하면 이후 메시지를 다시 받는다")
    void reconnectRestoresDelivery() throws Exception {
        String tokenB = accessToken(USER_B);
        StompSession a = connect(accessToken(USER_A), new AtomicReference<>());
        StompSession b = connect(tokenB, new AtomicReference<>());
        subscribeRoom(a, ROOM);
        BlockingQueue<MessageResponse> inboxB = subscribeRoom(b, ROOM);

        b.disconnect();
        a.send("/app/chat/" + ROOM + "/send", new SendMessageRequest("TEXT", "끊긴 사이 메시지", null));
        assertThat(inboxB.poll(1, TimeUnit.SECONDS)).isNull();

        // 재접속: 클라이언트가 할 일은 CONNECT 다시 + 구독 다시. 끊긴 사이 메시지는 REST 이력으로 따라잡는다.
        StompSession b2 = connect(tokenB, new AtomicReference<>());
        BlockingQueue<MessageResponse> inboxB2 = subscribeRoom(b2, ROOM);
        a.send("/app/chat/" + ROOM + "/send", new SendMessageRequest("TEXT", "재접속 후 메시지", null));

        MessageResponse afterReconnect = inboxB2.poll(5, TimeUnit.SECONDS);
        assertThat(afterReconnect).isNotNull();
        assertThat(afterReconnect.content()).isEqualTo("재접속 후 메시지");
    }

    private String accessToken(long userId) {
        return tokenProvider.createAccessToken(userId, "user" + userId + "@bma.test", "USER");
    }

    private StompSession connect(String token, AtomicReference<StompHeaders> connectedHeaders) throws Exception {
        return connect(token, connectedHeaders, new LinkedBlockingQueue<>());
    }

    private StompSession connect(String token, AtomicReference<StompHeaders> connectedHeaders,
                                 BlockingQueue<StompHeaders> errors) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                connectedHeaders.set(headers);
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // ERROR 프레임은 여기로 온다(구독 거부 등).
                errors.add(headers);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                        byte[] payload, Throwable exception) {
                // 페이로드 변환 실패 등은 조용히 묻히므로 눈에 보이게 남긴다.
                System.err.println("STOMP client exception: " + command + " " + exception);
            }
        };
        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(), connectHeaders, handler);
        StompSession session = future.get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<MessageResponse> subscribeRoom(StompSession session, long roomId) throws Exception {
        return subscribe(session, "/topic/chat/" + roomId);
    }

    private BlockingQueue<MessageResponse> subscribe(StompSession session, String destination) throws Exception {
        BlockingQueue<MessageResponse> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((MessageResponse) payload);
            }
        });
        // 심플 브로커의 구독 등록이 다음 SEND 보다 먼저 처리되도록 잠깐 기다린다.
        Thread.sleep(300);
        return inbox;
    }

    private static final class NoopFrameHandler implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
        }
    }
}
