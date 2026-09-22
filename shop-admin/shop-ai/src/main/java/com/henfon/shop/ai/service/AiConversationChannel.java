package com.henfon.shop.ai.service;

import com.henfon.shop.ai.dto.AiAgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 人工会话的进程内消息通道。
 *
 * 买家与客服各持一条长连接订阅同一条会话，这里负责把消息推给该会话的所有订阅者。
 * 用 Flux.create 自建订阅者列表，而不是 Sinks.many().multicast()：后者在所有订阅者
 * 离开时会终止 sink，客服接入、买家刷新页面的间隙里只要双方都不在线，通道就被标记完成，
 * 之后再有人订阅也收不到东西了。
 *
 * 消息本身不依赖本通道的可靠性——落库才是唯一事实来源，订阅时先从库里推一份快照，
 * 断线重连不会丢消息。本通道只负责"在线时尽快送达"。
 *
 * 当前部署是单实例，订阅者存在本进程内存里。将来多实例部署时，跨实例的消息需要经
 * Redis 发布订阅转发，届时把 publish 拆成"本机投递"与"广播到其他实例"两步即可，
 * subscribe 侧不用动。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
public class AiConversationChannel {

    private static final Logger log = LoggerFactory.getLogger(AiConversationChannel.class);

    /** 空转心跳间隔。空闲连接会被中间设备按"无流量"掐断，定期推一条让链路保持活跃。 */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

    /** 单个会话的在线订阅者数量上限，防止异常客户端反复订阅把订阅者列表撑大。 */
    private static final int MAX_SUBSCRIBERS_PER_CONVERSATION = 8;

    private final Map<String, List<FluxSink<AiAgentEvent>>> subscribers = new ConcurrentHashMap<>();

    /**
     * 订阅一个会话的消息流。
     *
     * @param conversationId 会话标识
     * @return 事件流，含心跳
     * @author Henfon
     * @date 2026-09-21
     */
    public Flux<AiAgentEvent> subscribe(String conversationId) {
        Flux<AiAgentEvent> events = Flux.create(sink -> {
            List<FluxSink<AiAgentEvent>> sinks = subscribers.computeIfAbsent(conversationId,
                    key -> new CopyOnWriteArrayList<>());
            if (sinks.size() >= MAX_SUBSCRIBERS_PER_CONVERSATION) {
                log.warn("会话 {} 的订阅者已达上限 {}，拒绝新订阅", conversationId, MAX_SUBSCRIBERS_PER_CONVERSATION);
                sink.error(new IllegalStateException("订阅数超出上限"));
                return;
            }
            sinks.add(sink);
            log.debug("会话 {} 新增订阅者，当前 {} 个", conversationId, sinks.size());
            sink.onDispose(() -> {
                sinks.remove(sink);
                if (sinks.isEmpty()) {
                    subscribers.remove(conversationId, sinks);
                }
                log.debug("会话 {} 的订阅者已释放", conversationId);
            });
        });
        return Flux.merge(events, heartbeat(conversationId));
    }

    /**
     * 把事件推给会话的全部订阅者。
     *
     * 没有订阅者时静默返回：客服还没接入、买家关掉了窗口都是正常情况，消息已经落库，
     * 对方下次上线订阅时会从快照里拿到。
     *
     * @param conversationId 会话标识
     * @param event 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public void publish(String conversationId, AiAgentEvent event) {
        List<FluxSink<AiAgentEvent>> sinks = subscribers.get(conversationId);
        if (sinks == null || sinks.isEmpty()) {
            return;
        }
        for (FluxSink<AiAgentEvent> sink : sinks) {
            if (sink.isCancelled()) {
                continue;
            }
            sink.next(event);
        }
    }

    /**
     * 当前在线订阅者数量，用于判断对方是否在线。
     *
     * @param conversationId 会话标识
     * @return 订阅者数量
     * @author Henfon
     * @date 2026-09-21
     */
    public int subscriberCount(String conversationId) {
        List<FluxSink<AiAgentEvent>> sinks = subscribers.get(conversationId);
        return sinks == null ? 0 : sinks.size();
    }

    /**
     * 心跳流。
     *
     * @param conversationId 会话标识
     * @return 心跳事件流
     * @author Henfon
     * @date 2026-09-21
     */
    private Flux<AiAgentEvent> heartbeat(String conversationId) {
        return Flux.interval(HEARTBEAT_INTERVAL).map(tick -> AiAgentEvent.ping(conversationId));
    }
}
