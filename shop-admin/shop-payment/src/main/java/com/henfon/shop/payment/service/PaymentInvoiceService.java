package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.dto.PaymentInvoiceCreateRequest;
import com.henfon.shop.payment.dto.PaymentInvoiceResponse;
import com.henfon.shop.payment.dto.PaymentInvoiceStatusRequest;
import com.henfon.shop.payment.entity.PaymentInvoice;
import com.henfon.shop.payment.mapper.PaymentInvoiceMapper;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.service.TradeOrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单发票申请应用服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class PaymentInvoiceService {

    private static final int TYPE_NORMAL = 1;
    private static final int TYPE_SPECIAL = 2;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ISSUING = 1;
    private static final int STATUS_ISSUED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;

    private final PaymentInvoiceMapper invoiceMapper;
    private final TradeOrderService tradeOrderService;

    /**
     * 创建发票申请服务。
     *
     * @param invoiceMapper 发票数据访问对象
     * @param tradeOrderService 订单应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentInvoiceService(PaymentInvoiceMapper invoiceMapper, TradeOrderService tradeOrderService) {
        this.invoiceMapper = invoiceMapper;
        this.tradeOrderService = tradeOrderService;
    }

    /**
     * 为已支付订单创建发票申请，同一订单重复提交时返回原申请。
     *
     * @param memberId 当前会员ID
     * @param orderId 订单ID
     * @param request 发票申请信息
     * @return 发票申请响应
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public PaymentInvoiceResponse create(Long memberId, Long orderId, PaymentInvoiceCreateRequest request) {
        TradeOrder order = tradeOrderService.findById(orderId);
        if (memberId == null || !memberId.equals(order.getMemberId())) {
            throw new BusinessException("PAYMENT_INVOICE_FORBIDDEN", "无权为该订单申请发票");
        }
        // 历史订单可能未回填 payment_status，但订单已进入待发货、已发货或已完成状态时同样视为已支付。
        boolean paidOrder = Integer.valueOf(1).equals(order.getPaymentStatus())
                || (order.getOrderStatus() != null && order.getOrderStatus() >= 20 && order.getOrderStatus() <= 40);
        if (!paidOrder) {
            throw new BusinessException("PAYMENT_INVOICE_ORDER_UNPAID", "仅已支付订单可以申请发票");
        }
        if (request.invoiceType() == TYPE_SPECIAL && !StringUtils.hasText(request.taxNo())) {
            throw new BusinessException("PAYMENT_INVOICE_TAX_NO_REQUIRED", "专用发票必须填写税号");
        }
        PaymentInvoice existing = invoiceMapper.selectOne(new LambdaQueryWrapper<PaymentInvoice>()
                .eq(PaymentInvoice::getOrderId, orderId).last("LIMIT 1 FOR UPDATE"));
        if (existing != null) {
            return PaymentInvoiceResponse.from(existing);
        }

        PaymentInvoice invoice = new PaymentInvoice();
        invoice.setInvoiceNo("INV" + System.currentTimeMillis() + randomSuffix());
        invoice.setOrderId(order.getId());
        invoice.setOrderNo(order.getOrderNo());
        invoice.setMemberId(memberId);
        invoice.setInvoiceType(request.invoiceType());
        invoice.setTitle(request.title().trim());
        invoice.setTaxNo(normalize(request.taxNo()));
        invoice.setEmail(normalize(request.email()));
        invoice.setAmount(order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount());
        invoice.setStatus(STATUS_PENDING);
        invoice.setRequestedAt(LocalDateTime.now());
        try {
            invoiceMapper.insert(invoice);
        } catch (DuplicateKeyException exception) {
            // 并发重复申请由订单唯一索引兜底，返回先提交成功的申请记录。
            PaymentInvoice concurrent = invoiceMapper.selectOne(new LambdaQueryWrapper<PaymentInvoice>()
                    .eq(PaymentInvoice::getOrderId, orderId).last("LIMIT 1"));
            if (concurrent != null) {
                return PaymentInvoiceResponse.from(concurrent);
            }
            throw new BusinessException("PAYMENT_INVOICE_CREATE_CONCURRENT", "发票申请正在处理中，请稍后重试");
        }
        return PaymentInvoiceResponse.from(invoice);
    }

    /**
     * 查询会员订单发票申请。
     *
     * @param memberId 当前会员ID
     * @param orderId 订单ID
     * @return 发票申请响应，不存在时返回空
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentInvoiceResponse getMember(Long memberId, Long orderId) {
        PaymentInvoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<PaymentInvoice>()
                .eq(PaymentInvoice::getOrderId, orderId).eq(PaymentInvoice::getMemberId, memberId)
                .last("LIMIT 1"));
        return invoice == null ? null : PaymentInvoiceResponse.from(invoice);
    }

    /**
     * 分页查询后台发票申请。
     *
     * @param orderId 订单ID，可选
     * @param memberId 会员ID，可选
     * @param keyword 发票号、订单号或抬头关键字
     * @param status 发票状态
     * @param current 当前页
     * @param size 页大小
     * @return 发票分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<PaymentInvoice> page(Long orderId, Long memberId, String keyword, Integer status,
                                     long current, long size) {
        // 后台分页统一限制单页大小，避免大量发票抬头信息一次性返回。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return invoiceMapper.selectPage(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<PaymentInvoice>()
                .eq(orderId != null, PaymentInvoice::getOrderId, orderId)
                .eq(memberId != null, PaymentInvoice::getMemberId, memberId)
                .and(normalizedKeyword != null, wrapper -> wrapper.like(PaymentInvoice::getInvoiceNo, normalizedKeyword)
                        .or().like(PaymentInvoice::getOrderNo, normalizedKeyword)
                        .or().like(PaymentInvoice::getTitle, normalizedKeyword))
                .eq(status != null, PaymentInvoice::getStatus, status)
                .orderByDesc(PaymentInvoice::getCreatedAt));
    }

    /**
     * 更新后台发票状态及开票结果。
     *
     * @param invoiceNo 发票申请号
     * @param request 状态和开票结果
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public PaymentInvoiceResponse updateStatus(String invoiceNo, PaymentInvoiceStatusRequest request) {
        PaymentInvoice invoice = require(invoiceNo);
        int target = request.status();
        if (!canTransition(invoice.getStatus(), target)) {
            throw new BusinessException("PAYMENT_INVOICE_STATUS_INVALID", "发票状态不允许重复或逆向变更");
        }
        validateStatusPayload(target, request);
        invoice.setStatus(target);
        invoice.setInvoiceUrl(normalize(request.invoiceUrl()));
        invoice.setFailureReason(normalize(request.failureReason()));
        if (target == STATUS_ISSUED) {
            invoice.setIssuedAt(LocalDateTime.now());
            invoice.setFailureReason(null);
        } else if (target == STATUS_FAILED) {
            invoice.setIssuedAt(null);
        }
        if (invoiceMapper.updateById(invoice) == 0) {
            throw new BusinessException("PAYMENT_INVOICE_CONCURRENT_UPDATE", "发票申请已被其他操作修改");
        }
        return PaymentInvoiceResponse.from(invoice);
    }

    /**
     * 校验发票终态所需的开票结果信息，确保状态更新后具备完整交付凭证。
     *
     * @param target 目标状态
     * @param request 状态更新请求
     * @author Henfon
     * @date 2026-09-04
     */
    private void validateStatusPayload(int target, PaymentInvoiceStatusRequest request) {
        // 已开票必须关联可下载的电子发票地址，避免用户看到已开票却无法取得凭证。
        if (target == STATUS_ISSUED && !StringUtils.hasText(request.invoiceUrl())) {
            throw new BusinessException("PAYMENT_INVOICE_URL_REQUIRED", "已开票状态必须提供发票文件地址");
        }
        // 开票失败必须记录原因，便于客服和运营定位后续补开或联系用户。
        if (target == STATUS_FAILED && !StringUtils.hasText(request.failureReason())) {
            throw new BusinessException("PAYMENT_INVOICE_FAILURE_REASON_REQUIRED", "开票失败必须填写失败原因");
        }
    }

    /**
     * 查询发票申请实体，不存在时抛出业务异常。
     *
     * @param invoiceNo 发票申请号
     * @return 发票实体
     * @author Henfon
     * @date 2026-08-31
     */
    private PaymentInvoice require(String invoiceNo) {
        if (!StringUtils.hasText(invoiceNo)) {
            throw new BusinessException("PAYMENT_INVOICE_NOT_FOUND", "发票申请号不能为空");
        }
        PaymentInvoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<PaymentInvoice>()
                .eq(PaymentInvoice::getInvoiceNo, invoiceNo.trim()).last("LIMIT 1"));
        if (invoice == null) {
            throw new BusinessException("PAYMENT_INVOICE_NOT_FOUND", "发票申请不存在");
        }
        return invoice;
    }

    /**
     * 判断发票状态转换是否合法。
     *
     * @param from 原状态
     * @param to 目标状态
     * @return 是否允许转换
     * @author Henfon
     * @date 2026-08-31
     */
    private boolean canTransition(Integer from, int to) {
        if (from == null) {
            return false;
        }
        return switch (from) {
            case STATUS_PENDING -> to == STATUS_ISSUING || to == STATUS_FAILED || to == STATUS_CANCELLED;
            case STATUS_ISSUING -> to == STATUS_ISSUED || to == STATUS_FAILED;
            case STATUS_FAILED -> to == STATUS_ISSUING || to == STATUS_CANCELLED;
            default -> false;
        };
    }

    /**
     * 生成短随机后缀，降低同毫秒生成重复申请号的概率。
     *
     * @return 随机后缀
     * @author Henfon
     * @date 2026-08-31
     */
    private String randomSuffix() {
        return Integer.toHexString((int) (Math.random() * 0xFFFFFF));
    }

    /**
     * 规范化可选文本。
     *
     * @param value 原始文本
     * @return 去空白后的文本或空值
     * @author Henfon
     * @date 2026-08-31
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
