package com.henfon.shop.content.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品评价审核服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class ContentReviewAdminServiceTest {

    @Mock
    private ContentReviewMapper reviewMapper;

    @Mock
    private ContentNotificationService notificationService;

    /**
     * 审核状态变化后应向会员发送审核结果通知。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldNotifyMemberWhenReviewApproved() {
        ContentReview review = review(10L, 20L, 0);
        when(reviewMapper.selectById(10L)).thenReturn(review);
        when(reviewMapper.updateById(any(ContentReview.class))).thenReturn(1);
        ContentReviewAdminService service = new ContentReviewAdminService(reviewMapper, notificationService);

        service.updateStatus(10L, 1);

        ArgumentCaptor<com.henfon.shop.content.dto.NotificationEventRequest> captor =
                ArgumentCaptor.forClass(com.henfon.shop.content.dto.NotificationEventRequest.class);
        verify(notificationService).saveEvent(captor.capture());
        assertEquals("REVIEW_APPROVED", captor.getValue().eventType());
        assertEquals("REVIEW_APPROVED:10", captor.getValue().dedupeKey());
    }

    /**
     * 重复设置相同状态时不重复发送通知。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldNotNotifyWhenStatusUnchanged() {
        ContentReview review = review(11L, 21L, 1);
        when(reviewMapper.selectById(11L)).thenReturn(review);
        when(reviewMapper.updateById(any(ContentReview.class))).thenReturn(1);
        ContentReviewAdminService service = new ContentReviewAdminService(reviewMapper, notificationService);

        service.updateStatus(11L, 1);

        verify(notificationService, never()).saveEvent(any());
    }

    /**
     * 非法审核状态应直接拒绝且不访问数据库。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectInvalidStatus() {
        ContentReviewAdminService service = new ContentReviewAdminService(reviewMapper, notificationService);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, 2));

        assertEquals("CONTENT_REVIEW_STATUS_INVALID", exception.getCode());
        verify(reviewMapper, never()).selectById(any());
    }

    /**
     * 构造评价测试实体。
     *
     * @param id 评价ID
     * @param memberId 会员ID
     * @param status 评价状态
     * @return 评价实体
     * @author Henfon
     * @date 2026-09-04
     */
    private ContentReview review(Long id, Long memberId, Integer status) {
        ContentReview review = new ContentReview();
        review.setId(id);
        review.setMemberId(memberId);
        review.setStatus(status);
        return review;
    }
}
