package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentNotificationAdminItem;
import com.henfon.shop.content.dto.ContentNotificationAdminSummary;
import com.henfon.shop.content.entity.ContentNotification;
import com.henfon.shop.content.mapper.ContentNotificationMapper;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台通知中心服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
class ContentNotificationAdminServiceTest {

    @Mock
    private ContentNotificationMapper notificationMapper;

    @Mock
    private MemberUserMapper memberUserMapper;

    /**
     * 创建被测服务。
     *
     * @return 通知中心服务
     * @author Henfon
     * @date 2026-09-18
     */
    private ContentNotificationAdminService newService() {
        return new ContentNotificationAdminService(notificationMapper, memberUserMapper);
    }

    /**
     * 列表项应带回收件会员的昵称与账号，运营才知道通知发给了谁。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldFillMemberInfoWhenPaging() {
        Page<ContentNotification> page = new Page<>(1, 20);
        page.setRecords(List.of(notification(1L, 9L, 0, 0)));
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);
        MemberUser member = new MemberUser();
        member.setId(9L);
        member.setNickname("青木");
        member.setEmail("member9@example.com");
        when(memberUserMapper.selectBatchIds(List.of(9L))).thenReturn(List.of(member));

        IPage<ContentNotificationAdminItem> result = newService().page(null, null, null, null, null, 1, 20);

        ContentNotificationAdminItem item = result.getRecords().get(0);
        assertEquals("青木", item.memberName());
        assertEquals("member9@example.com", item.memberAccount());
    }

    /**
     * 会员记录已被删除时用ID占位，避免列表出现空白收件人。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldFallbackToMemberIdWhenMemberMissing() {
        Page<ContentNotification> page = new Page<>(1, 20);
        page.setRecords(List.of(notification(2L, 77L, 0, 0)));
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);
        when(memberUserMapper.selectBatchIds(List.of(77L))).thenReturn(List.of());

        IPage<ContentNotificationAdminItem> result = newService().page(null, null, null, null, null, 1, 20);

        ContentNotificationAdminItem item = result.getRecords().get(0);
        assertEquals("会员77", item.memberName());
        assertNull(item.memberAccount());
    }

    /**
     * 页大小超过上限时收敛到 200，避免一次拉取过多投递记录。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldCapPageSize() {
        Page<ContentNotification> page = new Page<>(1, 200);
        page.setRecords(List.of());
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);

        newService().page(null, null, null, null, null, 1, 9999);

        ArgumentCaptor<IPage<ContentNotification>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(notificationMapper).selectPage(captor.capture(), any());
        assertEquals(200L, captor.getValue().getSize());
    }

    /**
     * 统计概览分别给出总数、运营未读与会员未读。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldSummarizeCountsAndEventTypes() {
        when(notificationMapper.selectCount(any())).thenReturn(7L, 7L, 5L);
        when(notificationMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("eventType", "PAYMENT_SUCCEEDED", "typeCount", 4L),
                Map.of("eventType", "ORDER_SHIPPED", "typeCount", 1L)));

        ContentNotificationAdminSummary summary = newService().summary();

        assertEquals(7L, summary.total());
        assertEquals(7L, summary.adminUnread());
        assertEquals(5L, summary.memberUnread());
        assertEquals(2, summary.eventTypes().size());
        assertEquals("PAYMENT_SUCCEEDED", summary.eventTypes().get(0).eventType());
        assertEquals(4L, summary.eventTypes().get(0).count());
    }

    /**
     * 标记单条已读只改运营已读列，不触碰会员的阅读状态。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldMarkNotificationReadWithoutTouchingMemberStatus() {
        ContentNotification notification = notification(5L, 3L, 0, 0);
        when(notificationMapper.selectById(5L)).thenReturn(notification);
        when(notificationMapper.updateById(any(ContentNotification.class))).thenReturn(1);

        newService().markRead(5L);

        ArgumentCaptor<ContentNotification> captor = ArgumentCaptor.forClass(ContentNotification.class);
        verify(notificationMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getAdminReadStatus());
        assertNotNull(captor.getValue().getAdminReadAt());
        assertEquals(0, captor.getValue().getReadStatus());
    }

    /**
     * 已读的通知重复标记不再写库。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldSkipUpdateWhenAlreadyRead() {
        when(notificationMapper.selectById(6L)).thenReturn(notification(6L, 3L, 0, 1));

        newService().markRead(6L);

        verify(notificationMapper, never()).updateById(any(ContentNotification.class));
    }

    /**
     * 通知不存在时抛业务异常，避免静默成功。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldRejectMissingNotification() {
        when(notificationMapper.selectById(99L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> newService().markRead(99L));

        assertEquals("CONTENT_NOTIFICATION_NOT_FOUND", exception.getCode());
    }

    /**
     * 已读状态只接受 0 或 1。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldRejectInvalidReadStatusWhenPaging() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> newService().page(null, 3, null, null, null, 1, 20));

        assertEquals("CONTENT_NOTIFICATION_STATUS_INVALID", exception.getCode());
        verify(notificationMapper, never()).selectPage(any(), any());
    }

    /**
     * 全部已读返回实际更新条数。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldMarkAllUnreadRead() {
        when(notificationMapper.update(any(ContentNotification.class), any())).thenReturn(4);

        assertEquals(4, newService().markAllRead());
    }

    /**
     * 构造通知测试实体。
     *
     * @param id 通知ID
     * @param memberId 会员ID
     * @param readStatus 会员已读状态
     * @param adminReadStatus 运营已读状态
     * @return 通知实体
     * @author Henfon
     * @date 2026-09-18
     */
    private ContentNotification notification(Long id, Long memberId, Integer readStatus, Integer adminReadStatus) {
        ContentNotification notification = new ContentNotification();
        notification.setId(id);
        notification.setMemberId(memberId);
        notification.setReadStatus(readStatus);
        notification.setAdminReadStatus(adminReadStatus);
        return notification;
    }
}
