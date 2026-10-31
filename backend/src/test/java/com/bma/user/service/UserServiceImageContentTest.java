package com.bma.user.service;

import com.bma.common.entity.YesNo;
import com.bma.common.service.RegionService;
import com.bma.storage.StorageService;
import com.bma.user.dto.UserDtos.ProfileImageContent;
import com.bma.user.entity.ProfileImage;
import com.bma.user.repository.ProfileImageRepository;
import com.bma.user.repository.UserPreferenceRepository;
import com.bma.user.repository.UserProfileRepository;
import com.bma.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사이드바·마이페이지 사진(BMA-93): 본인 사진의 바이트와 MIME 을 그대로 돌려주고, 미등록은 빈 값이다.
 */
class UserServiceImageContentTest {

    private ProfileImageRepository imageRepository;
    private StorageService storageService;
    private UserService service;

    @BeforeEach
    void setUp() {
        imageRepository = mock(ProfileImageRepository.class);
        storageService = mock(StorageService.class);
        service = new UserService(mock(UserRepository.class), mock(UserProfileRepository.class),
                mock(UserPreferenceRepository.class), imageRepository, mock(RegionService.class), storageService);
    }

    @Test
    @DisplayName("등록된 사진은 저장소에서 읽은 바이트와 업로드 때 검증된 MIME 을 돌려준다")
    void returnsStoredBytes() {
        ProfileImage image = new ProfileImage();
        image.setUserId(7L);
        image.setOriginalObjectKey("profile/7/a.png");
        image.setContentType("image/png");
        when(imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(7L, YesNo.N)).thenReturn(List.of(image));
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G'};
        when(storageService.read("profile/7/a.png")).thenReturn(bytes);

        Optional<ProfileImageContent> content = service.getImageContent(7L);

        assertThat(content).isPresent();
        assertThat(content.get().bytes()).isEqualTo(bytes);
        assertThat(content.get().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("사진이 없으면 빈 값이고 저장소는 건드리지 않는다")
    void emptyWhenNoImage() {
        when(imageRepository.findByUserIdAndDeletedOrderByDisplayOrderAsc(7L, YesNo.N)).thenReturn(List.of());

        assertThat(service.getImageContent(7L)).isEmpty();
        verify(storageService, never()).read(anyString());
    }
}
