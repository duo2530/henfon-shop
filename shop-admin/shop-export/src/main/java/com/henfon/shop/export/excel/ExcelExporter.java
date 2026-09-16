package com.henfon.shop.export.excel;

import com.henfon.shop.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Excel 导出器。
 *
 * <p>全管理端的 Excel 生成统一走这里，避免各业务页面各写一套列宽与样式。
 * 采用 SXSSF 流式写盘，内存中只保留固定行数的滑动窗口，订单表这类几十万行的数据
 * 也不会把堆撑爆。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
public final class ExcelExporter {

    /** 滑动窗口行数，超出部分落临时文件。 */
    private static final int SXSSF_ROW_WINDOW = 200;

    /** 单次导出最大行数，超过时直接失败并提示收窄条件，避免产出无法打开的巨型文件。 */
    public static final int MAX_ROWS = 200_000;

    private static final int MIN_COLUMN_WIDTH = 8;

    private static final int MAX_COLUMN_WIDTH = 60;

    private static final String DATE_TIME_FORMAT = "yyyy-mm-dd hh:mm:ss";

    private static final String MONEY_FORMAT = "#,##0.00";

    private static final String INTEGER_FORMAT = "#,##0";

    private ExcelExporter() {
        // 工具类不允许实例化。
    }

    /**
     * 行数据推流器，由数据源实现分批查询并逐行推给导出器。
     *
     * @param <T> 行对象类型
     * @author Henfon
     * @date 2026-09-16
     */
    @FunctionalInterface
    public interface RowStreamer<T> {

        /**
         * 逐行推送数据。
         *
         * @param consumer 接收单行数据的消费者
         * @author Henfon
         * @date 2026-09-16
         */
        void stream(Consumer<T> consumer);
    }

