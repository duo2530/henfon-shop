package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.entity.TradeEventOutboxRetryAudit;
import com.henfon.shop.trade.mapper.TradeEventOutboxMapper;
import com.henfon.shop.trade.mapper.TradeEventOutboxRetryAuditMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Outbox 死信查询与人工补偿服务单元测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class TradeEventOutboxCompensationServiceTest {

    @Mock
    private TradeEventOutboxMapper outboxMapper;

    @Mock
    private TradeEventOutboxRetryAuditMapper retryAuditMapper;

    /**
     * 校验死信分页会限制页码大小并委托 Mapper 查询。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldPageDeadEventsWithSafePageSize() {
        Page<TradeEventOutbox> expected = new Page<>(1, 200);
        when(outboxMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(expected);
        TradeEventOutboxCompensationService service = new TradeEventOutboxCompensationService(outboxMapper);

        IPage<TradeEventOutbox> actual = service.pageDeadEvents("PAYMENT_SUCCEEDED", "SHOP_PAYMENT_SUCCEEDED",
                "order-1", 0, 999);

        assertEquals(expected, actual);
        ArgumentCaptor<Page<TradeEventOutbox>> captor = ArgumentCaptor.forClass(Page.class);
        verify(outboxMapper).selectPage(captor.capture(), any(Wrapper.class));
        assertEquals(1, captor.getValue().getCurrent());
        assertEquals(200, captor.getValue().getSize());
    }

    /**
     * 校验死信人工重试会重置状态并立即进入待发送队列。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldResetDeadEventForRetry() {
        TradeEventOutbox event = deadEvent();
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        when(outboxMapper.updateById(event)).thenReturn(1);
        TradeEventOutboxCompensationService service = new TradeEventOutboxCompensationService(outboxMapper);

        TradeEventOutbox actual = service.retryDeadEvent("evt-1");

        assertEquals(event, actual);
        assertEquals(0, actual.getStatus());
        assertEquals(0, actual.getRetryCount());
        assertNotNull(actual.getNextRetryAt());
        org.junit.jupiter.api.Assertions.assertNull(actual.getPublishedAt());
        assertEquals("previous failure", actual.getLastError());
        verify(outboxMapper).updateById(event);
    }

    /**
     * 校验人工重试会记录操作人、时间和成功结果，形成可追溯审计信息。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRecordManualRetryAudit() {
        TradeEventOutbox event = deadEvent();
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        when(outboxMapper.updateById(event)).thenReturn(1);

        TradeEventOutboxCompensationService service = new TradeEventOutboxCompensationService(outboxMapper, retryAuditMapper);
        TradeEventOutbox actual = service.retryDeadEvent("evt-1", " admin ");

        assertEquals("admin", actual.getManualRetryBy());
        assertNotNull(actual.getManualRetryAt());
        assertEquals("SUCCESS", actual.getManualRetryResult());
        verify(retryAuditMapper).insert(any(TradeEventOutboxRetryAudit.class));
    }

    /**
     * 校验非死信事件不能被人工重试，避免重复投递已成功消息。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectRetryForNonDeadEvent() {
        TradeEventOutbox event = deadEvent();
        event.setStatus(1);
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        TradeEventOutboxCompensationService service = new TradeEventOutboxCompensationService(outboxMapper);

        assertThrows(BusinessException.class, () -> service.retryDeadEvent("evt-1"));
        verify(outboxMapper).selectOne(any(Wrapper.class));
        verifyNoMoreInteractions(outboxMapper);
    }

    /**
     * 校验乐观锁更新失败会返回并发冲突，避免误报补偿成功。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectConcurrentRetryUpdate() {
        TradeEventOutbox event = deadEvent();
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        when(outboxMapper.updateById(event)).thenReturn(0);
        TradeEventOutboxCompensationService service = new TradeEventOutboxCompensationService(outboxMapper);

        assertThrows(BusinessException.class, () -> service.retryDeadEvent("evt-1"));
    }

    /**
     * 校验人工重试会清洗事件标识首尾空格，避免运营复制参数时误判事件不存在。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldTrimEventIdBeforeRetryQuery() {
        TradeEventOutbox event = deadEvent();
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        when(outboxMapper.updateById(event)).thenReturn(1);
        TradeEventOutboxCompensationService service = new TradeEventOutboxCompensationService(outboxMapper);

        service.retryDeadEvent("  evt-1  ");

        verify(outboxMapper).selectOne(any(Wrapper.class));
    }

    /**
     * 构造测试使用的死信事件。
     *
     * @return 已达到重试上限的 Outbox 事件
     * @author Henfon
     * @date 2026-08-31
     */
    private TradeEventOutbox deadEvent() {
        TradeEventOutbox event = new TradeEventOutbox();
        event.setId(1L);
        event.setEventId("evt-1");
        event.setStatus(2);
        event.setRetryCount(10);
        event.setVersion(0);
        event.setLastError("previous failure");
        event.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        return event;
    }
}
