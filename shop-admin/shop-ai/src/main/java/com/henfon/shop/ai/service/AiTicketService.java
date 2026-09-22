package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.ai.dto.AiTicketHandleRequest;
import com.henfon.shop.ai.dto.AiTicketSubmitRequest;
import com.henfon.shop.ai.entity.AiTicket;
import com.henfon.shop.ai.mapper.AiTicketMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 转人工工单。
 *
 * 工单是知识库覆盖不到的问题的出口，也是后续反推知识缺口的原料，所以落单时尽量少改写
 * 买家原话：contact 与 question 都是原文照存，运营看到的就是买家打出来的那句话。
 *
 * ai_summary 这一列在本批里留空。它需要一次额外的模型调用，而提交工单往往发生在对话
 * 链路已经出问题的时候（模型不可用、知识库不可达），这时候再依赖模型生成摘要，等于让
 * 唯一的兜底通道也被同一条链路卡住。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiTicketService {

    /** 待处理。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 处理中。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 已关闭。 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 允许写入的状态集合。 */
    private static final Set<String> ALLOWED_STATUS = Set.of(STATUS_PENDING, STATUS_PROCESSING, STATUS_CLOSED);

    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AiTicketMapper ticketMapper;

    private final AiConversationService conversationService;

    /**
     * 创建工单服务。
     *
     * @param ticketMapper 工单 Mapper
     * @param conversationService 会话管理服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiTicketService(AiTicketMapper ticketMapper, AiConversationService conversationService) {
        this.ticketMapper = ticketMapper;
        this.conversationService = conversationService;
    }

    /**
     * 门户提交工单。
     *
     * @param memberId 当前会员 ID，未登录为空
     * @param request 提交请求
     * @return 已落库的工单
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiTicket submit(Long memberId, AiTicketSubmitRequest request) {
        AiTicket ticket = new AiTicket();
        ticket.setConversationId(StringUtils.hasText(request.conversationId()) ? request.conversationId() : null);
        ticket.setMemberId(memberId);
        ticket.setContact(trim(request.contact()));
        ticket.setQuestion(trim(request.question()));
        ticket.setStatus(STATUS_PENDING);
        // 显式写入时间而不是依赖数据库默认值：插入后要把工单编号回填并返回给买家，
        // 若时间留空，响应里就是 null，买家看不到提交时间。
        LocalDateTime now = LocalDateTime.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.insert(ticket);
        // 编号里带主键，插入后才能确定，因此先插入再回填。ticket_no 允许为空，
        // 唯一索引对多个 NULL 不生效，先插入不会撞唯一键。
        ticket.setTicketNo(buildTicketNo(ticket.getId()));
        ticketMapper.updateById(ticket);
        if (StringUtils.hasText(ticket.getConversationId())) {
            conversationService.markTicketed(ticket.getConversationId());
        }
        return ticket;
    }

    /**
     * 分页查询工单。
     *
     * @param status 处理状态，可为空
     * @param keyword 联系方式或问题关键字，可为空
     * @param current 当前页
     * @param size 页大小
     * @return 分页数据
     * @author Henfon
     * @date 2026-09-21
     */
    public IPage<AiTicket> page(String status, String keyword, long current, long size) {
        Page<AiTicket> page = new Page<>(current, size);
        return ticketMapper.selectPage(page, Wrappers.lambdaQuery(AiTicket.class)
                .eq(StringUtils.hasText(status), AiTicket::getStatus, status)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(AiTicket::getContact, keyword)
                        .or()
                        .like(AiTicket::getQuestion, keyword))
                // 待处理排最前，同状态内按提交时间倒序，运营打开列表就能直接处理。
                // 状态用 FIELD() 指定顺序而不是按字母排：按字母排会把 CLOSED 顶到最前面，
                // 已关闭的工单反而最显眼。
                .last("ORDER BY FIELD(status, 'PENDING', 'PROCESSING', 'CLOSED'), created_at DESC"));
    }

    /**
     * 查询工单详情。
     *
     * @param id 工单 ID
     * @return 工单
     * @author Henfon
     * @date 2026-09-21
     */
    public AiTicket detail(Long id) {
        AiTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("AI_TICKET_NOT_FOUND", "工单不存在");
        }
        return ticket;
    }

    /**
     * 买家查询自己提交的工单。
     *
     * 按 member_id 过滤而不是"按会员的会话关联工单"：直接留言的工单没有会话标识，用会话关联
     * 会把这一类整个漏掉。归属过滤写在 SQL 条件里，不依赖调用方先查一次再判断。
     *
     * @param memberId 当前会员 ID
     * @param current 当前页
     * @param size 页大小
     * @return 分页数据
     * @author Henfon
     * @date 2026-09-21
     */
    public IPage<AiTicket> pageByMember(Long memberId, long current, long size) {
        requireMember(memberId);
        Page<AiTicket> page = new Page<>(current, size);
        return ticketMapper.selectPage(page, Wrappers.lambdaQuery(AiTicket.class)
                .eq(AiTicket::getMemberId, memberId)
                .orderByDesc(AiTicket::getCreatedAt));
    }

    /**
     * 买家查询自己某张工单的详情。
     *
     * 不属于本人的工单与不存在的工单返回同一个错误：区分开来就成了一个探测接口，能拿它
     * 试出某个 ID 上是否真的存在工单。
     *
     * @param memberId 当前会员 ID
     * @param id 工单 ID
     * @return 工单
     * @author Henfon
     * @date 2026-09-21
     */
    public AiTicket findOwnedTicket(Long memberId, Long id) {
        requireMember(memberId);
        AiTicket ticket = id == null ? null : ticketMapper.selectById(id);
        if (ticket == null || !memberId.equals(ticket.getMemberId())) {
            throw new BusinessException("AI_TICKET_NOT_FOUND", "工单不存在");
        }
        return ticket;
    }

    /**
     * 校验调用方已登录。
     *
     * @param memberId 当前会员 ID
     * @author Henfon
     * @date 2026-09-21
     */
    private void requireMember(Long memberId) {
        if (memberId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "请先登录后查看工单");
        }
    }

    /**
     * 处理工单。
     *
     * @param id 工单 ID
     * @param request 处理请求
     * @param handlerId 处理人 ID
     * @param handlerName 处理人名称
     * @return 更新后的工单
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiTicket handle(Long id, AiTicketHandleRequest request, Long handlerId, String handlerName) {
        String status = request.status() == null ? "" : request.status().toUpperCase();
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("AI_TICKET_STATUS_INVALID", "不支持的处理结果");
        }
        AiTicket ticket = detail(id);
        ticket.setStatus(status);
        ticket.setHandleNote(StringUtils.hasText(request.handleNote()) ? request.handleNote().trim() : null);
        // 回复留空表示"本次不更新回复"，不是"清空回复"：运营改一个状态时不该把上一次写给买家
        // 的答复顺手抹掉，那对买家来说等于答复凭空消失。要覆盖就填新内容。
        if (StringUtils.hasText(request.reply())) {
            ticket.setReplyContent(request.reply().trim());
            ticket.setRepliedAt(LocalDateTime.now());
        }
        ticket.setHandlerId(handlerId);
        // 处理人写名称快照而不是只存 ID：账号改名或离职后，历史工单上挂的仍是当时处理人的名字。
        ticket.setHandlerName(handlerName);
        ticket.setHandledAt(STATUS_CLOSED.equals(status) || STATUS_PROCESSING.equals(status) ? LocalDateTime.now() : null);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    /**
     * 生成工单编号。
     *
     * 形如 AI20260921-0001。用日期加主键序号而不是随机串：运营在电话里核对的就是这个号，
     * 需要能一眼看出提交日期，且短到可以口头念完。
     *
     * @param id 工单主键
     * @return 工单编号
     * @author Henfon
     * @date 2026-09-21
     */
    private String buildTicketNo(Long id) {
        return "AI" + LocalDate.now().format(TICKET_DATE) + "-" + String.format("%04d", id % 10000);
    }

    /**
     * 去除首尾空白。
     *
     * @param text 原始文本
     * @return 处理后的文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String trim(String text) {
        return text == null ? null : text.trim();
    }
}
