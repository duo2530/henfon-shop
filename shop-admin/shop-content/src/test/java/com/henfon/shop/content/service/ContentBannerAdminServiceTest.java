package com.henfon.shop.content.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentBannerSaveRequest;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Banner 后台管理服务测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class ContentBannerAdminServiceTest {

    @Mock
    private ContentBannerMapper bannerMapper;

    /**
     * 校验定时同步会启用已到开始时间的 Banner，并停用已过结束时间的 Banner。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldSyncBannerStatusByPublishWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        ContentBanner notStarted = banner(1L, 0,
                now.plusMinutes(10), null);
        ContentBanner expired = banner(2L, 1,
                null, now.minusMinutes(10));
        ContentBanner active = banner(3L, 1,
                now.minusMinutes(10), now.plusMinutes(10));
        when(bannerMapper.selectList(any())).thenReturn(List.of(notStarted, expired, active));
        when(bannerMapper.updateById(any(ContentBanner.class))).thenReturn(1);

        new ContentBannerAdminService(bannerMapper).syncScheduledStatusAt(now);

        assertEquals(0, notStarted.getStatus());
        assertEquals(0, expired.getStatus());
        assertEquals(1, active.getStatus());
        verify(bannerMapper, times(1)).updateById(any(ContentBanner.class));
    }

    /**
     * 校验发布时间边界包含开始和结束时刻，避免边界瞬间出现展示抖动。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldIncludePublishWindowBoundaries() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        ContentBanner banner = banner(4L, 0, now, now);
        when(bannerMapper.selectList(any())).thenReturn(List.of(banner));
        when(bannerMapper.updateById(any(ContentBanner.class))).thenReturn(1);

        new ContentBannerAdminService(bannerMapper).syncScheduledStatusAt(now);

        assertEquals(1, banner.getStatus());
        verify(bannerMapper).updateById(banner);
    }

    /**
     * 校验开始时间晚于结束时间时拒绝保存，避免形成永远不可发布的内容。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectInvalidPublishWindow() {
        ContentBannerSaveRequest request = new ContentBannerSaveRequest(
                null, "活动", null, null, "https://cdn.example/banner.png",
                "NONE", null, 0, 1,
                LocalDateTime.of(2026, 9, 5, 10, 0),
                LocalDateTime.of(2026, 9, 5, 9, 0), null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new ContentBannerAdminService(bannerMapper).save(request));

        assertEquals("CONTENT_BANNER_TIME_INVALID", exception.getCode());
    }

    /**
     * 创建用于测试时间窗口状态同步的 Banner 实体。
     *
     * @param id Banner ID
     * @param status 当前状态
     * @param startAt 开始时间
     * @param endAt 结束时间
     * @return Banner 实体
     * @author Henfon
     * @date 2026-09-04
     */
    private ContentBanner banner(Long id, int status, LocalDateTime startAt, LocalDateTime endAt) {
        ContentBanner banner = new ContentBanner();
        banner.setId(id);
        banner.setStatus(status);
        banner.setStartAt(startAt);
        banner.setEndAt(endAt);
        return banner;
    }
}
