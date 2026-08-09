package com.bma.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 저장소 추상화.
 *
 * <p>현재는 로컬 파일 시스템 구현({@link LocalStorageService})만 있지만,
 * S3 등으로 교체할 수 있도록 인터페이스로 분리해 둔다.</p>
 */
public interface StorageService {

    /**
     * 저장 결과.
     *
     * @param objectKey    저장소 내부 키. DB에는 이 값만 보관한다.
     * @param originalName 업로드 당시 파일명(표시용)
     * @param contentType  검증을 통과한 MIME 타입
     * @param size         바이트 크기
     * @param sha256       파일 내용 해시
     */
    record StoredFile(String objectKey, String originalName, String contentType, long size, String sha256) {
    }

    /**
     * 파일을 저장한다.
     *
     * @param userId 소유자 ID. 키 네임스페이스로 사용한다.
     * @param file   업로드 파일
     * @return 저장 결과
     */
    StoredFile upload(Long userId, MultipartFile file);

    /**
     * 파일을 삭제한다.
     *
     * @param objectKey 저장소 키
     */
    void delete(String objectKey);
}
