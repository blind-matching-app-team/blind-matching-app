package com.bma.common.security;

/**
 * WebSocket 인터셉터가 채팅방 접근 권한을 확인할 때 사용하는 계약.
 *
 * <p>공통 보안 계층이 채팅 도메인 구현에 직접 의존하지 않도록 인터페이스로 분리했다.
 * 실제 구현은 {@code com.bma.chat.service.ChatService}가 담당한다.</p>
 */
public interface ChatRoomAccessChecker {

    /**
     * 사용자가 해당 채팅방의 참여자인지 확인한다.
     *
     * @param userId 확인할 사용자 ID
     * @param roomId 채팅방 ID
     * @return 참여자이며 방이 활성 상태이면 {@code true}
     */
    boolean canAccessRoom(Long userId, Long roomId);
}
