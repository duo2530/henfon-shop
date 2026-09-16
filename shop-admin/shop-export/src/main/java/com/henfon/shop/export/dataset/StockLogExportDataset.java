package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.inventory.entity.InventoryStockLog;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 库存流水导出数据集。
 *
 * <p>流水表只存 SKU 标识，导出时整批补一次 SKU 编码与名称，避免逐行查询。</p>
 *
 * <p>支持按业务类型与 SKU 收窄范围。这两个条件是导出入口自己的筛选控件提供的，
 * 不复用页面上那组作用于库存台账的关键字与单据类型——台账与变动流水不是同一份数据，
 * 字段与取值域都对不上。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class StockLogExportDataset extends AbstractExportDataset<StockLogExportDataset.Row> {

    /** 库存变动业务类型文案，取值来源为库存模块的写入常量。 */
    private static final Map<String, String> BIZ_TYPE_LABELS = Map.of(
            "RESERVE", "下单预占", "RELEASE", "取消释放",
            "EXPIRE_RELEASE", "超时释放", "DEDUCT", "支付扣减");

    private final InventoryStockLogMapper inventoryStockLogMapper;

    private final CatalogSkuMapper catalogSkuMapper;

    /**
     * 创建库存流水导出数据集。
     *
     * @param inventoryStockLogMapper 库存流水数据访问对象
     * @param catalogSkuMapper SKU 数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public StockLogExportDataset(InventoryStockLogMapper inventoryStockLogMapper,
                                 CatalogSkuMapper catalogSkuMapper) {
        this.inventoryStockLogMapper = inventoryStockLogMapper;
        this.catalogSkuMapper = catalogSkuMapper;
    }

    /**
     * 库存流水导出行。
     *
     * @param createdAt 变动时间
     * @param skuCode SKU编码
     * @param skuName SKU名称
     * @param bizTypeLabel 业务类型
     * @param bizNo 关联单号
     * @param changeQuantity 变动数量
     * @param beforeAvailable 变动前可用
     * @param afterAvailable 变动后可用
     * @param beforeLocked 变动前锁定
     * @param afterLocked 变动后锁定
     * @param operatorId 操作人ID
     * @param remark 备注
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(LocalDateTime createdAt, String skuCode, String skuName, String bizTypeLabel,
                      String bizNo, Integer changeQuantity, Integer beforeAvailable, Integer afterAvailable,
                      Integer beforeLocked, Integer afterLocked, Long operatorId, String remark) {
    }

    @Override
    public ExportType type() {
        return ExportType.STOCK_LOG;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.dateTime("变动时间", Row::createdAt, 21),
                ExcelColumn.text("SKU编码", Row::skuCode, 20),
                ExcelColumn.text("SKU名称", Row::skuName, 28),
                ExcelColumn.text("业务类型", Row::bizTypeLabel, 12),
                ExcelColumn.text("关联单号", Row::bizNo, 24),
                ExcelColumn.integer("变动数量", Row::changeQuantity, 11),
                ExcelColumn.integer("变动前可用", Row::beforeAvailable, 12),
                ExcelColumn.integer("变动后可用", Row::afterAvailable, 12),
                ExcelColumn.integer("变动前锁定", Row::beforeLocked, 12),
                ExcelColumn.integer("变动后锁定", Row::afterLocked, 12),
                ExcelColumn.integer("操作人ID", Row::operatorId, 11),
                ExcelColumn.text("备注", Row::remark, 26));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        LambdaQueryWrapper<InventoryStockLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.skuId() != null, InventoryStockLog::getSkuId, query.skuId());
        wrapper.eq(StringUtils.hasText(query.bizType()), InventoryStockLog::getBizType,
                query.bizType() == null ? null : query.bizType().trim());
        wrapper.orderByDesc(InventoryStockLog::getId);

        streamPages(consumer, (pageNo, pageSize) -> {
            Page<InventoryStockLog> page = inventoryStockLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            List<InventoryStockLog> logs = page.getRecords();
            if (logs.isEmpty()) {
                return List.of();
            }
            Map<Long, CatalogSku> skus = loadSkus(logs);
            return logs.stream().map(log -> toRow(log, skus)).toList();
        });
    }

    /**
     * 整批加载本页流水涉及到的 SKU。
     *
     * @param logs 本页流水
     * @return SKU ID 到 SKU 的映射
     * @author Henfon
     * @date 2026-09-16
     */
    private Map<Long, CatalogSku> loadSkus(List<InventoryStockLog> logs) {
        List<Long> skuIds = logs.stream().map(InventoryStockLog::getSkuId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        List<CatalogSku> skus = catalogSkuMapper.selectBatchIds(skuIds);
        Map<Long, CatalogSku> result = new HashMap<>();
        for (CatalogSku sku : skus) {
            result.put(sku.getId(), sku);
        }
        return result;
    }

    /**
     * 实体转导出行为。
     *
     * @param log 库存流水实体
     * @param skus SKU 映射
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(InventoryStockLog log, Map<Long, CatalogSku> skus) {
        CatalogSku sku = log.getSkuId() == null ? null : skus.get(log.getSkuId());
        return new Row(log.getCreatedAt(),
                sku == null ? null : sku.getSkuCode(),
                sku == null ? null : sku.getSkuName(),
                BIZ_TYPE_LABELS.getOrDefault(log.getBizType(), log.getBizType()),
                log.getBizNo(), log.getChangeQuantity(), log.getBeforeAvailable(), log.getAfterAvailable(),
                log.getBeforeLocked(), log.getAfterLocked(), log.getOperatorId(), log.getRemark());
    }
}
