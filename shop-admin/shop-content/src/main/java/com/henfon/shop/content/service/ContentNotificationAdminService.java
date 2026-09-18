package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentNotificationAdminItem;
import com.henfon.shop.content.dto.ContentNotificationAdminSummary;
import com.henfon.shop.content.entity.ContentNotification;
import com.henfon.shop.content.mapper.ContentNotificationMapper;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 后台通知中心应用服务，按运营视角浏览会员站内通知的投递记录。
 *
 * <p>通知本体由业务事件监听器写入，这里只读浏览，不做发送。运营的已读状态落在
 * {@code admin_read_status}，与会员本人的 {@code read_status} 相互独立：管理员查看或
 * 标记已读不会影响会员端的未读提示。</p>
 *
 * @author Henfon
 * @date 2026-09-18
 */
@Service
public class ContentNotificationAdminService {

    /** 运营未读。 */
    private static final int UNREAD = 0;
    /** 运营已读。 */
    private static final int READ = 1;
    /** 后台列表单页上限，避免一次拉取过多投递记录。 */
    private static final long MAX_PAGE_SIZE = 200;

    private final ContentNotificationMapper notificationMapper;
    private final MemberUserMapper memberUserMapper;

    /**
     * 创建后台通知中心应用服务。
     *
     * @param notificationMapper 通知数据访问对象
     * @param memberUserMapper 会员数据访问对象，用于回填收件人信息
     * @author Henfon
     * @date 2026-09-18
     */
    public ContentNotificationAdminService(ContentNotificationMapper notificationMapper,
                                           MemberUserMapper memberUserMapper) {
        this.notificationMapper = notificationMapper;
        this.memberUserMapper = memberUserMapper;
    }

    /**
     * 分页查询平台发出的会员通知。
     *
     * @param eventType 事件类型，可选
     * @param adminReadStatus 运营已读状态，可选
     * @param memberReadStatus 会员已读状态，可选
     * @param keyword 标题、内容或业务单号关键字，可选
     * @param memberId 收件会员ID，可选
     * @param current 当前页
     * @param size 页大小
     * @return 通知分页数据
     * @author Henfon
     * @date 2026-09-18
     */
    public IPage<ContentNotificationAdminItem> page(String eventType, Integer adminReadStatus, Integer memberReadStatus,
                                                    String keyword, Long memberId, long current, long size) {
        validateReadStatus(adminReadStatus, "运营已读状态");
        validateReadStatus(memberReadStatus, "会员已读状态");
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        // 条件参数在 eq 调用时就会求值，这里先归一化，避免 eventType 为空时进 normalizeEventType。
        String normalizedEventType = StringUtils.hasText(eventType) ? normalizeEventType(eventType) : null;
        LambdaQueryWrapper<ContentNotification> wrapper = new LambdaQueryWrapper<ContentNotification>()
                .eq(normalizedEventType != null, ContentNotification::getEventType, normalizedEventType)
                .eq(adminReadStatus != null, ContentNotification::getAdminReadStatus, adminReadStatus)
                .eq(memberReadStatus != null, ContentNotification::getReadStatus, memberReadStatus)
                .eq(memberId != null, ContentNotification::getMemberId, memberId)
                .and(trimmedKeyword != null, query -> query
                        .like(ContentNotification::getTitle, trimmedKeyword)
                        .or().like(ContentNotification::getContent, trimmedKeyword)
                        .or().like(ContentNotification::getBusinessId, trimmedKeyword))
                .orderByDesc(ContentNotification::getCreatedAt)
                .orderByDesc(ContentNotification::getId);
        IPage<ContentNotification> raw = notificationMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        Page<ContentNotificationAdminItem> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(toItems(raw.getRecords()));
        return result;
    }

    /**
     * 统计通知总数、运营未读数、会员未读数与事件类型分布。
     *
     * @return 通知统计概览
     * @author Henfon
     * @date 2026-09-18
     */
    public ContentNotificationAdminSummary summary() {
        long total = count(new LambdaQueryWrapper<>());
        long adminUnread = count(new LambdaQueryWrapper<ContentNotification>()
                .eq(ContentNotification::getAdminReadStatus, UNREAD));
        long memberUnread = count(new LambdaQueryWrapper<ContentNotification>()
                .eq(ContentNotification::getReadStatus, UNREAD));
        return new ContentNotificationAdminSummary(total, adminUnread, memberUnread, eventTypeCounts());
    }

