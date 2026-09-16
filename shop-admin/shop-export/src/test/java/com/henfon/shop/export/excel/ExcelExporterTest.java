package com.henfon.shop.export.excel;

import com.henfon.shop.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 导出器测试。
 *
 * <p>断言的都是导出规范里被明确要求过的行为：表头样式、冻结首行、自动筛选、
 * 列宽上下限，以及缺数据时留空而不是填占位值。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
class ExcelExporterTest {

    /**
     * 导出行测试数据。
     *
     * @param transNo 流水单号
     * @param quantity 数量
     * @param amount 金额
     * @param settledAt 结算时间
     * @author Henfon
     * @date 2026-09-16
     */
    private record Row(String transNo, Integer quantity, BigDecimal amount, LocalDateTime settledAt) {
    }

    private static List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("流水单号", Row::transNo, 24),
                ExcelColumn.integer("数量", Row::quantity, 11),
                ExcelColumn.money("金额(¥)", Row::amount, 14),
                ExcelColumn.dateTime("结算时间", Row::settledAt, 21));
    }

    /**
     * 验证表头样式、冻结首行、自动筛选与正文取值。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldWriteHeaderAndBodyWithFrozenHeaderRow() throws Exception {
        LocalDateTime settledAt = LocalDateTime.of(2026, 9, 16, 10, 30, 0);
        List<Row> rows = List.of(
                new Row("PAY-1", 3, new BigDecimal("128.50"), settledAt),
                new Row("PAY-2", 0, BigDecimal.ZERO, null));

        byte[] content = write(columns(), rows);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals(1, workbook.getNumberOfSheets());
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("对账流水", sheet.getSheetName());

            // 表头：文案、加粗、浅灰底、居中。
            var headerCell = sheet.getRow(0).getCell(0);
            assertEquals("流水单号", headerCell.getStringCellValue());
            assertEquals(HorizontalAlignment.CENTER, headerCell.getCellStyle().getAlignment());
            assertEquals(IndexedColors.GREY_25_PERCENT.getIndex(),
                    headerCell.getCellStyle().getFillForegroundColor());
            assertTrue(workbook.getFontAt(headerCell.getCellStyle().getFontIndexAsInt()).getBold());

            // 冻结首行 + 表头行自动筛选。
            assertNotNull(sheet.getPaneInformation());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            assertNotNull(sheet.getCTWorksheet().getAutoFilter());
            assertEquals("A1:D1", sheet.getCTWorksheet().getAutoFilter().getRef());

            // 正文：表头占第 0 行，两行数据落在第 1、2 行；数值与日期按类型写入，空值留空白单元格。
            assertEquals(2, sheet.getLastRowNum());
            assertEquals("PAY-1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(1).getCellType());
            assertEquals(3D, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals(128.50D, sheet.getRow(1).getCell(2).getNumericCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(3).getCellType());
            // 缺数据的单元格留空即可，POI 可能不落盘空单元格，两种读回结果都算通过。
            var blankCell = sheet.getRow(2).getCell(3);
            assertTrue(blankCell == null || blankCell.getCellType() == CellType.BLANK);
        }
    }

    /**
     * 验证列宽以业务配置为准、表头更宽时自动放宽，并限制在上下限内。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldResolveColumnWidthAgainstHeaderAndLimits() throws Exception {
        List<ExcelColumn<Row>> columns = List.of(
                ExcelColumn.text("流水单号", Row::transNo, 24),
                // 表头显示宽度 14 + 3 = 17，大于配置的 8，应放宽到 17。
                ExcelColumn.text("商品库数据导出", Row::quantity, 8),
                // 配置宽度 3 低于下限，应抬到 8。
                ExcelColumn.text("数", Row::quantity, 3),
                // 表头远超上限，应压到 60。
                ExcelColumn.text("这是一个明显超过六十个字符宽度的超长表头文案用来验证上限收敛行为是否生效", Row::amount, 200));

        byte[] content = write(columns, List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // POI 列宽以 1/256 个字符为单位。
            assertEquals(24 * 256, sheet.getColumnWidth(0));
            assertEquals(17 * 256, sheet.getColumnWidth(1));
            assertEquals(8 * 256, sheet.getColumnWidth(2));
            assertEquals(60 * 256, sheet.getColumnWidth(3));
        }
    }

    /**
     * 验证列定义为空时给出明确业务错误，不生成打不开的空文件。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldRejectEmptyColumnDefinition() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ExcelExporter.write(out, "空表", List.of(), consumer -> {
                }));

        assertEquals("EXPORT_COLUMNS_EMPTY", exception.getCode());
        assertEquals(0, out.size());
    }

    /**
     * 验证没有任何数据行时仍产出只含表头的文件。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldProduceHeaderOnlyFileWhenNoData() throws Exception {
        byte[] content = write(columns(), List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals(1, sheet.getPhysicalNumberOfRows());
            assertEquals("流水单号", sheet.getRow(0).getCell(0).getStringCellValue());
            assertNull(sheet.getRow(1));
        }
    }

    /**
     * 按列定义写出 Excel 并返回文件内容。
     *
     * @param columns 列定义
     * @param rows 导出行
     * @return xlsx 文件内容
     * @author Henfon
     * @date 2026-09-16
     */
    private static byte[] write(List<ExcelColumn<Row>> columns, List<Row> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rowCount = ExcelExporter.write(out, "对账流水", columns, streamer(rows));
        assertEquals(rows.size(), rowCount);
        return out.toByteArray();
    }

    /**
     * 把固定行列表包装成导出器要求的推流器。
     *
     * @param rows 导出行
     * @return 推流器
     * @author Henfon
     * @date 2026-09-16
     */
    private static ExcelExporter.RowStreamer<Row> streamer(List<Row> rows) {
        return (Consumer<Row> consumer) -> rows.forEach(consumer);
    }
}
