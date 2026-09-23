package com.bma.verification.service;

import com.bma.common.config.AppProperties;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.storage.StorageService;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.User;
import com.bma.user.repository.ProfileImageRepository;
import com.bma.user.repository.UserRepository;
import com.bma.verification.dto.PhotoVerificationDtos.StatusResponse;
import com.bma.verification.dto.PhotoVerificationDtos.VerifyResponse;
import com.bma.verification.entity.PhotoVerification;
import com.bma.verification.repository.PhotoVerificationRepository;
import com.bma.verification.service.FaceComparisonGateway.FaceComparisonException;
import com.bma.verification.service.FaceComparisonGateway.FaceMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/**
 * S16 사진인증 (BMA-82).
 *
 * <p><b>촬영 이미지 미저장 원칙</b>: 셀피는 요청 본문에서 바이트 배열로만 읽어 얼굴 대조에 쓰고, 대조가 끝나면
 * 배열을 0 으로 덮어 버린다. 저장소·DB·로그 어디에도 쓰지 않는다. 이 클래스 밖으로 셀피 바이트가 나가는 곳은
 * {@link FaceComparisonGateway#compare} 하나뿐이다.</p>
 *
 * <ul>
 *   <li>프로필 사진(S3)이 있어야 대조할 수 있다(없으면 409 VERIFY_010).</li>
 *   <li>유사도가 기준({@code app.verification.photo.similarity-threshold}) 이상이면 완료 배지. 미만이면 422 VERIFY_011,
 *       재시도 횟수 제한 없음(S16-04d).</li>
 *   <li>완료 후 프로필 사진을 바꾸면 인증이 해제된다(다른 사진으로 배지를 달고 다니지 못하게).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoVerificationService {

    private final PhotoVerificationRepository verificationRepository;
    private final ProfileImageRepository imageRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final FaceComparisonGateway gateway;
    private final AppProperties properties;

    /**
     * 사진인증 상태 (S8-17 메뉴 표시).
     */
    public StatusResponse status(Long userId) {
        User user = requireUser(userId);
        long attempts = verificationRepository.countByUserIdAndDeleted(userId, YesNo.N);
        String last = verificationRepository.findFirstByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N)
                .map(PhotoVerification::getResult).orElse(null);
        return new StatusResponse(user.isPhotoVerified(), user.getPhotoVerifiedDate(), user.getPhotoVerifyProvider(),
                user.getPhotoVerifiedImageId(), attempts, last);
    }

    /**
     * 셀피를 프로필 사진과 대조한다 (S16-04 촬영하기).
     *
     * <p>실패·오류도 시도 이력은 남아야 하므로 {@link BusinessException} 에는 롤백하지 않는다.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public VerifyResponse verify(Long userId, MultipartFile selfie) {
        User user = requireUser(userId);
        if (user.isPhotoVerified()) {
            throw new BusinessException(ErrorCode.PHOTO_ALREADY_VERIFIED);
        }
        ProfileImage image = imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(userId, YesNo.N).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PHOTO_PROFILE_IMAGE_REQUIRED));

        double threshold = properties.verification().photo().similarityThreshold();
        byte[] selfieBytes = readSelfie(selfie);
        String selfieType = ImageBytes.detect(selfieBytes);
        if (selfieType == null || !properties.storage().allowedContentTypes().contains(selfieType)) {
            Arrays.fill(selfieBytes, (byte) 0);
            throw new BusinessException(ErrorCode.INVALID_FILE, "이미지 파일이 아닙니다.");
        }

        FaceMatch match;
        try {
            byte[] reference = storageService.read(image.getOriginalObjectKey());
            match = gateway.compare(selfieBytes, selfieType, reference, image.getContentType());
        } catch (FaceComparisonException e) {
            verificationRepository.save(PhotoVerification.of(userId, gateway.provider(), PhotoVerification.RESULT_ERROR,
                    null, threshold, image.getId(), e.getMessage()));
            log.warn("얼굴 대조 제공자 오류: userId={}, provider={}, reason={}", userId, gateway.provider(), e.getMessage());
            throw new BusinessException(ErrorCode.PHOTO_COMPARISON_FAILED);
        } finally {
            // 촬영 이미지는 대조 즉시 폐기한다. 참조가 남아 있어도 내용은 사라진다.
            Arrays.fill(selfieBytes, (byte) 0);
        }

        boolean passed = match.faceDetected() && match.similarity() >= threshold;
        if (!passed) {
            String reason = match.faceDetected() ? "유사도 " + match.similarity() + " < 기준 " + threshold : "얼굴을 찾지 못함";
            verificationRepository.save(PhotoVerification.of(userId, gateway.provider(), PhotoVerification.RESULT_FAIL,
                    match.similarity(), threshold, image.getId(), reason));
            log.info("사진인증 실패: userId={}, similarity={}, threshold={}, faceDetected={}",
                    userId, match.similarity(), threshold, match.faceDetected());
            throw new BusinessException(ErrorCode.PHOTO_MISMATCH,
                    match.faceDetected() ? ErrorCode.PHOTO_MISMATCH.getMessage() : "얼굴을 찾지 못했어요. 얼굴을 원 안에 맞춰 다시 촬영해 주세요.");
        }

        verificationRepository.save(PhotoVerification.of(userId, gateway.provider(), PhotoVerification.RESULT_PASS,
                match.similarity(), threshold, image.getId(), null));
        user.verifyPhoto(gateway.provider(), image.getId());
        log.info("사진인증 완료: userId={}, similarity={}, imageId={}, provider={}",
                userId, match.similarity(), image.getId(), gateway.provider());
        return new VerifyResponse(true, match.similarity(), threshold, gateway.provider(),
                user.getPhotoVerifiedDate(), image.getId());
    }

    private byte[] readSelfie(MultipartFile selfie) {
        if (selfie == null || selfie.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "촬영 이미지가 비어 있습니다.");
        }
        long max = properties.verification().photo().maxSelfieBytes();
        if (selfie.getSize() > max) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "촬영 이미지는 최대 " + (max / (1024 * 1024)) + "MB 까지 허용됩니다.");
        }
        try {
            return selfie.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "촬영 이미지를 읽을 수 없습니다.");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 파일 시그니처로 이미지 형식을 판별한다. 클라이언트가 보낸 Content-Type 은 믿지 않는다.
     */
    static final class ImageBytes {
        private ImageBytes() {
        }

        static String detect(byte[] b) {
            if (b == null || b.length < 12) {
                return null;
            }
            if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return "image/png";
            }
            if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return "image/webp";
            }
            if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
                String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if (brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf")) {
                    return brand.startsWith("heif") || brand.startsWith("mif") ? "image/heif" : "image/heic";
                }
            }
            return null;
        }
    }
}
