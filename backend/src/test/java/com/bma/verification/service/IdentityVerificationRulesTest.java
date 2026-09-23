package com.bma.verification.service;

import com.bma.verification.service.IdentityVerificationGateway.IdentityResult;
import com.bma.verification.service.IdentityVerificationGateway.IdentityVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BMA-19 안건3(만 19세 판정)과 스텁 인증사·마스킹 규칙을 고정한다.
 */
class IdentityVerificationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @Test
    @DisplayName("만 19세: 19번째 생일 당일부터 성인, 하루 전은 미성년자")
    void adultAtNineteenthBirthday() {
        assertThat(IdentityVerificationService.isAdult(LocalDate.of(2007, 9, 24), TODAY, 19)).isTrue();
        assertThat(IdentityVerificationService.isAdult(LocalDate.of(2007, 9, 25), TODAY, 19)).isFalse();
        assertThat(IdentityVerificationService.isAdult(LocalDate.of(1995, 5, 5), TODAY, 19)).isTrue();
        assertThat(IdentityVerificationService.isAdult(LocalDate.of(2010, 1, 1), TODAY, 19)).isFalse();
        assertThat(IdentityVerificationService.isAdult(null, TODAY, 19)).isFalse();
    }

    @Test
    @DisplayName("윤년 2월 29일생은 평년에는 2월 28일에 만 나이가 오른다")
    void leapDayBirthday() {
        LocalDate birth = LocalDate.of(2008, 2, 29);
        // Java 의 plusYears 는 2월 28일로 맞춘다(우리 나라 행정 관행과 같다). 2월 27일까지는 미성년자.
        assertThat(IdentityVerificationService.isAdult(birth, LocalDate.of(2027, 2, 27), 19)).isFalse();
        assertThat(IdentityVerificationService.isAdult(birth, LocalDate.of(2027, 2, 28), 19)).isTrue();
    }

    @Test
    @DisplayName("스텁 인증사: result 의 이름·생년월일을 그대로 쓰고 CI 는 같은 사람이면 같은 값")
    void stubGateway() {
        StubIdentityVerificationGateway gw = new StubIdentityVerificationGateway();
        Map<String, Object> payload = Map.of("result", Map.of(
                "name", "홍길동", "birthDate", "1995-05-05", "genderCode", "M", "phoneNumber", "01012345678"));
        IdentityResult a = gw.confirm("tx1", payload);
        IdentityResult b = gw.confirm("tx2", payload);
        assertThat(a.birthDate()).isEqualTo(LocalDate.of(1995, 5, 5));
        assertThat(a.ci()).isEqualTo(b.ci()).hasSize(64);
        assertThat(gw.begin("tx1", 1L)).containsKey("sdkToken");

        assertThatThrownBy(() -> gw.confirm("tx3", Map.of()))
                .isInstanceOf(IdentityVerificationException.class);
        assertThatThrownBy(() -> gw.confirm("tx4", Map.of("result", Map.of("name", "홍", "birthDate", "95/05/05"))))
                .isInstanceOf(IdentityVerificationException.class);
    }

    @Test
    @DisplayName("실명·전화번호는 마스킹해서만 남긴다")
    void masking() {
        assertThat(IdentityVerificationService.maskName("홍길동")).isEqualTo("홍*동");
        assertThat(IdentityVerificationService.maskName("김수한무")).isEqualTo("김**무");
        assertThat(IdentityVerificationService.maskName("홍")).isEqualTo("홍*");
        assertThat(IdentityVerificationService.maskName(null)).isNull();
        assertThat(IdentityVerificationService.maskPhone("01012345678")).isEqualTo("010****5678");
        assertThat(IdentityVerificationService.maskPhone("0101")).isEqualTo("0101");
    }
}
