package com.henfon.shop.export.dataset;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.export.entity.ExportType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 导出数据集注册表。
 *
 * <p>由 Spring 收集全部实现后建立类型索引，新增导出类型只需增加一个数据集实现，
 * 编排层无需改动。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class ExportDatasetRegistry {

    private final Map<ExportType, ExportDataset<?>> datasets;

    /**
     * 创建导出数据集注册表。
     *
     * @param datasetList 容器内全部导出数据集实现
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportDatasetRegistry(List<ExportDataset<?>> datasetList) {
        Map<ExportType, ExportDataset<?>> collected = new EnumMap<>(ExportType.class);
        for (ExportDataset<?> dataset : datasetList) {
            ExportDataset<?> existing = collected.put(dataset.type(), dataset);
            if (existing != null) {
                throw new IllegalStateException("导出类型重复注册：" + dataset.type());
            }
        }
        this.datasets = Map.copyOf(collected);
    }

    /**
     * 获取指定类型的数据集。
     *
     * @param type 导出类型
     * @return 数据集实现
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportDataset<?> require(ExportType type) {
        ExportDataset<?> dataset = datasets.get(type);
        if (dataset == null) {
            throw new BusinessException("EXPORT_TYPE_UNSUPPORTED", "暂不支持该类型的导出：" + type);
        }
        return dataset;
    }

    /**
     * 已注册的导出类型。
     *
     * @return 类型集合
     * @author Henfon
     * @date 2026-09-16
     */
    public java.util.Set<ExportType> supportedTypes() {
        return datasets.keySet();
    }
}
