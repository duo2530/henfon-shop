package com.henfon.shop.export.excel;

import java.util.function.Function;

/**
 * Excel 列定义。
 *
 * <p>每类导出用一组显式列定义描述表头、取值方式、列宽与单元格格式。
 * 刻意不使用注解加反射：列宽需要逐个手工调，取值逻辑也能在编译期被检查到。</p>
 *
 * @param header 表头文案
 * @param extractor 行对象到单元格值的取值函数，返回 null 时输出空单元格
 * @param width 列宽，单位为字符数，中文按两个字符计算
 * @param kind 单元格格式
 * @param <T> 行对象类型
 * @author Henfon
 * @date 2026-09-16
 */
public record ExcelColumn<T>(String header, Function<T, Object> extractor, int width, Kind kind) {

    /** 单元格格式，决定对齐方式与数字格式。 */
    public enum Kind {

        /** 文本，左对齐。 */
        TEXT,

        /** 整数，右对齐并使用千分位。 */
        INTEGER,

        /** 小数，右对齐，保留两位。 */
        DECIMAL,

        /** 金额，右对齐，保留两位并使用千分位。 */
        MONEY,

        /** 日期时间，居中，格式 yyyy-mm-dd hh:mm:ss。 */
        DATETIME
    }

    /**
     * 定义文本列。
     *
     * @param header 表头
     * @param extractor 取值函数
     * @param width 列宽
     * @param <T> 行对象类型
     * @return 列定义
     * @author Henfon
     * @date 2026-09-16
     */
    public static <T> ExcelColumn<T> text(String header, Function<T, Object> extractor, int width) {
        return new ExcelColumn<>(header, extractor, width, Kind.TEXT);
    }

    /**
     * 定义整数列。
     *
     * @param header 表头
     * @param extractor 取值函数
     * @param width 列宽
     * @param <T> 行对象类型
     * @return 列定义
     * @author Henfon
     * @date 2026-09-16
     */
    public static <T> ExcelColumn<T> integer(String header, Function<T, Object> extractor, int width) {
        return new ExcelColumn<>(header, extractor, width, Kind.INTEGER);
    }

    /**
     * 定义小数列。
     *
     * @param header 表头
     * @param extractor 取值函数
     * @param width 列宽
     * @param <T> 行对象类型
     * @return 列定义
     * @author Henfon
     * @date 2026-09-16
     */
    public static <T> ExcelColumn<T> decimal(String header, Function<T, Object> extractor, int width) {
        return new ExcelColumn<>(header, extractor, width, Kind.DECIMAL);
    }

    /**
     * 定义金额列。
     *
     * @param header 表头
     * @param extractor 取值函数
     * @param width 列宽
     * @param <T> 行对象类型
     * @return 列定义
     * @author Henfon
     * @date 2026-09-16
     */
    public static <T> ExcelColumn<T> money(String header, Function<T, Object> extractor, int width) {
        return new ExcelColumn<>(header, extractor, width, Kind.MONEY);
    }

    /**
     * 定义日期时间列。
     *
     * @param header 表头
     * @param extractor 取值函数
     * @param width 列宽
     * @param <T> 行对象类型
     * @return 列定义
     * @author Henfon
     * @date 2026-09-16
     */
    public static <T> ExcelColumn<T> dateTime(String header, Function<T, Object> extractor, int width) {
        return new ExcelColumn<>(header, extractor, width, Kind.DATETIME);
    }
}
