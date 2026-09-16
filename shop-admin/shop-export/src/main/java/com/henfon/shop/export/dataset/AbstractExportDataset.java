package com.henfon.shop.export.dataset;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 导出数据集基类。
 *
 * <p>统一按固定页大小循环拉取数据，保证导出「全部」时不会把整表读进内存。
 * 分页循环的退出条件有两个：取到空结果，或某一页不足页大小，两者都代表已到末尾。</p>
 *
 * @param <T> 行对象类型
 * @author Henfon
 * @date 2026-09-16
 */
public abstract class AbstractExportDataset<T> implements ExportDataset<T> {

    /** 单次拉取条数，兼顾查询次数与单批内存占用。 */
    protected static final int PAGE_SIZE = 500;

    /**
     * 按页循环推送数据。
     *
     * @param consumer 行消费者
     * @param loader 分页加载函数，入参为页码（从 1 开始）与页大小
     * @author Henfon
     * @date 2026-09-16
     */
    protected void streamPages(Consumer<T> consumer, BiFunction<Long, Long, List<T>> loader) {
        long pageNo = 1L;
        while (true) {
            List<T> batch = loader.apply(pageNo, (long) PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                return;
            }
            for (T item : batch) {
                consumer.accept(item);
            }
            if (batch.size() < PAGE_SIZE) {
                return;
            }
            pageNo++;
        }
    }
}
