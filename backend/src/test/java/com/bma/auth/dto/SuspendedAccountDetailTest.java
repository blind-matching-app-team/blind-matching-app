package com.bma.auth.dto;

import com.bma.auth.dto.AuthDtos.DuplicateEmailDetail;
import com.bma.auth.dto.AuthDtos.SuspendedAccountDetail;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정지 계정 / 이메일 중복 상세의 직렬화 규칙 테스트.
 *
 * <p>명세는 영구 정지일 때도 {@code restrictedUntil} 필드가 <b>항상 존재하고 값만 null</b>
 * 이어야 한다고 못 박고 있다. 그런데 application.yml 의
 * {@code default-property-inclusion: non_null} 이 전역으로 켜져 있어, 아무 조치를 하지 않으면
 * 필드가 통째로 사라진다. 이 테스트는 전역 설정을 그대로 재현한 ObjectMapper 로
 * 그 규칙이 깨지지 않는지 고정한다.</p>
 */
class SuspendedAccountDetailTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // application.yml 의 전역 설정(non_null)을 그대로 재현한다.
        objectMapper = new ObjectMapper();
        // setSerializationInclusion 은 deprecated 라 대체 API 를 쓴다.
        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    @Test
    @DisplayName("영구 정지여도 restrictedUntil 필드가 null 값으로 남는다")
    void permanent_keepsRestrictedUntilFieldAsNull() throws Exception {
        String json = objectMapper.writeValueAsString(
                SuspendedAccountDetail.permanent("반복적인 부적절한 콘텐츠 게시"));

        // 전역 non_null 설정에도 불구하고 키 자체는 사라지지 않아야 한다.
        assertThat(json).contains("\"restrictedUntil\":null");
        assertThat(json).contains("\"restrictionType\":\"PERMANENT\"");
        assertThat(json).contains("\"errorCode\":\"ACCOUNT_SUSPENDED\"");
    }

    @Test
    @DisplayName("기간제 정지는 해제 시각을 UTC 문자열 그대로 싣는다")
    void temporary_carriesRestrictedUntil() throws Exception {
        String json = objectMapper.writeValueAsString(
                SuspendedAccountDetail.temporary("욕설 사용", "2026-09-01T00:00:00Z"));

        assertThat(json).contains("\"restrictionType\":\"TEMPORARY\"");
        assertThat(json).contains("\"restrictedUntil\":\"2026-09-01T00:00:00Z\"");
    }

    @Test
    @DisplayName("영구 정지 상세는 해제 시각을 갖지 않는다")
    void permanent_hasNoRestrictedUntilValue() {
        SuspendedAccountDetail detail = SuspendedAccountDetail.permanent("사기 행위");

        assertThat(detail.restrictionType()).isEqualTo(SuspendedAccountDetail.PERMANENT);
        assertThat(detail.restrictedUntil()).isNull();
        assertThat(detail.reason()).isEqualTo("사기 행위");
    }

    @Test
    @DisplayName("이메일 중복 상세에 기존 계정의 가입 수단이 실린다")
    void duplicateEmail_carriesProvider() throws Exception {
        String json = objectMapper.writeValueAsString(DuplicateEmailDetail.of("KAKAO"));

        assertThat(json).contains("\"errorCode\":\"EMAIL_DUPLICATE\"");
        assertThat(json).contains("\"provider\":\"KAKAO\"");
    }
}
