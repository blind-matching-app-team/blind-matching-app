package com.bma.storage;

import com.bma.common.config.AppProperties;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 로컬 파일 시스템 기반 저장소 구현.
 *
 * <p>고친 점 — 기존 구현에는 다음 취약점이 있었다.</p>
 * <ul>
 *   <li><b>경로 탈출</b>: 클라이언트가 보낸 파일명에서 확장자를 잘라 그대로 경로에 붙였다.
 *       {@code evil.jpg/../../../app.jar} 같은 이름이면 저장 루트 밖에 파일을 쓸 수 있었다.
 *       → 파일명을 신뢰하지 않고 확장자를 화이트리스트에서만 고르며,
 *          최종 경로가 루트 하위인지 {@code normalize()} 후 재확인한다.</li>
 *   <li><b>타입 검증 없음</b>: 어떤 MIME이든 저장됐다.
 *       → 선언된 Content-Type과 실제 파일 시그니처(매직 바이트)를 모두 검사한다.</li>
 *   <li><b>크기 검증 없음</b>: 멀티파트 전역 한도에만 의존했다. → 건별로 확인한다.</li>
 * </ul>
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    /** 허용 MIME 타입 → 저장 시 사용할 확장자. 클라이언트가 보낸 확장자는 쓰지 않는다. */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            // 사양서 S3-02 가 heic 를 요구한다. 아이폰 기본 촬영 포맷이다.
            "image/heic", ".heic",
            "image/heif", ".heif");

    /** HEIC/HEIF 로 인정하는 ftyp 브랜드. */
    private static final Set<String> HEIF_BRANDS =
            Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1");

    /** 파일 시그니처(매직 바이트) 검사에 필요한 최소 바이트 수. */
    private static final int SIGNATURE_LENGTH = 12;

    private final Path root;
    private final long maxFileSizeBytes;
    private final List<String> allowedContentTypes;

    /**
     * 저장 루트를 준비한다.
     *
     * @param properties 애플리케이션 설정
     */
    public LocalStorageService(AppProperties properties) {
        this.root = Paths.get(properties.storage().root()).toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.storage().maxFileSizeBytes();
        this.allowedContentTypes = properties.storage().allowedContentTypes();
    }

    @Override
    public StoredFile upload(Long userId, MultipartFile file) {
        validate(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("업로드 파일 읽기 실패: userId={}", userId, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }

        String contentType = file.getContentType();
        verifySignature(bytes, contentType);

        // 키는 전적으로 서버가 만든다. 사용자 입력이 경로에 섞이지 않으므로 탈출이 원천 차단된다.
        String objectKey = "profile/" + userId + "/" + UUID.randomUUID()
                + EXTENSION_BY_CONTENT_TYPE.get(contentType);
        Path target = resolveInsideRoot(objectKey);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("파일 저장 실패: key={}", objectKey, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }

        log.info("프로필 이미지 저장: userId={}, key={}, size={}", userId, objectKey, bytes.length);
        return new StoredFile(
                objectKey,
                sanitizeDisplayName(file.getOriginalFilename()),
                contentType,
                bytes.length,
                sha256Hex(bytes));
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveInsideRoot(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 파일이 이미 없어도 업무 흐름을 막을 이유는 없다. 로그만 남기고 넘어간다.
            log.warn("파일 삭제 실패(무시): key={}", objectKey, e);
        }
    }

    /**
     * 업로드 파일의 존재 여부, 크기, 선언된 MIME 타입을 검사한다.
     *
     * @param file 업로드 파일
     * @throws BusinessException 검증 실패 시
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.INVALID_FILE,
                    "파일 크기는 최대 " + (maxFileSizeBytes / (1024 * 1024)) + "MB 까지 허용됩니다.");
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !allowedContentTypes.contains(contentType)
                || !EXTENSION_BY_CONTENT_TYPE.containsKey(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE,
                    "허용되지 않는 파일 형식입니다. 허용 형식: " + allowedContentTypes);
        }
    }

    /**
     * 파일 앞부분의 시그니처가 선언된 MIME 타입과 일치하는지 확인한다.
     *
     * <p>Content-Type 헤더는 클라이언트가 마음대로 보낼 수 있으므로 헤더만 믿으면
     * 확장자만 이미지인 임의 바이너리를 저장하게 된다.</p>
     *
     * @param bytes       파일 내용
     * @param contentType 선언된 MIME 타입
     * @throws BusinessException 시그니처가 일치하지 않는 경우
     */
    private void verifySignature(byte[] bytes, String contentType) {
        if (bytes.length < SIGNATURE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "이미지 파일이 아닙니다.");
        }

        boolean matched = switch (contentType) {
            // JPEG: FF D8 FF
            case "image/jpeg" -> (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            case "image/png" -> (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                    && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A;
            // WEBP: "RIFF" ....(4바이트 크기).... "WEBP"
            case "image/webp" -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            // HEIC/HEIF: ISO base media 컨테이너다. 4~7 이 "ftyp", 8~11 이 브랜드다.
            // 브랜드는 인코더마다 갈려(heic/heix/hevc/mif1/msf1) 하나로 못 박을 수 없다.
            case "image/heic", "image/heif" -> isHeif(bytes);
            default -> false;
        };

        if (!matched) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "파일 내용이 선언된 이미지 형식과 다릅니다.");
        }
    }

    /**
     * HEIC/HEIF 컨테이너인지 확인한다.
     *
     * @param bytes 파일 앞부분
     * @return ftyp 박스의 브랜드가 HEIF 계열이면 {@code true}
     */
    private boolean isHeif(byte[] bytes) {
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
        return HEIF_BRANDS.contains(brand);
    }

    /**
     * 오브젝트 키를 실제 경로로 변환하되, 반드시 저장 루트 하위인지 확인한다.
     *
     * <p>DB에 저장된 키가 어떤 이유로든 오염되어도 루트 밖 파일을 건드리지 못하게 하는 최후 방어선이다.</p>
     *
     * @param objectKey 저장소 키
     * @return 정규화된 절대 경로
     * @throws BusinessException 루트를 벗어나는 경로인 경우
     */
    private Path resolveInsideRoot(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            log.error("저장 루트를 벗어나는 경로 접근 시도: key={}", objectKey);
            throw new BusinessException(ErrorCode.INVALID_FILE, "잘못된 파일 경로입니다.");
        }
        return resolved;
    }

    /**
     * 표시용 파일명에서 경로 구분자를 제거한다.
     *
     * <p>이 값은 경로 조립에 쓰이지 않지만, 화면에 그대로 출력될 수 있으므로 정리해 둔다.</p>
     *
     * @param originalFilename 업로드된 원본 파일명
     * @return 경로 성분이 제거된 파일명
     */
    private String sanitizeDisplayName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "image";
        }
        // 윈도우/유닉스 구분자를 모두 잘라 마지막 이름만 남긴다.
        String name = originalFilename.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replace("..", "").trim();
        if (name.isEmpty()) {
            return "image";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    /**
     * 파일 내용의 SHA-256 해시를 계산한다.
     *
     * @param bytes 파일 내용
     * @return 소문자 16진 해시
     */
    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
