package com.bma.storage;

import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LocalStorageService} 단위 테스트.
 *
 * <p>여기서 검증하는 것은 대부분 보안 회귀 테스트다.
 * 이전 구현은 클라이언트 파일명을 그대로 경로에 붙여 저장 루트 밖에 파일을 쓸 수 있었고,
 * MIME 타입 검증도 없었다.</p>
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempRoot;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(
                TestProperties.withStorage(tempRoot.toString(), 1024 * 1024L, 6));
    }

    @Test
    @DisplayName("정상 PNG 업로드는 저장 루트 안에만 파일을 만든다")
    void upload_storesInsideRoot() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", pngBytes());

        StorageService.StoredFile stored = storageService.upload(7L, file);

        assertThat(stored.objectKey()).startsWith("profile/7/");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.sha256()).hasSize(64);

        Path written = tempRoot.resolve(stored.objectKey()).normalize();
        assertThat(written).exists();
        assertThat(written.startsWith(tempRoot)).isTrue();
    }

    @Test
    @DisplayName("파일명에 경로 탈출 문자가 있어도 저장 루트를 벗어나지 않는다")
    void upload_ignoresPathTraversalInFilename() throws IOException {
        // 이전 구현이라면 상위 디렉터리 성분이 그대로 경로에 반영되어 루트 밖에 파일이 생겼다.
        MockMultipartFile malicious = new MockMultipartFile(
                "file", "../../../evil.png", "image/png", pngBytes());

        StorageService.StoredFile stored = storageService.upload(7L, malicious);

        Path written = tempRoot.resolve(stored.objectKey()).normalize();
        assertThat(written.startsWith(tempRoot)).isTrue();

        // 저장 루트 상위에 새 파일이 생기지 않았는지 확인한다.
        try (Stream<Path> siblings = Files.list(tempRoot.getParent())) {
            assertThat(siblings.map(Path::getFileName).map(Path::toString))
                    .doesNotContain("evil.png");
        }
    }

    @Test
    @DisplayName("허용되지 않은 MIME 타입은 거부된다")
    void upload_rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.bin", "application/octet-stream", new byte[]{1, 2, 3, 4});

        assertThatThrownBy(() -> storageService.upload(7L, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }

    @Test
    @DisplayName("확장자만 이미지인 파일은 시그니처 검사에서 걸러진다")
    void upload_rejectsContentTypeSpoofing() {
        // Content-Type 헤더는 클라이언트가 마음대로 보낼 수 있으므로 내용까지 확인해야 한다.
        byte[] notAnImage = "this is definitely not an image file".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "photo.png", "image/png", notAnImage);

        assertThatThrownBy(() -> storageService.upload(7L, spoofed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("선언된 이미지 형식");
    }

    @Test
    @DisplayName("최대 크기를 초과하면 거부된다")
    void upload_rejectsOversizedFile() {
        LocalStorageService smallLimitService = new LocalStorageService(
                TestProperties.withStorage(tempRoot.toString(), 10L, 6));
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", pngBytes());

        assertThatThrownBy(() -> smallLimitService.upload(7L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("파일 크기");
    }

    @Test
    @DisplayName("삭제 시에도 저장 루트를 벗어나는 키는 거부된다")
    void delete_rejectsEscapingKey() {
        assertThatThrownBy(() -> storageService.delete("../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("잘못된 파일 경로");
    }

    /**
     * 유효한 PNG 시그니처를 가진 최소 바이트 배열을 만든다.
     *
     * @return PNG 헤더로 시작하는 바이트
     */
    private byte[] pngBytes() {
        byte[] bytes = new byte[32];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }
}
