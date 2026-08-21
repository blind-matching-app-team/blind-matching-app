package com.bma.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API가 클라이언트에 내려주는 업무 오류 코드 목록.
 *
 * <p>코드 체계는 {@code <도메인>_<3자리 일련번호>} 형태이며, HTTP 상태와 1:1로 묶어
 * 컨트롤러마다 상태 코드를 다르게 쓰는 일이 없도록 한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ────────────────────────────────────────────────────────────────
    /** 요청 본문/파라미터 검증 실패. */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    /** JSON 파싱 실패 등 요청 형식 자체가 잘못된 경우. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 형식을 해석할 수 없습니다."),
    /** 대상 리소스를 찾을 수 없음. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "대상을 찾을 수 없습니다."),
    /** 현재 상태에서 수행할 수 없는 요청. */
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "현재 상태에서 처리할 수 없습니다."),
    /** 유니크 제약 위반 등 데이터 정합성 충돌. */
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "COMMON_410", "이미 처리된 요청이거나 중복된 데이터입니다."),
    /** 처리되지 않은 서버 내부 오류. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다."),

    // ── 인증/인가 ───────────────────────────────────────────────────────────
    /** 인증 정보가 없거나 자격 증명이 일치하지 않음. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
    /** 서명 불일치, 종류 불일치 등 토큰 자체가 유효하지 않음. */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 토큰입니다."),
    /** 이미 사용 중인 이메일. */
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH_003", "이미 사용 중인 이메일입니다."),
    /** 액세스 토큰 만료. 클라이언트는 리프레시 토큰으로 재발급을 시도해야 한다. */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_004", "토큰이 만료되었습니다."),
    /** 권한이 부족한 요청. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_005", "접근 권한이 없습니다."),
    /** 이미 사용 중인 휴대전화 번호. */
    PHONE_DUPLICATED(HttpStatus.CONFLICT, "AUTH_006", "이미 사용 중인 휴대전화 번호입니다."),
    /** 정지되었거나 탈퇴한 계정의 로그인 시도. */
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "AUTH_007", "이용할 수 없는 계정 상태입니다."),
    /** 이미 회수된 리프레시 토큰이 다시 제출된 경우(탈취 의심). */
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH_008", "만료되었거나 이미 사용된 토큰입니다. 다시 로그인해 주세요."),
    /**
     * 정지된 계정의 로그인 시도.
     *
     * <p>{@link #ACCOUNT_NOT_ACTIVE} 와 분리한 이유: 프론트가 이용정지 화면(S1-18~21)을 띄우려면
     * 정지 종류와 해제 시각이 필요하다. 상세는 응답 {@code data} 에 담긴다.</p>
     */
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_009", "이용이 정지된 계정입니다."),

    // ── 회원 ────────────────────────────────────────────────────────────────
    /** 사용자를 찾을 수 없음. */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    /** 프로필이 아직 등록되지 않음. */
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_002", "프로필이 등록되지 않았습니다."),
    /** 이미 사용 중인 닉네임. */
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER_003", "이미 사용 중인 닉네임입니다."),
    /** 사용자당 이미지 보유 한도를 초과. */
    IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "USER_004", "등록 가능한 이미지 수를 초과했습니다."),
    /** 허용되지 않는 파일 형식/크기. */
    INVALID_FILE(HttpStatus.BAD_REQUEST, "USER_005", "허용되지 않는 파일입니다."),
    /** 파일 저장/삭제 실패. */
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "USER_006", "파일 처리에 실패했습니다."),

    // ── 온보딩 ──────────────────────────────────────────────────────────────
    /** 질문을 찾을 수 없음. */
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "ONB_001", "질문을 찾을 수 없습니다."),
    /** 보기가 해당 질문에 속하지 않음. */
    OPTION_MISMATCH(HttpStatus.BAD_REQUEST, "ONB_002", "질문에 속하지 않는 보기입니다."),
    /** 필수 질문에 답변하지 않음. */
    REQUIRED_ANSWER_MISSING(HttpStatus.BAD_REQUEST, "ONB_003", "필수 질문에 대한 답변이 없습니다."),

    // ── 매칭 ────────────────────────────────────────────────────────────────
    /** 자기 자신을 대상으로 한 요청. */
    SELF_ACTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "MATCH_001", "자기 자신에게는 수행할 수 없습니다."),
    /** 지원하지 않는 액션 타입. */
    INVALID_ACTION_TYPE(HttpStatus.BAD_REQUEST, "MATCH_002", "지원하지 않는 액션 유형입니다."),
    /** 매칭을 찾을 수 없음. */
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_003", "매칭을 찾을 수 없습니다."),
    /** 차단 관계라 진행할 수 없음. */
    BLOCKED_RELATION(HttpStatus.FORBIDDEN, "MATCH_004", "차단된 상대와는 진행할 수 없습니다."),
    /** 프로필이 완성되지 않아 매칭에 참여할 수 없음. */
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "MATCH_005", "프로필을 완성해야 매칭에 참여할 수 있습니다."),

    // ── 채팅 ────────────────────────────────────────────────────────────────
    /** 채팅방을 찾을 수 없음. */
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "채팅방을 찾을 수 없습니다."),
    /** 채팅방 참여자가 아님. */
    NOT_CHAT_MEMBER(HttpStatus.FORBIDDEN, "CHAT_002", "채팅방에 접근할 권한이 없습니다."),
    /** 종료된 채팅방. */
    CHAT_ROOM_CLOSED(HttpStatus.CONFLICT, "CHAT_003", "종료된 채팅방입니다."),

    // ── Reveal ──────────────────────────────────────────────────────────────
    /** 공개 정책을 찾을 수 없음. */
    REVEAL_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "REVEAL_001", "공개 정책을 찾을 수 없습니다."),
    /** 아직 조건을 충족하지 못한 단계로 상승 시도. */
    REVEAL_CONDITION_NOT_MET(HttpStatus.CONFLICT, "REVEAL_002", "아직 공개 조건을 충족하지 않았습니다."),
    /** 현재 단계보다 낮거나 순서를 건너뛴 요청. */
    REVEAL_LEVEL_INVALID(HttpStatus.BAD_REQUEST, "REVEAL_003", "요청한 공개 단계가 올바르지 않습니다."),

    // ── 결제 ────────────────────────────────────────────────────────────────
    /** 상품을 찾을 수 없거나 판매 중지 상태. */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY_001", "상품을 찾을 수 없습니다."),
    /** 결제 승인 실패. */
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAY_002", "결제 승인에 실패했습니다."),
    /** 요청 금액과 상품 금액이 다름(위변조 의심). */
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAY_003", "결제 금액이 일치하지 않습니다."),
    /** 웹훅 서명 검증 실패. */
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "PAY_004", "웹훅 서명 검증에 실패했습니다."),
    /** 결제 건을 찾을 수 없음. */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY_005", "결제 정보를 찾을 수 없습니다."),

    // ── 안전 ────────────────────────────────────────────────────────────────
    /** 이미 신고한 대상에 대한 중복 신고. */
    REPORT_DUPLICATED(HttpStatus.CONFLICT, "SAFE_001", "이미 접수된 신고입니다.");

    /** 이 오류에 대응하는 HTTP 상태 코드. */
    private final HttpStatus status;

    /** 클라이언트가 분기 처리에 사용하는 문자열 코드. */
    private final String code;

    /** 사용자에게 그대로 노출 가능한 기본 메시지. */
    private final String message;
}
