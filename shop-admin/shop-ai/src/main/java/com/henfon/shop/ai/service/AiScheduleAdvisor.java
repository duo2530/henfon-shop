package com.henfon.shop.ai.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.ai.config.AiProperties;
import com.henfon.shop.ai.dto.AiSchedulePlanRequest;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 把一句排班要求翻译成生成参数。
 *
 * 模型只做这一件事，排班本身仍由 {@link AiScheduleService} 的规则算：让模型直接吐一整张排班表，
 * 它会漏人、写错时间格式、撞上"一个客服一天一段班"的唯一键，而且说不出为什么这么排。翻译成
 * 参数就不一样了——错了也只是参数错，主管在预览里一眼看得见、能改，也落不到库里。
 *
 * <p>开关关掉时对话模型这个 Bean 根本不存在，这里取不到就明确报"未启用"，让界面退回手工填
 * 参数；一键排班本身不依赖它，照用。解析不出 JSON 同样直接报错，不静默给个默认值——默认值
 * 会让人以为它听懂了。</p>
 *
 * @author Henfon
 * @date 2026-09-22
 */
@Component
public class AiScheduleAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AiScheduleAdvisor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认覆盖时段：模型没说时间时按这个来，与界面上的默认值一致。 */
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);

    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("H:mm");

    private static final DateTimeFormatter HOUR_MINUTE_SECOND = DateTimeFormatter.ofPattern("H:mm:ss");

    /** 去掉时间文本里除数字与冒号以外的东西，让"9点""09：00"都能进解析。 */
    private static final Pattern NOISE = Pattern.compile("[^0-9:]");

    private static final String INSTRUCTION = """
            把下面这句排班要求翻译成 JSON，只输出 JSON，不要解释、不要代码块标记。

            字段（没有提到的就不要输出）：
            - startTime / endTime：覆盖时段，24 小时制 "HH:mm"
            - perDay：同一时刻需要几个人在岗，整数
            - weekdays：生成哪几天，用 1 到 7 的数组表示（1 周一，7 周日）
            - maxShiftHours：单人单班最多几小时，超过就把一天拆成几段
            - maxShiftsPerAgent：每人每周最多排几个班
            - clearUncovered：是否删除没被覆盖到的旧班次，布尔

            例子：{"startTime":"09:00","endTime":"18:00","perDay":1,"weekdays":[1,2,3,4,5,6,7]}
            """;

    private final ChatClient chatClient;

    /**
     * 创建排班参数解析器。
     *
     * @param chatModelProvider 对话模型，AI 未启用时为空
     * @param properties AI 配置
     * @author Henfon
     * @date 2026-09-22
     */
    public AiScheduleAdvisor(ObjectProvider<ChatModel> chatModelProvider, AiProperties properties) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(properties.getChat().getChatModel())
                        .withTemperature(0D)
                        .withMaxToken(512)
                        .build())
                // 提示词里有 JSON 的花括号，默认的 ST 渲染器会把它当占位符，渲染直接失败
                .defaultTemplateRenderer(new NoOpTemplateRenderer())
                .build();
    }

    /**
     * 判断能否解析：AI 未启用时界面要退回手工填参数。
     *
     * @return 可用返回 true
     * @author Henfon
     * @date 2026-09-22
     */
    public boolean available() {
        return chatClient != null;
    }

    /**
     * 把一句排班要求翻译成生成参数。
     *
     * @param text 排班要求
     * @return 生成参数
     * @author Henfon
     * @date 2026-09-22
     */
    public AiSchedulePlanRequest parse(String text) {
        if (chatClient == null) {
            throw new BusinessException("AI_DISABLED", "AI 客服未启用，读不了这句话：请直接填写排班参数");
        }
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("AI_SCHEDULE_TEXT_EMPTY", "请先写一句排班要求，例如「工作日两人，早九晚六」");
        }
        String raw;
        try {
            raw = chatClient.prompt()
                    .user(INSTRUCTION + "\n排班要求：" + text.trim())
                    .call()
                    .content();
        } catch (Exception exception) {
            log.warn("排班参数解析调用失败：{}", exception.getMessage());
            throw new BusinessException("AI_SCHEDULE_PARSE_FAILED", "没读懂这句话，请换个说法或直接填写排班参数");
        }
        return toRequest(raw);
    }

    /**
     * 从模型的回复里取出 JSON 并转成参数。
     *
     * @param raw 模型回复
     * @return 生成参数
     * @author Henfon
     * @date 2026-09-22
     */
    private AiSchedulePlanRequest toRequest(String raw) {
        JsonNode node = readJson(raw);
        if (node == null) {
            log.warn("排班参数解析结果不是 JSON：{}", abbreviate(raw));
            throw new BusinessException("AI_SCHEDULE_PARSE_FAILED", "没读懂这句话，请换个说法或直接填写排班参数");
        }
        return new AiSchedulePlanRequest(
                time(node, "startTime", DEFAULT_START),
                time(node, "endTime", DEFAULT_END),
                integer(node, "perDay"),
                weekdays(node),
                integer(node, "maxShiftHours"),
                integer(node, "maxShiftsPerAgent"),
                node.hasNonNull("clearUncovered") ? node.get("clearUncovered").asBoolean(false) : null);
    }

    /**
     * 从一段文本里抠出 JSON 对象。
     *
     * 模型有时会把 JSON 包在代码块里或前后加两句说明，所以按首尾花括号截取，而不是直接
     * 反序列化整段。
     *
     * @param raw 模型回复
     * @return 解析出的对象，不是 JSON 时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private JsonNode readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(raw.substring(start, end + 1));
            return node == null || node.isMissingNode() ? null : node;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 读一个时间字段。
     *
     * @param node JSON 对象
     * @param field 字段名
     * @param fallback 读不到时的取值
     * @return 时间
     * @author Henfon
     * @date 2026-09-22
     */
    private LocalTime time(JsonNode node, String field, LocalTime fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = NOISE.matcher(value.asText().trim().replace('：', ':').replace('点', ':'))
                .replaceAll("");
        if (!text.contains(":")) {
            text = text + ":00";
        }
        for (DateTimeFormatter formatter : List.of(HOUR_MINUTE, HOUR_MINUTE_SECOND)) {
            try {
                return LocalTime.parse(text, formatter);
            } catch (DateTimeParseException exception) {
                // 换下一种写法再试，两种都不是时间就退回默认
            }
        }
        return fallback;
    }

    /**
     * 读一个整数字段。
     *
     * @param node JSON 对象
     * @param field 字段名
     * @return 整数，读不到或不是正数时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        int number = value.asInt();
        return number > 0 ? number : null;
    }

    /**
     * 读星期字段，容忍数字数组与中文写法。
     *
     * @param node JSON 对象
     * @return 星期列表，读不到时返回 null 表示按整周处理
     * @author Henfon
     * @date 2026-09-22
     */
    private List<Integer> weekdays(JsonNode node) {
        JsonNode value = node.get("weekdays");
        if (value == null || value.isNull()) {
            return null;
        }
        Set<Integer> days = new LinkedHashSet<>();
        if (value.isArray()) {
            value.forEach(item -> days.addAll(days(item.asText())));
        } else if (value.isTextual()) {
            days.addAll(days(value.asText()));
        } else if (value.isNumber()) {
            days.addAll(days(String.valueOf(value.asInt())));
        }
        return days.isEmpty() ? null : days.stream().sorted().toList();
    }

    /**
     * 从一段写法里认出星期。
     *
     * @param token 写法，可能是数字、"周一"、"工作日"、"周末"之类
     * @return 认出的星期
     * @author Henfon
     * @date 2026-09-22
     */
    private List<Integer> days(String token) {
        if (!StringUtils.hasText(token)) {
            return List.of();
        }
        String text = token.trim();
        if (text.matches("[1-7]")) {
            return List.of(Integer.valueOf(text));
        }
        List<Integer> named = new ArrayList<>();
        String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < names.length; i++) {
            if (text.contains(names[i]) || text.contains(names[i].replace("周", "星期"))) {
                named.add(i + 1);
            }
        }
        if (!named.isEmpty()) {
            return named;
        }
        if (text.contains("工作日") || text.contains("周一到周五")) {
            return List.of(1, 2, 3, 4, 5);
        }
        if (text.contains("周末")) {
            return List.of(6, 7);
        }
        if (text.contains("每天") || text.contains("整周") || text.contains("全周")) {
            return List.of(1, 2, 3, 4, 5, 6, 7);
        }
        return List.of();
    }

    /**
     * 截断日志里的文本。
     *
     * @param text 原始文本
     * @return 截断后的文本
     * @author Henfon
     * @date 2026-09-22
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }
}