    /**
     * 将一条通知标记为运营已读。
     *
     * @param id 通知ID
     * @author Henfon
     * @date 2026-09-18
     */
    @Transactional
    public void markRead(Long id) {
        if (id == null) {
            throw new BusinessException("CONTENT_NOTIFICATION_ARGUMENT_INVALID", "通知ID不能为空");
        }
        ContentNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException("CONTENT_NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        if (Integer.valueOf(READ).equals(notification.getAdminReadStatus())) {
            return;
        }
        notification.setAdminReadStatus(READ);
        notification.setAdminReadAt(LocalDateTime.now());
        if (notificationMapper.updateById(notification) == 0) {
            throw new BusinessException("CONTENT_NOTIFICATION_CONCURRENT_UPDATE", "通知已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 将全部运营未读通知标记为已读。
     *
     * @return 本次更新条数
     * @author Henfon
     * @date 2026-09-18
     */
    @Transactional
    public int markAllRead() {
        ContentNotification update = new ContentNotification();
        update.setAdminReadStatus(READ);
        update.setAdminReadAt(LocalDateTime.now());
        return notificationMapper.update(update, new LambdaQueryWrapper<ContentNotification>()
                .eq(ContentNotification::getAdminReadStatus, UNREAD));
    }

    /**
     * 将通知实体转换为后台列表项，并批量回填收件会员信息。
     *
     * @param records 通知实体列表
     * @return 后台列表项
     * @author Henfon
     * @date 2026-09-18
     */
    private List<ContentNotificationAdminItem> toItems(List<ContentNotification> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> memberIds = records.stream()
                .map(ContentNotification::getMemberId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MemberUser> memberById = new HashMap<>();
        if (!memberIds.isEmpty()) {
            List<MemberUser> members = memberUserMapper.selectBatchIds(memberIds);
            if (members != null) {
                members.stream()
                        .filter(Objects::nonNull)
                        .forEach(member -> memberById.put(member.getId(), member));
            }
        }
        List<ContentNotificationAdminItem> items = new ArrayList<>(records.size());
        for (ContentNotification record : records) {
            MemberUser member = record.getMemberId() == null ? null : memberById.get(record.getMemberId());
            items.add(new ContentNotificationAdminItem(
                    record.getId(),
                    record.getMemberId(),
                    memberDisplayName(member, record.getMemberId()),
                    memberAccount(member),
                    record.getOrderId(),
                    record.getBusinessId(),
                    record.getEventType(),
                    record.getTitle(),
                    record.getContent(),
                    record.getReadStatus(),
                    record.getReadAt(),
                    record.getAdminReadStatus(),
                    record.getAdminReadAt(),
                    record.getCreatedAt()));
        }
        return items;
    }

    /**
     * 按事件类型分组统计通知条数。
     *
     * @return 事件类型计数，按条数倒序
     * @author Henfon
     * @date 2026-09-18
     */
    private List<ContentNotificationAdminSummary.EventTypeCount> eventTypeCounts() {
        List<Map<String, Object>> rows = notificationMapper.selectMaps(new QueryWrapper<ContentNotification>()
                .select("event_type AS eventType", "COUNT(*) AS typeCount")
                .groupBy("event_type")
                .orderByDesc("typeCount"));
        List<ContentNotificationAdminSummary.EventTypeCount> counts = new ArrayList<>();
        if (rows == null) {
            return counts;
        }
        for (Map<String, Object> row : rows) {
            Object eventType = row.get("eventType");
            Object typeCount = row.get("typeCount");
            if (eventType == null) {
                continue;
            }
            counts.add(new ContentNotificationAdminSummary.EventTypeCount(
                    String.valueOf(eventType),
                    typeCount instanceof Number number ? number.longValue() : 0L));
        }
        return counts;
    }

    /**
     * 统计满足条件的通知条数。
     *
     * @param wrapper 查询条件
     * @return 通知条数
     * @author Henfon
     * @date 2026-09-18
     */
    private long count(LambdaQueryWrapper<ContentNotification> wrapper) {
        Long value = notificationMapper.selectCount(wrapper);
        return value == null ? 0L : value;
    }

    /**
     * 校验已读状态取值范围。
     *
     * @param status 已读状态
     * @param label 字段名称，用于错误提示
     * @author Henfon
     * @date 2026-09-18
     */
    private void validateReadStatus(Integer status, String label) {
        if (status != null && status != UNREAD && status != READ) {
            throw new BusinessException("CONTENT_NOTIFICATION_STATUS_INVALID", label + "只能为0（未读）或1（已读）");
        }
    }

    /**
     * 归一化事件类型，库中统一存大写。
     *
     * @param eventType 原始事件类型
     * @return 大写事件类型
     * @author Henfon
     * @date 2026-09-18
     */
    private String normalizeEventType(String eventType) {
        return eventType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 取收件会员显示名，会员记录被删除时退回ID占位。
     *
     * @param member 会员
     * @param memberId 会员ID
     * @return 显示名
     * @author Henfon
     * @date 2026-09-18
     */
    private String memberDisplayName(MemberUser member, Long memberId) {
        if (member == null) {
            return memberId == null ? "未知会员" : "会员" + memberId;
        }
        if (StringUtils.hasText(member.getNickname())) {
            return member.getNickname().trim();
        }
        if (StringUtils.hasText(member.getUsername())) {
            return member.getUsername().trim();
        }
        return "会员" + member.getId();
    }

    /**
     * 取收件会员账号，优先邮箱。
     *
     * @param member 会员
     * @return 账号
     * @author Henfon
     * @date 2026-09-18
     */
    private String memberAccount(MemberUser member) {
        if (member == null) {
            return null;
        }
        if (StringUtils.hasText(member.getEmail())) {
            return member.getEmail().trim();
        }
        if (StringUtils.hasText(member.getUsername())) {
            return member.getUsername().trim();
        }
        if (StringUtils.hasText(member.getPhone())) {
            return member.getPhone().trim();
        }
        return member.getMemberNo();
    }
}
