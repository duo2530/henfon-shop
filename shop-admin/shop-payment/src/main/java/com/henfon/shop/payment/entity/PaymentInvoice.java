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
 * 订单发票申请实体。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
@TableName("payment_invoice")
public class PaymentInvoice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String invoiceNo;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private Integer invoiceType;
    private String title;
    private String taxNo;
    private String email;
    private BigDecimal amount;
    private Integer status;
    private String invoiceUrl;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
