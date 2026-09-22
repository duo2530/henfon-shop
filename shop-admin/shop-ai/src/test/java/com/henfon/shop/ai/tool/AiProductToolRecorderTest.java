package com.henfon.shop.ai.tool;

import com.henfon.shop.catalog.service.CatalogPortalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品检索工具包装的测试。
 *
 * 它做两件事：把调用原样透传给被包装的工具，把返回里的商品编号记下来。前者错了买家拿到的是
 * 截断或改写的检索结果，后者错了这一轮少几张卡片，所以两处都单独验一遍。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@ExtendWith(MockitoExtension.class)
class AiProductToolRecorderTest {

    private static final String RESULT = "{\"products\":[{\"productId\":55,\"name\":\"三模机械键盘\"},"
            + "{\"productId\":7,\"name\":\"蓝牙耳机\"}],\"note\":\"以上是搜索结果\"}";

    @Mock
    private ToolCallback delegate;

    @Test
    void recordsProductIdsAndPassesResultThrough() {
        List<Long> collected = new ArrayList<>();
        when(delegate.call(anyString())).thenReturn(RESULT);

        String returned = new AiProductToolRecorder(delegate, collected::addAll).call("{\"keyword\":\"键盘\"}");

        assertEquals(RESULT, returned, "工具返回要原样给回模型，包装不能改写检索结果");
        assertEquals(List.of(55L, 7L), collected);
        verify(delegate).call("{\"keyword\":\"键盘\"}");
    }

    @Test
    void ignoresResultWithoutProducts() {
        List<Long> collected = new ArrayList<>();
        when(delegate.call(anyString())).thenReturn("{\"note\":\"没有拿到有效的商品关键词\"}");

        String returned = new AiProductToolRecorder(delegate, collected::addAll).call("{}");

        assertEquals("{\"note\":\"没有拿到有效的商品关键词\"}", returned);
        assertTrue(collected.isEmpty());
    }

    @Test
    void ignoresUnreadableResult() {
        List<Long> collected = new ArrayList<>();
        when(delegate.call(anyString())).thenReturn("not a json");

        String returned = new AiProductToolRecorder(delegate, collected::addAll).call("{}");

        assertEquals("not a json", returned, "读不出编号不该影响这一轮回答");
        assertTrue(collected.isEmpty());
    }

    /**
     * 包装的起点是「商品工具能被解析成方法回调」。框架换包位或注解不生效时这里先红，
     * 而不是等到联调才发现模型手上没有检索工具。
     */
    @Test
    void resolvesProductToolCallback() {
        AiProductTools tools = new AiProductTools(mock(CatalogPortalService.class));

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();

        assertEquals(1, callbacks.length);
        assertEquals("searchProducts", callbacks[0].getToolDefinition().name());
    }
}
