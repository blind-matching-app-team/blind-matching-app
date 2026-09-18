package com.bma.user.service;

import com.bma.auth.entity.UserToken;
import com.bma.auth.repository.UserTokenRepository;
import com.bma.chat.entity.ChatRoom;
import com.bma.chat.repository.ChatRoomRepository;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.security.TokenType;
import com.bma.matching.entity.Match;
import com.bma.matching.entity.MatchQueue;
import com.bma.matching.repository.MatchQueueRepository;
import com.bma.matching.repository.MatchRepository;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.repository.NotificationRepository;
import com.bma.notification.service.NotificationService;
import com.bma.onboarding.entity.UserAnswer;
import com.bma.onboarding.repository.UserAnswerRepository;
import com.bma.storage.StorageService;
import com.bma.user.dto.UserDtos.PasswordChangeRequest;
import com.bma.user.dto.UserDtos.PasswordChangeResult;
import com.bma.user.dto.UserDtos.WithdrawResult;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.User;
import com.bma.user.repository.ProfileImageRepository;
import com.bma.user.repository.UserPreferenceRepository;
import com.bma.user.repository.UserProfileRepository;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 계정 관리: 비밀번호 변경(S8-16), 회원 탈퇴(S8-15).
 *
 * <p>{@code UserService}(프로필·선호·이미지)와 분리한 이유는 탈퇴가 매칭·채팅·알림·토큰 등
 * 여러 도메인을 한 트랜잭션에서 정리하는 작업이라 의존성이 전혀 다르기 때문이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    /** 소셜 로그인 일회용 티켓의 {@code US_USER_TOKEN.TOKEN_TYPE} 값({@code SocialAuthService} 와 동일). */
    private static final String SOCIAL_TICKET_TOKEN_TYPE = "SOCIAL_TICKET";

    /** 탈퇴로 매칭이 끝났을 때 {@code MT_MATCH.END_REASON_CODE} 에 남기는 값. */
    private static final String END_REASON_WITHDRAWN = "WITHDRAWN";

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final ProfileImageRepository imageRepository;
    private final UserAnswerRepository answerRepository;
    private final UserTokenRepository tokenRepository;
    private final MatchRepository matchRepository;
    private final MatchQueueRepository queueRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 비밀번호를 바꾼다 (S8-16).
     *
     * <p>현재 비밀번호를 확인한 뒤 바꾸고, 다른 기기에 남아 있는 로그인을 끊기 위해
     * 리프레시 토큰을 모두 폐기한다. 지금 쓰고 있는 액세스 토큰은 만료(기본 30분)까지 유효하다.</p>
     *
     * @param userId  요청자
     * @param request 현재/새 비밀번호
     * @return 변경 결과
     * @throws BusinessException 소셜 계정(비밀번호 없음), 현재 비밀번호 불일치, 새 비밀번호가 현재와 같은 경우
     */
    @Transactional
    public PasswordChangeResult changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 소셜로만 가입한 계정은 비밀번호가 없다. S8-16 은 이 계정에 메뉴 자체를 숨긴다.
        if (user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다.");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        int ended = revokeAllTokens(userId);

        log.info("비밀번호 변경: userId={}, 폐기한 세션={}", userId, ended);
        return new PasswordChangeResult(LocalDateTime.now(), ended);
    }

    /**
     * 회원 탈퇴 (S8-15).
     *
     * <p>한 트랜잭션에서 다음을 처리한다.</p>
     * <ol>
     *   <li>진행 중인 매칭을 종료하고 채팅방을 닫는다. 상대에게는 S7-14 종료 알림만 간다(사유 비공개).</li>
     *   <li>매칭 대기열에서 뺀다.</li>
     *   <li>프로필 사진을 저장소에서 지우고, 프로필·선호조건·온보딩 답변·알림을 논리 삭제한다.</li>
     *   <li>리프레시 토큰과 소셜 티켓을 모두 폐기한다.</li>
     *   <li>계정을 {@code WITHDRAWN} 으로 바꾸고 이메일·전화·비밀번호·소셜 키를 익명화한다.</li>
     * </ol>
     *
     * <p>매칭·메시지 행 자체는 남긴다. 상대방의 채팅목록이 매칭 히스토리 역할을 겸하기 때문이다(BMA-49).</p>
     *
     * @param userId 요청자
     * @return 탈퇴 결과
     * @throws BusinessException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public WithdrawResult withdraw(Long userId) {
        User user = userRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1) 진행 중 매칭 종료 + 채팅방 닫기 + 상대 알림
        List<Match> matches = matchRepository.findActiveMatches(userId, Match.STATUS_ACTIVE);
        for (Match match : matches) {
            match.terminate(Match.STATUS_UNMATCHED, userId, END_REASON_WITHDRAWN);
            chatRoomRepository.findByMatchIdAndDeleted(match.getId(), YesNo.N).ifPresent(ChatRoom::close);
            notificationService.notify(match.partnerOf(userId), NotificationEvent.MATCH_ENDED,
                    "매칭이 종료됐어요",
                    "진행 중이던 대화가 마무리되었습니다. 새로운 매칭을 시작해 보세요.",
                    "MATCH", match.getId());
        }

        // 2) 대기열
        queueRepository.findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .ifPresent(MatchQueue::cancel);

        // 3) 프로필 사진(저장소 파일까지) · 프로필 · 선호조건 · 온보딩 답변 · 알림
        for (ProfileImage image : imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N)) {
            image.markDeleted();
            storageService.delete(image.getOriginalObjectKey());
        }
        profileRepository.findById(userId).ifPresent(profile -> profile.anonymize());
        preferenceRepository.findById(userId).ifPresent(preference -> preference.markDeleted());
        answerRepository.findByUserIdAndDeleted(userId, YesNo.N).forEach(UserAnswer::markDeleted);
        notificationRepository.deleteAllOf(userId);

        // 4) 토큰
        revokeAllTokens(userId);

        // 5) 계정 익명화
        user.withdraw();

        log.info("회원 탈퇴: userId={}, 종료된 매칭={}", userId, matches.size());
        return new WithdrawResult(userId, User.STATUS_WITHDRAWN, LocalDateTime.now(), matches.size());
    }

    /**
     * 사용자의 살아 있는 리프레시 토큰과 소셜 티켓을 모두 폐기한다.
     *
     * @param userId 사용자
     * @return 폐기한 리프레시 토큰 수
     */
    private int revokeAllTokens(Long userId) {
        List<UserToken> refreshTokens = tokenRepository
                .findAllByUserIdAndTokenTypeAndDeleted(userId, TokenType.REFRESH.value(), YesNo.N);
        refreshTokens.forEach(UserToken::revoke);
        tokenRepository.findAllByUserIdAndTokenTypeAndDeleted(userId, SOCIAL_TICKET_TOKEN_TYPE, YesNo.N)
                .forEach(UserToken::revoke);
        return refreshTokens.size();
    }
}
