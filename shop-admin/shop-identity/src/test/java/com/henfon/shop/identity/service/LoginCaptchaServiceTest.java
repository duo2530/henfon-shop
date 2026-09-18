package com.henfon.shop.identity.service;

import com.henfon.shop.identity.dto.LoginCaptchaResponse;
import com.henfon.shop.identity.security.LoginCaptchaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端登录图形验证码生成测试。
 *
 * @author Henfon
 * @date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
class LoginCaptchaServiceTest {

    private static final String DATA_URL_PREFIX = "data:image/png;base64,";

    @Mock
    private LoginCaptchaStore captchaStore;

    private LoginCaptchaService service;

    /**
     * 初始化验证码服务测试对象。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @BeforeEach
    void setUp() {
        service = new LoginCaptchaService(captchaStore);
    }

    /**
     * 验证生成四位非易混字符明文，并返回可解码的 PNG 图片。
     *
     * @throws Exception 图片解码异常
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldStoreFourCharacterCodeAndReturnDecodablePng() throws Exception {
        when(captchaStore.create(anyString())).thenReturn("captcha-id");

        LoginCaptchaResponse response = service.create();

        assertEquals("captcha-id", response.captchaId());
        assertTrue(response.imageBase64().startsWith(DATA_URL_PREFIX), "图片应为 data URL 形式");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(captchaStore).create(codeCaptor.capture());
        String code = codeCaptor.getValue();
        assertEquals(4, code.length());
        assertTrue(code.matches("[2-9A-HJKMNPQRSTUVWXYZ]{4}"), "验证码应只包含非易混字符：" + code);

        byte[] png = Base64.getDecoder().decode(response.imageBase64().substring(DATA_URL_PREFIX.length()));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "验证码图片应可解码");
        assertEquals(120, image.getWidth());
        assertEquals(40, image.getHeight());
    }
}
