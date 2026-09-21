package com.henfon.shop.ai.tool;

import com.henfon.shop.marketing.dto.MarketingMemberCouponView;
import com.henfon.shop.marketing.service.MarketingPortalService;
import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.service.TradeAfterSaleService;
import com.henfon.shop.trade.service.TradeOrderService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户买家侧客服的工具集：订单、物流、售后、优惠券。
 *
 * 这四样都是「状态」，正确答案只有一个且随时在变，不能交给向量检索。检索是相似度匹配，
 * 买家问「我上个月那单」时可能召回别人的相似订单，而且拿不出确定的那一行来对质。所以走工具：
 * 模型只表达调用意图，后端用当前登录身份查库，真实数据回填。模型在这条链路里负责组织语言，
 * 不负责提供事实。
 *
 * 身份一律从 ToolContext 取，工具签名里不出现 memberId。若写成 queryOrder(orderNo) 直接查库，
 * 等于开放了一个查询任意订单的接口——别人拿你的订单号就能读到订单内容，而这种试探只需要发生
 * 一次。身份也不能从 SecurityContextHolder 取：工具回调跑在 Reactor 的流式链路上，不一定还在
 * 原来的请求线程上，ThreadLocal 里很可能取不到认证信息。
 *
 * 越权与不存在返回同一句「没查到」，不区分原因。区分了就等于告诉试探者这个订单存在。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiPortalTools {

    /** 工具上下文里的会员身份键，必须与编排层注入时使用的键一致。 */
    public static final String CONTEXT_MEMBER_ID = "memberId";

    /** 回答里时间只精确到分钟，秒与毫秒对买家没有意义，还白占上下文。 */
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 单次回给模型的条数上限。调大只会挤占上下文，不会让回答更准。 */
    private static final int MAX_ORDERS = 10;

    /** 物流轨迹最多回最近的几条，完整轨迹动辄几十条，模型只需要知道最新状态。 */
    private static final int MAX_LOGISTICS_NODES = 8;

    private static final int MAX_AFTER_SALES = 10;

    /**
     * 订单状态文案，与门户 App.tsx 的 mapPortalOrderStatus 保持同一套说法。
     * AI 说的和买家在「我的订单」里看到的必须是同一句，否则买家会以为客服在说另一个订单。
     */
    private static final Map<Integer, String> ORDER_STATUS_LABELS = Map.of(
            10, "待付款",
            20, "待发货",
            30, "运输中",
            40, "已完成",
            50, "已取消",
            60, "退款中",
            70, "已退款");

    /** 售后状态文案，与门户 OrdersPage 的 AFTER_SALE_STATUS_LABELS 保持一致。 */
    private static final Map<Integer, String> AFTER_SALE_STATUS_LABELS = Map.of(
            10, "待审核",
            20, "处理中",
            30, "已完成",
            40, "已驳回",
            50, "已取消");

    /** 售后类型文案，与门户 OrdersPage 的 AFTER_SALE_TYPE_LABELS 保持一致。 */
    private static final Map<Integer, String> AFTER_SALE_TYPE_LABELS = Map.of(
            1, "仅退款",
            2, "退货退款",
            3, "换货");

    private final TradeOrderService orderService;

    private final TradeAfterSaleService afterSaleService;

    private final MarketingPortalService marketingPortalService;

    /**
     * 创建门户工具集。
     *
     * @param orderService 交易订单服务
     * @param afterSaleService 交易售后服务
     * @param marketingPortalService 门户营销查询服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiPortalTools(TradeOrderService orderService,
                         TradeAfterSaleService afterSaleService,
                         MarketingPortalService marketingPortalService) {
        this.orderService = orderService;
        this.afterSaleService = afterSaleService;
        this.marketingPortalService = marketingPortalService;
    }

    /**
     * 查询当前登录会员的订单列表。
     *
     * @param status 订单状态筛选词，可为空
     * @param context 工具上下文，会员身份由此注入
     * @return 订单摘要
     * @author Henfon
     * @date 2026-09-21
     */
    @Tool(description = "查询当前登录会员的订单列表。买家问「我的订单」「我买的东西在哪」「最近买过什么」时调用。"
            + "status 可选，只能填这几个词之一：待付款、待发货、运输中、已完成、已取消，不填表示全部。"
            + "这里只返回订单摘要，要看某一单的物流轨迹，请再用订单号调用查询订单详情。")
    public Map<String, Object> queryMyOrders(String status, ToolContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long memberId = memberId(context);
        if (memberId == null) {
            result.put("note", NOT_LOGGED_IN);
            return result;
        }
        List<TradeOrder> orders = orderService.listMemberOrders(memberId, parseOrderStatus(status), MAX_ORDERS);
        result.put("orders", orders.stream().map(this::toOrderBrief).toList());
        if (orders.isEmpty()) {
            result.put("note", "该会员名下没有符合条件的订单，不要推测订单内容。");
        }
        return result;
    }

    /**
     * 按订单号查询当前登录会员的订单详情与物流轨迹。
     *
     * @param orderNo 订单号，由买家提供
     * @param context 工具上下文
     * @return 订单详情、商品明细与物流轨迹
     * @author Henfon
     * @date 2026-09-21
     */
    @Tool(description = "按订单号查询当前登录会员的订单详情与物流轨迹。买家提供了订单号，或者问「这单到哪了」"
            + "「什么时候发的货」时调用。orderNo 要用买家给出的完整订单号，不要自己编。"
            + "如果没查到，就如实告诉买家没查到这个订单，让他核对订单号，不要猜测订单的状态或物流。")
    public Map<String, Object> queryMyOrderDetail(String orderNo, ToolContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long memberId = memberId(context);
        if (memberId == null) {
            result.put("note", NOT_LOGGED_IN);
            return result;
        }
        TradeOrder order = orderService.findMemberOrderByNo(memberId, orderNo);
        if (order == null) {
            // 订单不存在与订单不属于该会员都落到这里，返回同一句话。区分了就泄露了「这个订单存在」。
            result.put("note", "没有查到这个订单号对应的订单。请让买家核对订单号后再查，不要推测订单内容。");
            return result;
        }
        result.put("order", toOrderBrief(order));
        result.put("items", orderService.listItems(order.getId()).stream().map(this::toItemBrief).toList());
        result.put("logistics", logisticsBrief(order.getId()));
        return result;
    }

    /**
     * 查询当前登录会员的售后进度。
     *
     * @param context 工具上下文
     * @return 售后申请列表
     * @author Henfon
     * @date 2026-09-21
     */
    @Tool(description = "查询当前登录会员的售后申请与处理进度。买家问退款、退货、换货办得怎么样了时调用。"
            + "这里只能查到买家自己的售后单，查不到就如实说明没有售后记录。")
    public Map<String, Object> queryMyAfterSales(ToolContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long memberId = memberId(context);
        if (memberId == null) {
            result.put("note", NOT_LOGGED_IN);
            return result;
        }
        List<TradeAfterSale> records = afterSaleService.listByMember(memberId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (TradeAfterSale record : records) {
            if (items.size() >= MAX_AFTER_SALES) {
                break;
            }
            items.add(toAfterSaleBrief(record));
        }
        result.put("afterSales", items);
        if (items.isEmpty()) {
            result.put("note", "该会员没有售后申请记录。");
        }
        return result;
    }

    /**
     * 查询当前登录会员已领取的优惠券。
     *
     * @param context 工具上下文
     * @return 会员优惠券列表
     * @author Henfon
     * @date 2026-09-21
     */
    @Tool(description = "查询当前登录会员已经领取的优惠券。买家问「我有什么券」「我的优惠券还能用吗」时调用。"
            + "这里查的是买家已领的券；如果买家问平台有哪些券可以领、怎么领，那属于平台规则，按知识库资料回答。")
    public Map<String, Object> queryMyCoupons(ToolContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long memberId = memberId(context);
        if (memberId == null) {
            result.put("note", NOT_LOGGED_IN);
            return result;
        }
        List<MarketingMemberCouponView> coupons = marketingPortalService.memberCouponViews(memberId, null);
        result.put("coupons", coupons.stream().map(this::toCouponBrief).toList());
        if (coupons.isEmpty()) {
            result.put("note", "该会员还没有领取过优惠券，可以提示他到「领券中心」看看。");
        }
        return result;
    }

    /** 未登录时的统一说明。工具在未登录时根本不会挂给模型，这条是防御性的。 */
    private static final String NOT_LOGGED_IN =
            "买家还没有登录，查不到他的任何个人数据。请引导他先登录再问，不要凭猜测回答订单、售后或优惠券的问题。";

    /**
     * 从工具上下文读取会员身份。
     *
     * 用 Number 而不是 Long 接收：值由编排层放进 Map，装箱类型经序列化或框架转手后可能变成
     * Integer，直接强转 Long 会在运行期抛 ClassCastException。
     *
     * @param context 工具上下文
     * @return 会员 ID，未注入时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private Long memberId(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object value = context.getContext().get(CONTEXT_MEMBER_ID);
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 解析模型给的订单状态词。
     *
     * 认不出来就不过滤，而不是返回空列表：模型把状态理解偏时，宁可多给几单让它自己挑，
     * 也好过回一句「没有订单」——那会让买家以为订单丢了。
     *
     * @param status 状态词
     * @return 订单状态码，无法识别时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private Integer parseOrderStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim()) {
            case "待付款", "未付款", "待支付" -> 10;
            case "待发货", "已付款" -> 20;
            case "运输中", "已发货", "待收货", "配送中", "已寄出" -> 30;
            case "已完成", "完成", "已签收" -> 40;
            case "已取消", "取消" -> 50;
            case "退款中" -> 60;
            case "已退款" -> 70;
            default -> null;
        };
    }

    /**
     * 组装订单摘要。
     *
     * 刻意不带收货人姓名、手机号与详细地址：这些字段对回答「订单到哪了」没有帮助，
     * 却会随对话上下文一起被送到模型服务端，没有必要扩大这个面。
     *
     * @param order 订单
     * @return 摘要
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<String, Object> toOrderBrief(TradeOrder order) {
        Map<String, Object> brief = new LinkedHashMap<>();
        putIfPresent(brief, "orderNo", order.getOrderNo());
        putIfPresent(brief, "status", statusLabel(order.getOrderStatus()));
        putIfPresent(brief, "payableAmount", amount(order.getPayableAmount()));
        putIfPresent(brief, "paidAmount", amount(order.getPaidAmount()));
        putIfPresent(brief, "createdAt", time(order.getCreatedAt()));
        putIfPresent(brief, "logisticsCompany", order.getLogisticsCompany());
        putIfPresent(brief, "trackingNo", order.getTrackingNo());
        return brief;
    }

    /**
     * 组装订单商品明细。
     *
     * @param item 订单明细
     * @return 摘要
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<String, Object> toItemBrief(TradeOrderItem item) {
        Map<String, Object> brief = new LinkedHashMap<>();
        putIfPresent(brief, "productName", item.getProductName());
        putIfPresent(brief, "skuName", item.getSkuName());
        if (item.getQuantity() != null) {
            brief.put("quantity", item.getQuantity());
        }
        putIfPresent(brief, "unitPrice", amount(item.getUnitPrice()));
        return brief;
    }

    /**
     * 组装物流轨迹，只保留最近的几条。
     *
     * 不依赖查询返回的顺序，自己按事件时间排一遍再取尾部的若干条：轨迹表的排序口径改过，
     * 而这里取错一头就会把「已签收」当成第一站，回答出来的进度正好反了。
     *
     * @param orderId 订单 ID
     * @return 物流节点列表，最新的在前
     * @author Henfon
     * @date 2026-09-21
     */
    private List<Map<String, Object>> logisticsBrief(Long orderId) {
        List<TradeOrderLogistics> nodes = new ArrayList<>(orderService.listLogistics(orderId));
        if (nodes.isEmpty()) {
            return List.of();
        }
        nodes.sort(Comparator.comparing(TradeOrderLogistics::getEventTime,
                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));
        int from = Math.max(0, nodes.size() - MAX_LOGISTICS_NODES);
        List<Map<String, Object>> briefs = new ArrayList<>();
        for (int index = nodes.size() - 1; index >= from; index--) {
            TradeOrderLogistics node = nodes.get(index);
            Map<String, Object> brief = new LinkedHashMap<>();
            putIfPresent(brief, "time", time(node.getEventTime()));
            putIfPresent(brief, "description", node.getEventDescription());
            putIfPresent(brief, "location", node.getEventLocation());
            briefs.add(brief);
        }
        return briefs;
    }

    /**
     * 组装售后摘要。
     *
     * @param record 售后单
     * @return 摘要
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<String, Object> toAfterSaleBrief(TradeAfterSale record) {
        Map<String, Object> brief = new LinkedHashMap<>();
        putIfPresent(brief, "afterSaleNo", record.getAfterSaleNo());
        putIfPresent(brief, "type", AFTER_SALE_TYPE_LABELS.get(record.getAfterSaleType()));
        putIfPresent(brief, "status", AFTER_SALE_STATUS_LABELS.get(record.getStatus()));
        putIfPresent(brief, "refundAmount", amount(record.getRefundAmount()));
        putIfPresent(brief, "appliedAt", time(record.getCreatedAt()));
        // 售后表只存订单 ID，带上订单号才能让买家对上号。售后单已按会员过滤过，这里回查不带归属校验。
        TradeOrder order = record.getOrderId() == null ? null : orderService.findById(record.getOrderId());
        if (order != null) {
            putIfPresent(brief, "orderNo", order.getOrderNo());
        }
        return brief;
    }

    /**
     * 组装优惠券摘要。
     *
     * @param coupon 会员优惠券视图
     * @return 摘要
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<String, Object> toCouponBrief(MarketingMemberCouponView coupon) {
        Map<String, Object> brief = new LinkedHashMap<>();
        putIfPresent(brief, "title", coupon.couponTitle());
        putIfPresent(brief, "discountAmount", amount(coupon.discountAmount()));
        putIfPresent(brief, "minSpend", amount(coupon.minSpend()));
        putIfPresent(brief, "validUntil", time(coupon.endAt()));
        Integer receiveStatus = coupon.receiveStatus();
        String statusLabel = receiveStatus == null ? null : switch (receiveStatus) {
            case 0 -> "未使用";
            case 1 -> "已使用";
            case 2 -> "已过期";
            default -> null;
        };
        putIfPresent(brief, "status", statusLabel);
        return brief;
    }

    /**
     * 订单状态码转文案。
     *
     * @param orderStatus 状态码
     * @return 文案
     * @author Henfon
     * @date 2026-09-21
     */
    private String statusLabel(Integer orderStatus) {
        if (orderStatus == null) {
            return null;
        }
        return ORDER_STATUS_LABELS.getOrDefault(orderStatus, "状态 " + orderStatus);
    }

    /**
     * 金额转两位小数的字符串。
     *
     * 转成字符串而不是原样给 BigDecimal：模型看到 268.00 与 268 会当作两个不同的值，
     * 统一格式能少一类莫名其妙的金额误述。
     *
     * @param value 金额
     * @return 金额文本，为空时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 时间转文本。
     *
     * @param value 时间
     * @return 时间文本，为空时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private String time(LocalDateTime value) {
        return value == null ? null : value.format(MINUTE_FORMAT);
    }

    /**
     * 只放有值的字段。
     *
     * 未支付订单的 paidAmount、未发货订单的物流单号都是空，一并塞进上下文会让模型把
     * 「字段存在但为空」读成「金额是 0」或者「有单号但没查到」。
     *
     * @param target 目标结构
     * @param key 字段名
     * @param value 字段值
     * @author Henfon
     * @date 2026-09-21
     */
    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }
}
