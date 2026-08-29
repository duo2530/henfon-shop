package com.henfon.shop.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车明细实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("trade_cart_item")
public class TradeCartItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Integer selected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
