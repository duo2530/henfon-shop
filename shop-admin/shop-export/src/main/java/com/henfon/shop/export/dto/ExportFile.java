package com.henfon.shop.export.dto;

import java.io.InputStream;

/**
 * 导出文件下载载荷。
 *
 * @param fileName 下载文件名
 * @param fileSize 文件大小，单位字节
 * @param content 文件内容流，由调用方负责关闭
 * @author Henfon
 * @date 2026-09-16
 */
public record ExportFile(String fileName, Long fileSize, InputStream content) {
}
