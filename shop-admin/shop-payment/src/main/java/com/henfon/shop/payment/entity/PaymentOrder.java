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
 * 支付单实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private String channel;
    private Integer status;
    private BigDecimal amount;
    private String transactionNo;
    private String notifyPayload;
    private LocalDateTime paidAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
    /** 人工对账状态覆盖值：reconciled/ignored，空值表示按系统规则计算。 */
    private String reconciliationStatus;
    /** 人工对账处理备注。 */
    private String reconciliationRemark;
}
