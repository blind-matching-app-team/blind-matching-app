package com.bma.verification.service;

import com.bma.common.config.AppProperties;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.user.entity.User;
import com.bma.user.repository.UserRepository;
import com.bma.user.service.AccountService;
import com.bma.verification.dto.IdentityVerificationDtos.ConfirmRequest;
import com.bma.verification.dto.IdentityVerificationDtos.ConfirmResponse;
import com.bma.verification.dto.IdentityVerificationDtos.RequestResponse;
import com.bma.verification.dto.IdentityVerificationDtos.StatusResponse;
import com.bma.verification.entity.IdentityVerification;
import com.bma.verification.repository.IdentityVerificationRepository;
import com.bma.verification.service.IdentityVerificationGateway.IdentityResult;
import com.bma.verification.service.IdentityVerificationGateway.IdentityVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * S15 본인인증 (BMA-79).
 *
 * <ul>
 *   <li>요청: 거래 ID 를 만들고 인증사 SDK 파라미터를 돌려준다(10분 유효). 이미 완료한 계정은 409.</li>
 *   <li>확인: 인증사 결과를 검증 → <b>인증사가 준 생년월일</b>로 만 19세 판정(BMA-19 안건3). 미성년자는 계정을 롤백(탈퇴 처리)하고
 *       {@code VERIFY_003}. 같은 CI 가 다른 활성 계정에 있으면 {@code VERIFY_004}. 통과하면 계정에 완료 표시.</li>
 *   <li>완료된 계정은 매칭 진입(S9)에서 재인증을 요구하지 않는다({@code MatchingService.joinQueue}).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityVerificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IdentityVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final IdentityVerificationGateway gateway;
    private final AccountService accountService;
    private final AppProperties properties;

    /**
     * 인증 요청 (S15-04 버튼).
     */
    @Transactional
    public RequestResponse request(Long userId) {
        User user = requireUser(userId);
        if (user.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        // 열려 있던 이전 요청은 닫는다. 거래 ID 는 하나만 살아 있다.
        verificationRepository.findByUserIdAndStatusAndDeleted(userId, IdentityVerification.STATUS_REQUESTED, YesNo.N)
                .forEach(IdentityVerification::expire);

        String transactionId = UUID.randomUUID().toString().replace("-", "");
        int minutes = properties.verification().identity().requestExpireMinutes();
        IdentityVerification saved = verificationRepository.save(
                IdentityVerification.request(userId, transactionId, gateway.provider(), minutes));
        Map<String, Object> sdkParams = gateway.begin(transactionId, userId);
        log.info("본인인증 요청: userId={}, tx={}, provider={}", userId, transactionId, gateway.provider());
        return new RequestResponse(transactionId, gateway.provider(), sdkParams, saved.getExpireDate());
    }

    /**
     * 인증 상태 (S4 분기·S15 재노출 여부).
     */
    public StatusResponse status(Long userId) {
        User user = requireUser(userId);
        String last = verificationRepository.findFirstByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N)
                .map(IdentityVerification::getStatus).orElse(null);
        return new StatusResponse(user.isIdentityVerified(), user.getIdentityVerifiedDate(),
                user.getIdentityProvider(), user.isIdentityVerified(), last);
    }

    /**
     * 인증 결과 확인.
     *
     * <p>미성년자·중복 거부는 예외로 응답하지만 기록(시도 상태, 계정 롤백)은 남아야 하므로 {@link BusinessException} 에는
     * 롤백하지 않는다.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ConfirmResponse confirm(Long userId, ConfirmRequest request) {
        User user = requireUser(userId);
        if (user.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        IdentityVerification attempt = verificationRepository
                .findByTransactionIdAndDeleted(request.transactionId(), YesNo.N)
                .filter(v -> v.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_REQUEST_NOT_FOUND));
        if (!attempt.isRequested()) {
            throw new BusinessException(ErrorCode.IDENTITY_REQUEST_EXPIRED);
        }
        if (attempt.isExpired(LocalDateTime.now())) {
            attempt.expire();
            throw new BusinessException(ErrorCode.IDENTITY_REQUEST_EXPIRED);
        }

        IdentityResult result;
        try {
            result = gateway.confirm(request.transactionId(), request.providerPayload());
        } catch (IdentityVerificationException e) {
            attempt.complete(IdentityVerification.STATUS_FAILED, null, null, null, null, e.getMessage());
            log.info("본인인증 실패: userId={}, tx={}, reason={}", userId, request.transactionId(), e.getMessage());
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e.getMessage());
        }

        String nameMasked = maskName(result.name());
        String phoneMasked = maskPhone(result.phoneNumber());

        // BMA-19 안건3: 인증사가 검증한 생년월일만 법적 기준. 미성년자는 계정 생성 자체를 되돌린다(S15-09).
        if (!isAdult(result.birthDate(), LocalDate.now(KST))) {
            attempt.complete(IdentityVerification.STATUS_REJECTED_MINOR, nameMasked, result.birthDate(),
                    result.genderCode(), phoneMasked, "만 " + adultAge() + "세 미만");
            accountService.withdraw(userId);
            log.info("본인인증 미성년자 거부·계정 롤백: userId={}, tx={}", userId, request.transactionId());
            throw new BusinessException(ErrorCode.IDENTITY_MINOR);
        }

        String ciHash = sha256(result.ci());
        if (userRepository.existsByIdentityCiHashAndDeletedAndIdNot(ciHash, YesNo.N, userId)) {
            attempt.complete(IdentityVerification.STATUS_REJECTED_DUPLICATE, nameMasked, result.birthDate(),
                    result.genderCode(), phoneMasked, "다른 계정에 이미 묶인 CI");
            log.info("본인인증 중복 거부: userId={}, tx={}", userId, request.transactionId());
            throw new BusinessException(ErrorCode.IDENTITY_DUPLICATED);
        }

        attempt.complete(IdentityVerification.STATUS_VERIFIED, nameMasked, result.birthDate(),
                result.genderCode(), phoneMasked, null);
        user.verifyIdentity(gateway.provider(), ciHash, result.birthDate());
        log.info("본인인증 완료: userId={}, tx={}, provider={}", userId, request.transactionId(), gateway.provider());
        return new ConfirmResponse(IdentityVerification.STATUS_VERIFIED, user.getIdentityVerifiedDate(),
                gateway.provider(), true, true);
    }

    /**
     * 만 나이 기준 성인 판정. 생일이 지나야 한 살을 더 먹는다.
     *
     * @param birthDate 인증사가 검증한 생년월일
     * @param today     기준일(KST)
     * @return 만 {@code adultAge}세 이상이면 {@code true}
     */
    public boolean isAdult(LocalDate birthDate, LocalDate today) {
        return isAdult(birthDate, today, adultAge());
    }

    public static boolean isAdult(LocalDate birthDate, LocalDate today, int adultAge) {
        return birthDate != null && !today.isBefore(birthDate.plusYears(adultAge));
    }

    private int adultAge() {
        return properties.verification().identity().adultAge();
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.length() <= 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
