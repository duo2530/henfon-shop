package com.henfon.shop.ai.chat;

/**
 * AI 客服的提示词。
 *
 * 放在代码里而不是数据库或配置文件：这几段文字是回答质量的主要决定因素，改动必须走
 * 代码评审与发布流程。做成后台可改的配置项，等于把「模型会不会乱说话」交给一个没有
 * 测试覆盖的输入框。
 *
 * 文本里不要出现花括号。ChatClient 默认会用 ST 模板渲染系统提示与用户输入，花括号会被
 * 当成占位符；虽然装配时已关掉模板渲染（NoOpTemplateRenderer），但把这条约束写在这里，
 * 免得日后有人把渲染器改回去时踩坑。
 *
 * @author Henfon
 * @date 2026-09-21
 */
public final class AiPrompts {

    /** 知识片段与用户问题之间的分隔，供拼装上下文时使用。 */
    public static final String KNOWLEDGE_HEADER = "以下是本次检索到的资料，只允许依据这些内容回答业务问题：";

    /** 没有检索到任何资料时的替代文本，明确告诉模型"这次没有依据"。 */
    public static final String KNOWLEDGE_EMPTY = "本次没有检索到任何资料。";

    /**
     * 门户买家侧客服的系统提示。
     *
     * 核心是第 1 条与第 2 条：把模型的角色限制在「组织语言」，事实只能来自检索资料。
     * 大模型天然倾向于把半截信息补全，客服场景里补出来的时效、金额、赔付直接变成投诉，
     * 所以宁可让它说不知道。
     */
    public static final String PORTAL_SYSTEM = """
            你是 Henfon商城的在线客服，只负责解答买家在浏览商品、下单、支付、物流、售后环节的问题。

            必须遵守：
            1. 涉及商品信息、平台规则、流程说明的问题，只能依据下面提供的资料回答。资料里没有写的内容，回答「这个我暂时查不到准确信息，可以留下联系方式让客服帮你确认」，不许推测，不许把常识当作平台规则。
            2. 不许给出任何承诺性表述，包括但不限于送达时效、发货时间、价格优惠、赔偿金额、退款到账时间。资料里如果写了时效，照抄资料原文，不要换算、不要加码。
            3. 不许编造订单号、物流单号、金额、政策条款、活动名称。没有实时查询结果时，就说明需要转人工核实。
            4. 只用中文回答，像正常客服说话那样，直接给结论。不要重复买家的问题，不要自我介绍，不要罗列一堆无关选项，长度控制在 200 字以内。
            5. 问题指向某个具体订单、账户或售后单，而你手上没有对应的实时数据时，明确说明需要转人工，并提示买家留下手机号或邮箱。
            6. 与商城购物无关的话题，直接说明你只负责商城购物咨询，不要展开。
            """;

    /** 管理端运营助手的系统提示，P2 启用，先把边界写死在这里。 */
    public static final String ADMIN_SYSTEM = """
            你是 Henfon商城管理端的运营助手，面向内部运营人员。

            必须遵守：
            1. 只回答经营指标查询、指标口径解释、运营文案生成三类问题，其余问题明确说明超出当前能力范围。
            2. 数字只能来自工具返回的结果。工具没有返回的指标，回答「当前没有这项数据」，不许估算，不许用行业经验值代替。
            3. 不许透出买家手机号、收货地址等个人信息，需要定位具体用户时返回脱敏后的标识。
            4. 只用中文回答，先给结论再给口径说明，长度控制在 300 字以内。
            """;

    /**
     * 拼装带资料的系统提示。
     *
     * @param basePrompt 基础系统提示
     * @param knowledge 检索到的资料文本，无资料时传空串
     * @return 系统提示
     * @author Henfon
     * @date 2026-09-21
     */
    public static String withKnowledge(String basePrompt, String knowledge) {
        StringBuilder prompt = new StringBuilder(basePrompt);
        prompt.append('\n').append(KNOWLEDGE_HEADER).append('\n');
        if (knowledge == null || knowledge.isBlank()) {
            prompt.append(KNOWLEDGE_EMPTY).append('\n');
        } else {
            prompt.append(knowledge).append('\n');
        }
        return prompt.toString();
    }

    private AiPrompts() {
        // 常量类不允许实例化。
    }
}
