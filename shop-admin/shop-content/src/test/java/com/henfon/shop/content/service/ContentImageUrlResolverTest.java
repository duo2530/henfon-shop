package com.henfon.shop.content.service;

import com.henfon.shop.integration.storage.ImageReferenceResolver;
import com.henfon.shop.integration.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * 内容图片地址解析器单元测试。
 *
 * @author Henfon
 * @date 2026-09-17
 */
@ExtendWith(MockitoExtension.class)
class ContentImageUrlResolverTest {

    @Mock
    private MinioStorageService storageService;

    /**
     * 构造被测解析器，转发到共用的存储引用解析器。
     *
     * @return 内容图片地址解析器
     * @author Henfon
     * @date 2026-09-17
     */
    private ContentImageUrlResolver resolver() {
        return new ContentImageUrlResolver(new ImageReferenceResolver(storageService));
    }

    /**
     * 校验读取时逐个重签数组内的对象键，外部图床地址原样保留。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldResignEachUrlInJsonArray() {
        when(storageService.resolveAccessUrl("media/a.png")).thenReturn("https://signed/a.png");
        when(storageService.resolveAccessUrl("https://cdn.example/b.png")).thenReturn("https://cdn.example/b.png");

        String result = resolver()
                .resignJsonArray("[\"media/a.png\",\"https://cdn.example/b.png\"]");

        assertEquals("[\"https://signed/a.png\",\"https://cdn.example/b.png\"]", result);
    }

    /**
     * 校验非法 JSON 原样返回，不阻塞整页内容查询。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldKeepRawValueWhenJsonIsInvalid() {
        assertEquals("not-json", resolver().resignJsonArray("not-json"));
    }

    /**
     * 校验写入时把预签名地址归一化为对象键。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldNormalizeUploadedUrlsWhenWriting() {
        String signedUrl = "https://minio.local/henfon-shop/media/a.png?X-Amz-Signature=abc";
        when(storageService.normalizeReference(signedUrl)).thenReturn("media/a.png");

        String result = resolver().normalizeJsonArray(List.of(signedUrl));

        assertEquals("[\"media/a.png\"]", result);
    }

    /**
     * 校验空白项被丢弃且去掉首尾空格。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldSkipBlankEntriesWhenWriting() {
        when(storageService.normalizeReference("media/a.png")).thenReturn("media/a.png");

        String result = resolver()
                .normalizeJsonArray(List.of("  media/a.png  ", "   "));

        assertEquals("[\"media/a.png\"]", result);
    }

    /**
     * 校验空输入不产生空数组字符串。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldReturnNullWhenNormalizingEmptyInput() {
        ContentImageUrlResolver resolver = resolver();

        assertNull(resolver.normalizeJsonArray(null));
        assertNull(resolver.normalizeJsonArray(List.of()));
    }
}
