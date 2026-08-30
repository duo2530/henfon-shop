package com.henfon.shop.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付退款单实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("payment_refund_order")
public class PaymentRefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private String idempotencyKey;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private BigDecimal amount;
    private String reason;
    private Integer status;
    private String transactionNo;
    private String notifyPayload;
    private LocalDateTime requestedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
