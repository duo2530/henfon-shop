package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.LoginCaptchaResponse;
import com.henfon.shop.identity.security.LoginCaptchaStore;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 管理端登录图形验证码服务。
 *
 * @author Henfon
 * @date 2026-09-18
 */
@Service
public class LoginCaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    /** 去掉 0/O/1/I/L 等易混字符，降低人工识别失败率。 */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoginCaptchaStore captchaStore;

    /**
     * 创建验证码服务。
     *
     * @param captchaStore 验证码存储
     * @author Henfon
     * @date 2026-09-18
     */
    public LoginCaptchaService(LoginCaptchaStore captchaStore) {
        this.captchaStore = captchaStore;
    }

    /**
     * 生成新的图形验证码，明文只保存在 Redis，响应仅返回图片。
     *
     * @return 验证码标识与图片
     * @author Henfon
     * @date 2026-09-18
     */
    public LoginCaptchaResponse create() {
        String code = randomCode();
        return new LoginCaptchaResponse(captchaStore.create(code), renderImage(code));
    }

    /**
     * 生成随机验证码明文。
     *
     * @return 验证码明文
     * @author Henfon
     * @date 2026-09-18
     */
    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * 使用 Java2D 绘制验证码图片并编码为 data URL。
     *
     * @param code 验证码明文
     * @return data URL 形式的 PNG 图片
     * @author Henfon
     * @date 2026-09-18
     */
    private String renderImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(0xF1F5F9));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            drawInterference(graphics);
            drawCharacters(graphics, code);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            // 图片编码失败属于运行环境异常，对调用方统一暴露为可重试的业务错误。
            throw new BusinessException("AUTH_CAPTCHA_UNAVAILABLE", "验证码生成失败，请稍后重试");
        }
    }

    /**
     * 叠加干扰线与噪点，抑制简单脚本的字符切分。
     *
     * @param graphics 画布
     * @author Henfon
     * @date 2026-09-18
     */
    private void drawInterference(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 5; i++) {
            graphics.setColor(randomColor(150, 210));
            graphics.drawLine(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
        }
        for (int i = 0; i < 40; i++) {
            graphics.setColor(randomColor(160, 215));
            graphics.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 1, 1);
        }
    }

    /**
     * 逐字符绘制验证码，每个字符带随机字号与轻微旋转。
     *
     * @param graphics 画布
     * @param code 验证码明文
     * @author Henfon
     * @date 2026-09-18
     */
    private void drawCharacters(Graphics2D graphics, String code) {
        int step = (WIDTH - 16) / CODE_LENGTH;
        for (int i = 0; i < CODE_LENGTH; i++) {
            graphics.setColor(randomColor(30, 110));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24 + RANDOM.nextInt(4)));
            double angle = (RANDOM.nextDouble() - 0.5) * 0.5;
            // 旋转围绕字符自身基线中点，避免字符被裁出画布。
            graphics.rotate(angle, 16 + i * step, HEIGHT / 2.0);
            graphics.drawString(String.valueOf(code.charAt(i)), 14 + i * step, 30);
            graphics.rotate(-angle, 16 + i * step, HEIGHT / 2.0);
        }
    }

    /**
     * 生成指定区间内的随机颜色。
     *
     * @param min 分量下界
     * @param max 分量上界
     * @return 随机颜色
     * @author Henfon
     * @date 2026-09-18
     */
    private Color randomColor(int min, int max) {
        int span = max - min;
        return new Color(min + RANDOM.nextInt(span), min + RANDOM.nextInt(span), min + RANDOM.nextInt(span));
    }
}
