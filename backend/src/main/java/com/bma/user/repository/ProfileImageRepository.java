package com.bma.user.repository;

import com.bma.user.entity.ProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link ProfileImage} 저장소.
 */
public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

    /**
     * 사용자의 살아 있는 이미지를 표시 순서대로 조회한다.
     *
     * @param userId  사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 이미지 목록
     */
    List<ProfileImage> findByUserIdAndDeletedOrderByDisplayOrderAsc(Long userId, String deleted);

    /**
     * 여러 사용자의 이미지를 한 번에 조회한다.
     *
     * <p>추천 목록처럼 다건을 처리할 때 사용자마다 조회하면 N+1이 되므로 IN 조건으로 가져온다.</p>
     *
     * @param userIds 사용자 ID 목록
     * @param deleted 논리 삭제 여부
     * @return 이미지 목록
     */
    List<ProfileImage> findByUserIdInAndDeletedOrderByDisplayOrderAsc(List<Long> userIds, String deleted);

    /**
     * 사용자가 보유한 이미지 수를 센다. 업로드 한도 검사에 사용한다.
     *
     * @param userId  사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 이미지 수
     */
    long countByUserIdAndDeleted(Long userId, String deleted);

    /**
     * 소유자까지 함께 지정해 이미지를 조회한다.
     *
     * <p>ID만으로 조회한 뒤 소유자를 비교하면 "존재 여부"가 노출될 수 있으므로
     * 조회 조건에 소유자를 포함한다.</p>
     *
     * @param id      이미지 ID
     * @param userId  소유자 ID
     * @param deleted 논리 삭제 여부
     * @return 이미지
     */
    Optional<ProfileImage> findByIdAndUserIdAndDeleted(Long id, Long userId, String deleted);

    /**
     * 사용자의 대표 이미지를 조회한다.
     *
     * @param userId    사용자 ID
     * @param primaryYn 대표 여부
     * @param deleted   논리 삭제 여부
     * @return 대표 이미지
     */
    Optional<ProfileImage> findFirstByUserIdAndPrimaryYnAndDeleted(Long userId, String primaryYn, String deleted);
}
