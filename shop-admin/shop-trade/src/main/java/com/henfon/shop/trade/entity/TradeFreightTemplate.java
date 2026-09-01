package com.henfon.shop.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易运费模板实体。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Data
@TableName("trade_freight_template")
public class TradeFreightTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String carrierName;
    private Integer baseWeightGram;
    private BigDecimal baseFee;
    private Integer additionalWeightGram;
    private BigDecimal additionalFee;
    private BigDecimal freeShippingThreshold;
    private BigDecimal remoteSurcharge;
    private String remoteRegionsCsv;
    private Integer status;
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