    /**
     * 生成 Excel 文件内容。
     *
     * @param out 输出流
     * @param sheetName 工作表名称
     * @param columns 列定义
     * @param streamer 行数据推流器
     * @param <T> 行对象类型
     * @return 实际写入的数据行数，不含表头
     * @author Henfon
     * @date 2026-09-16
     */
    public static <T> int write(OutputStream out, String sheetName, List<ExcelColumn<T>> columns,
                                RowStreamer<T> streamer) {
        if (columns == null || columns.isEmpty()) {
            throw new BusinessException("EXPORT_COLUMNS_EMPTY", "导出列定义为空，无法生成文件");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW);
        workbook.setCompressTempFiles(true);
        try {
            SXSSFSheet sheet = workbook.createSheet(sheetName);
            sheet.setDefaultRowHeightInPoints(18);
            CellStyle headerStyle = buildHeaderStyle(workbook);
            Map<ExcelColumn.Kind, CellStyle> bodyStyles = buildBodyStyles(workbook);

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int index = 0; index < columns.size(); index++) {
                ExcelColumn<T> column = columns.get(index);
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(column.header());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(index, resolveColumnWidth(column));
            }
            // 冻结表头并开启筛选，长表格滚动时不会丢失列名。
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));

            int[] rowCount = new int[1];
            streamer.stream(item -> {
                if (rowCount[0] >= MAX_ROWS) {
                    throw new BusinessException("EXPORT_ROW_LIMIT_EXCEEDED",
                            "导出数据超过" + MAX_ROWS + "行上限，请缩小时间范围或筛选条件后重试");
                }
                Row row = sheet.createRow(rowCount[0] + 1);
                for (int index = 0; index < columns.size(); index++) {
                    ExcelColumn<T> column = columns.get(index);
                    Cell cell = row.createCell(index);
                    cell.setCellStyle(bodyStyles.get(column.kind()));
                    applyValue(cell, column, item);
                }
                rowCount[0]++;
            });

            workbook.write(out);
            return rowCount[0];
        } catch (IOException exception) {
            throw new BusinessException("EXPORT_WRITE_FAILED", "生成 Excel 文件失败：" + exception.getMessage());
        } finally {
            // 关闭工作簿时 SXSSFWorkbook 自身会清理滑动窗口落盘的临时文件，
            // 因此不再单独调用已标记过时的 dispose()。
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 关闭失败不影响已生成的内容，交由临时文件清理策略兜底。
            }
        }
    }

    /**
     * 写入单元格值。
     *
     * @param cell 单元格
     * @param column 列定义
     * @param item 行对象
     * @param <T> 行对象类型
     * @author Henfon
     * @date 2026-09-16
     */
    private static <T> void applyValue(Cell cell, ExcelColumn<T> column, T item) {
        Object value = column.extractor().apply(item);
        if (value == null) {
            // 缺数据就留空，不用占位符或推算值填充，避免对外报表出现臆造数据。
            cell.setBlank();
            return;
        }
        switch (column.kind()) {
            case INTEGER -> cell.setCellValue(toDouble(value));
            case DECIMAL, MONEY -> cell.setCellValue(toDouble(value));
            case DATETIME -> cell.setCellValue(toDate(value));
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    /**
     * 将数值对象转为 double。
     *
     * @param value 原始值
     * @return double 值
     * @author Henfon
     * @date 2026-09-16
     */
    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            // 无法解析的数值按 0 处理，避免整张报表因为单行脏数据生成失败。
            return 0D;
        }
    }

    /**
     * 将日期对象转为 Excel 可识别的日期。
     *
     * @param value 原始值
     * @return java.util.Date
     * @author Henfon
     * @date 2026-09-16
     */
    private static Date toDate(Object value) {
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            // 数据库存的是无时区的本地时间，按系统时区还原才能与页面显示一致。
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof LocalDate date) {
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        return new Date();
    }

    /**
     * 计算列宽。
     *
     * <p>以业务配置的宽度为准，表头比配置更宽时自动放宽，并限制在合理区间内，
     * 既不会因为「联系方式」这类窄列看不清内容，也不会因为「收货地址」把表格撑出屏幕。</p>
     *
     * @param column 列定义
     * @return POI 列宽单位值
     * @author Henfon
     * @date 2026-09-16
     */
    private static int resolveColumnWidth(ExcelColumn<?> column) {
        int headerWidth = displayWidth(column.header()) + 3;
        int resolved = Math.max(column.width(), headerWidth);
        resolved = Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, resolved));
        // POI 的列宽以 1/256 个字符为单位。
        return resolved * 256;
    }

    /**
     * 计算字符串的显示宽度，中文与全角字符按两个字符计。
     *
     * @param text 文本
     * @return 显示宽度
     * @author Henfon
     * @date 2026-09-16
     */
    private static int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            width += text.charAt(index) > 0x7F ? 2 : 1;
        }
        return width;
    }

    /**
     * 构建表头样式。
     *
     * @param workbook 工作簿
     * @return 表头样式
     * @author Henfon
     * @date 2026-09-16
     */
    private static CellStyle buildHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 按单元格类型构建正文样式，样式对象复用以免触发 xlsx 的样式数量上限。
     *
     * @param workbook 工作簿
     * @return 各类型对应的样式
     * @author Henfon
     * @date 2026-09-16
     */
    private static Map<ExcelColumn.Kind, CellStyle> buildBodyStyles(SXSSFWorkbook workbook) {
        Map<ExcelColumn.Kind, CellStyle> styles = new EnumMap<>(ExcelColumn.Kind.class);
        for (ExcelColumn.Kind kind : ExcelColumn.Kind.values()) {
            CellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            switch (kind) {
                case INTEGER -> {
                    style.setAlignment(HorizontalAlignment.RIGHT);
                    style.setDataFormat(workbook.createDataFormat().getFormat(INTEGER_FORMAT));
                }
                case DECIMAL -> {
                    style.setAlignment(HorizontalAlignment.RIGHT);
                    style.setDataFormat(workbook.createDataFormat().getFormat(MONEY_FORMAT));
                }
                case MONEY -> {
                    style.setAlignment(HorizontalAlignment.RIGHT);
                    style.setDataFormat(workbook.createDataFormat().getFormat(MONEY_FORMAT));
                }
                case DATETIME -> {
                    style.setAlignment(HorizontalAlignment.CENTER);
                    style.setDataFormat(workbook.createDataFormat().getFormat(DATE_TIME_FORMAT));
                }
                default -> style.setAlignment(HorizontalAlignment.LEFT);
            }
            styles.put(kind, style);
        }
        return styles;
    }
}
