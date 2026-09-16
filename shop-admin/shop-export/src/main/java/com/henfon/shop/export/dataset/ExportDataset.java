package com.henfon.shop.export.dataset;

import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;

import java.util.List;
import java.util.function.Consumer;

/**
 * 导出数据集。
 *
 * <p>每类导出一个实现，负责声明列定义并按条件把数据推给导出器。实现必须分批查询，
 * 不能把全量结果先堆进内存再返回。</p>
 *
 * @param <T> 行对象类型
 * @author Henfon
 * @date 2026-09-16
 */
public interface ExportDataset<T> {

    /**
     * 本数据集对应的导出类型。
     *
     * @return 导出类型
     * @author Henfon
     * @date 2026-09-16
     */
    ExportType type();

    /**
     * 列定义。
     *
     * @return 列定义列表
     * @author Henfon
     * @date 2026-09-16
     */
    List<ExcelColumn<T>> columns();

    /**
     * 按条件分批推送数据行。
     *
     * @param consumer 行消费者
     * @param query 查询条件
     * @author Henfon
     * @date 2026-09-16
     */
    void stream(Consumer<T> consumer, ExportQuery query);
}
