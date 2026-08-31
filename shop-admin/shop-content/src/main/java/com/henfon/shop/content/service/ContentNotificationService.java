package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.NotificationEventRequest;
import com.henfon.shop.content.entity.ContentNotification;
import com.henfon.shop.content.mapper.ContentNotificationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员站内通知应用服务。
 *
 * <p>该服务提供支付、发货、退款和售后等业务事件的统一落库入口，后续可由领域事件消费者直接调用。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class ContentNotificationService {

    private static final int UNREAD = 0;
    private static final int READ = 1;

    private final ContentNotificationMapper notificationMapper;

    /**
     * 创建站内通知应用服务。
     *
     * @param notificationMapper 通知数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotificationService(ContentNotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 保存业务事件通知并按幂等键去重。
     *
     * @param request 业务通知请求
     * @return 已存在或新创建的通知
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public ContentNotification saveEvent(NotificationEventRequest request) {
        if (request.memberId() == null || !StringUtils.hasText(request.eventType())
                || !StringUtils.hasText(request.title()) || !StringUtils.hasText(request.content())
                || !StringUtils.hasText(request.dedupeKey())) {
            throw new BusinessException("CONTENT_NOTIFICATION_ARGUMENT_INVALID", "通知必要信息不能为空");
        }
        String dedupeKey = request.dedupeKey().trim();
        ContentNotification existing = findByDedupeKey(request.memberId(), dedupeKey);
        if (existing != null) {
            return existing;
        }
        ContentNotification notification = new ContentNotification();
        notification.setMemberId(request.memberId());
        notification.setOrderId(request.orderId());
        notification.setBusinessId(trimToNull(request.businessId()));
        notification.setEventType(request.eventType().trim().toUpperCase());
        notification.setTitle(request.title().trim());
        notification.setContent(request.content().trim());
        notification.setDedupeKey(dedupeKey);
        notification.setReadStatus(UNREAD);
        try {
            notificationMapper.insert(notification);
            return notification;
        } catch (DuplicateKeyException exception) {
            // 并发事件由唯一索引仲裁，冲突请求读取已落库的通知即可。
            ContentNotification concurrent = findByDedupeKey(request.memberId(), dedupeKey);
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
    }

    /**
     * 记录支付成功通知。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param paidAmount 实付金额
     * @return 已落库通知
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotification notifyPaymentSucceeded(Long memberId, Long orderId, String orderNo,
                                                       BigDecimal paidAmount) {
        return saveEvent(new NotificationEventRequest(memberId, orderId, orderNo, "PAYMENT_SUCCEEDED",
                "支付成功", "订单 " + safe(orderNo) + " 已支付成功，实付金额 ¥" + money(paidAmount),
                "PAYMENT_SUCCEEDED:" + orderId));
    }

    /**
     * 记录订单发货通知。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param logisticsCompany 物流公司
     * @param trackingNo 运单号
     * @return 已落库通知
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotification notifyShipped(Long memberId, Long orderId, String orderNo,
                                              String logisticsCompany, String trackingNo) {
        return saveEvent(new NotificationEventRequest(memberId, orderId, orderNo, "ORDER_SHIPPED",
                "订单已发货", "订单 " + safe(orderNo) + " 已由 " + safe(logisticsCompany)
                        + " 发出，运单号：" + safe(trackingNo), "ORDER_SHIPPED:" + orderId));
    }

    /**
     * 记录退款成功通知。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param refundAmount 退款金额
     * @return 已落库通知
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotification notifyRefunded(Long memberId, Long orderId, String orderNo,
                                               BigDecimal refundAmount) {
        return saveEvent(new NotificationEventRequest(memberId, orderId, orderNo, "REFUND_SUCCEEDED",
                "退款成功", "订单 " + safe(orderNo) + " 退款已完成，退款金额 ¥" + money(refundAmount),
                "REFUND_SUCCEEDED:" + orderId));
    }

    /**
     * 记录售后状态通知。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param afterSaleNo 售后单号
     * @param status 售后状态
     * @param description 状态描述
     * @return 已落库通知
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotification notifyAfterSale(Long memberId, Long orderId, String afterSaleNo,
                                                String status, String description) {
        String safeStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "UPDATED";
        return saveEvent(new NotificationEventRequest(memberId, orderId, afterSaleNo, "AFTER_SALE_" + safeStatus,
                "售后进度更新", "售后单 " + safe(afterSaleNo) + "：" + safe(description),
                "AFTER_SALE:" + afterSaleNo + ":" + safeStatus));
    }

    /**
     * 分页查询会员通知。
     *
     * @param memberId 会员ID
     * @param readStatus 已读状态，0未读、1已读，可为空
     * @param current 当前页
     * @param size 页大小
     * @return 通知分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<ContentNotification> pageForMember(Long memberId, Integer readStatus, long current, long size) {
        if (memberId == null) {
            throw new BusinessException("CONTENT_NOTIFICATION_MEMBER_REQUIRED", "会员ID不能为空");
        }
        if (readStatus != null && readStatus != UNREAD && readStatus != READ) {
            throw new BusinessException("CONTENT_NOTIFICATION_STATUS_INVALID", "通知已读状态必须为0或1");
        }
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        return notificationMapper.selectPage(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<ContentNotification>()
                .eq(ContentNotification::getMemberId, memberId)
                .eq(readStatus != null, ContentNotification::getReadStatus, readStatus)
                .orderByDesc(ContentNotification::getCreatedAt)
                .orderByDesc(ContentNotification::getId));
    }

    /**
     * 将会员指定通知标记为已读。
     *
     * @param memberId 会员ID
     * @param notificationId 通知ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        ContentNotification notification = notificationMapper.selectOne(new LambdaQueryWrapper<ContentNotification>()
                .eq(ContentNotification::getId, notificationId)
                .eq(ContentNotification::getMemberId, memberId)
                .last("LIMIT 1"));
        if (notification == null) {
            throw new BusinessException("CONTENT_NOTIFICATION_NOT_FOUND", "通知不存在或无权操作");
        }
        if (Integer.valueOf(READ).equals(notification.getReadStatus())) {
            return;
        }
        notification.setReadStatus(READ);
        notification.setReadAt(LocalDateTime.now());
        if (notificationMapper.updateById(notification) == 0) {
            throw new BusinessException("CONTENT_NOTIFICATION_CONCURRENT_UPDATE", "通知已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 将会员全部未读通知标记为已读。
     *
     * @param memberId 会员ID
     * @return 本次更新数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public int markAllRead(Long memberId) {
        if (memberId == null) {
            throw new BusinessException("CONTENT_NOTIFICATION_MEMBER_REQUIRED", "会员ID不能为空");
        }
        ContentNotification update = new ContentNotification();
        update.setReadStatus(READ);
        update.setReadAt(LocalDateTime.now());
        return notificationMapper.update(update,
                new LambdaQueryWrapper<ContentNotification>()
                        .eq(ContentNotification::getMemberId, memberId)
                        .eq(ContentNotification::getReadStatus, UNREAD));
    }

    /**
     * 按会员和幂等键查询通知。
     *
     * @param memberId 会员ID
     * @param dedupeKey 幂等键
     * @return 已有通知
     * @author Henfon
     * @date 2026-08-31
     */
    private ContentNotification findByDedupeKey(Long memberId, String dedupeKey) {
        return notificationMapper.selectOne(new LambdaQueryWrapper<ContentNotification>()
                .eq(ContentNotification::getMemberId, memberId)
                .eq(ContentNotification::getDedupeKey, dedupeKey)
                .last("LIMIT 1"));
    }

    /**
     * 将可选文本清理为空值。
     *
     * @param value 原始文本
     * @return 清理后的文本
     * @author Henfon
     * @date 2026-08-31
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 将空文本转为通知中的默认占位内容。
     *
     * @param value 原始文本
     * @return 安全文本
     * @author Henfon
     * @date 2026-08-31
     */
    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未知";
    }

    /**
     * 格式化金额展示文本。
     *
     * @param amount 金额
     * @return 两位小数金额
     * @author Henfon
     * @date 2026-08-31
     */
    private String money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
