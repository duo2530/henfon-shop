package com.henfon.shop.ai.service;

import com.henfon.shop.ai.config.AiProperties;
import com.henfon.shop.ai.dto.AiAgentEvent;
import com.henfon.shop.ai.entity.AiConversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工接待的空闲收敛。
 *
 * 人工会话的状态推进原本只依赖客服点「结束服务」。客服去处理别的会话、临时被叫走、下班忘了
 * 收尾，或者干脆把浏览器关掉，会话就永久停在 HUMAN：买家那侧会一直挂着"人工客服正在接待"、
 * 长连接不断，连提问都被拦住——HUMAN 状态下门户走的是人工链路，智能客服不可用。实测库里
 * 有会话停在这个状态十三个小时，客服一句话都没说过。
 *
 * 两道闸按时间递进：
 * <ul>
 *   <li>催办：空闲到 {@code idleRemindMinutes} 推一条提醒给接待客服，只提示不动状态。客服
 *       看着买家慢慢打字时被系统抢走会话，比没人管更糟。</li>
 *   <li>回收：空闲到 {@code idleCloseMinutes} 才真的结束，把买家交回智能客服，走的就是客服
 *       手动结束那条链路（推 ended → 门户弹评价并把记录读回来）。</li>
 * </ul>
 *
 * 空闲的判据取"最后一条消息时间"与"客服接入时间"中较晚的一个，而不是只看消息时间：客服
 * 接入后一句话没说就消失的会话，最后消息时间可能是 null，只按它判定会永远匹配不上。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@Service
public class AiAgentIdleService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentIdleService.class);

    /** 入库时间与 JVM 时钟的容差，避免刚接入的会话被判成已空闲。 */
    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(1);

    private final AiConversationService conversationService;

    private final AiConversationChannel channel;

    private final AiProperties properties;

    /**
     * 创建空闲收敛服务。
     *
     * @param conversationService 会话服务
     * @param channel 会话消息通道
     * @param properties AI 业务配置
     * @author Henfon
     * @date 2026-09-22
     */
    public AiAgentIdleService(AiConversationService conversationService,
                              AiConversationChannel channel,
                              AiProperties properties) {
        this.conversationService = conversationService;
        this.channel = channel;
        this.properties = properties;
    }

    /**
     * 扫描一轮人工接待中的会话：该催办的催办，该回收的回收。
     *
     * @return 本轮结束的会话数
     * @author Henfon
     * @date 2026-09-22
     */
    public int sweep() {
        long remindMinutes = properties.getAgent().getIdleRemindMinutes();
        long closeMinutes = properties.getAgent().getIdleCloseMinutes();
        if (closeMinutes <= 0 && remindMinutes <= 0) {
            return 0;
        }
        List<AiConversation> serving = conversationService.servingSessions(properties.getAgent().getScanLimit());
        if (serving.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int closed = 0;
        for (AiConversation conversation : serving) {
            long idleMinutes = idleMinutes(conversation, now);
            if (closeMinutes > 0 && idleMinutes >= closeMinutes) {
                if (close(conversation, idleMinutes)) {
                    closed++;
                }
                continue;
            }
            if (remindMinutes > 0 && idleMinutes >= remindMinutes) {
                channel.publish(conversation.getConversationId(), AiAgentEvent.idle(conversation, idleMinutes));
            }
        }
        if (closed > 0) {
            log.info("本轮回收了 {} 个空闲超时的人工会话", closed);
        }
        return closed;
    }

    /**
     * 计算会话已经空闲的分钟数。
     *
     * @param conversation 会话
     * @param now 当前时间
     * @return 空闲分钟数，时间倒挂（时钟回拨）时返回 0
     * @author Henfon
     * @date 2026-09-22
     */
    private long idleMinutes(AiConversation conversation, LocalDateTime now) {
        LocalDateTime last = conversation.getLastMessageAt();
        LocalDateTime joined = conversation.getAgentJoinedAt();
        // 取较晚的一个：客服接入后没说过话时 lastMessageAt 可能为空或早于接入时间。
        LocalDateTime activity = last == null ? joined : (joined == null || last.isAfter(joined) ? last : joined);
        if (activity == null) {
            return 0;
        }
        long minutes = Duration.between(activity, now).toMinutes();
        return minutes < 0 ? 0 : minutes;
    }

    /**
     * 结束一个空闲超时会话。
     *
     * 会话服务的条件更新返回 null 表示它已经不在人工接待中（客服抢先结束了，或另一轮扫描已经
     * 处理过），这时不该推事件，否则客服会收到一条"服务已结束"的重复通知。
     *
     * @param conversation 会话
     * @param idleMinutes 空闲分钟数
     * @return 真的结束了返回 true
     * @author Henfon
     * @date 2026-09-22
     */
    private boolean close(AiConversation conversation, long idleMinutes) {
        AiConversation closed = conversationService.closeForIdle(conversation.getConversationId(), idleMinutes);
        if (closed == null) {
            return false;
        }
        channel.publish(closed.getConversationId(), AiAgentEvent.ended(closed));
        return true;
    }
}
