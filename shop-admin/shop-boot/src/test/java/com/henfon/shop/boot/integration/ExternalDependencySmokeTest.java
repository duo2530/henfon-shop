package com.henfon.shop.boot.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 外部依赖集成冒烟测试。
 *
 * <p>测试默认跳过，避免普通单元测试依赖本机基础设施；在测试环境设置
 * {@code SHOP_IT_ENABLED=true} 后，会检查 MySQL、Redis、RocketMQ、MinIO
 * 以及可选支付回调地址是否可达。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
class ExternalDependencySmokeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /**
     * 检查测试 MySQL 端口可建立 TCP 连接。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldConnectToMySql() throws IOException {
        assumeIntegrationEnabled();
        // 仅建立 TCP 连接，不执行写入，避免污染集成测试数据库。
        assertTcpReachable("MySQL", env("SHOP_TEST_MYSQL_HOST", "127.0.0.1"),
                intEnv("SHOP_TEST_MYSQL_PORT", 3306));
    }

    /**
     * 检查 Redis 服务并执行 PING 命令，验证服务不仅端口开放而且可以响应协议。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldPingRedis() throws IOException {
        assumeIntegrationEnabled();
        String host = env("SHOP_TEST_REDIS_HOST", "127.0.0.1");
        int port = intEnv("SHOP_TEST_REDIS_PORT", 6379);
        try (Socket socket = openSocket(host, port)) {
            // 使用 Redis 最小 RESP 请求验证应用层响应，而不是仅验证端口开放。
            OutputStream output = socket.getOutputStream();
            output.write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String text = readLine(socket);
            assertTrue(text.startsWith("+PONG") || text.startsWith("-NOAUTH"),
                    "Redis 未返回预期 PING 响应: " + text);
        }
    }

    /**
     * 检查 RocketMQ NameServer 端口可达。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldReachRocketMqNameServer() throws IOException {
        assumeIntegrationEnabled();
        String nameServer = env("SHOP_TEST_ROCKETMQ_NAME_SERVER", "127.0.0.1:9876");
        String[] address = nameServer.split(":", 2);
        // NameServer 目前按 host:port 单地址配置，和 application-test.yml 保持一致。
        assertTcpReachable("RocketMQ NameServer", address[0],
                address.length == 2 ? Integer.parseInt(address[1]) : 9876);
    }

    /**
     * 检查 MinIO 健康端点，确保对象存储已经完成启动。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldReachMinioHealthEndpoint() throws IOException, InterruptedException {
        assumeIntegrationEnabled();
        String endpoint = env("SHOP_TEST_MINIO_ENDPOINT", "http://127.0.0.1:9000");
        // MinIO 健康接口不需要凭据，适合在启动探针阶段执行。
        URI healthUri = URI.create(endpoint.replaceAll("/+$", "") + "/minio/health/live");
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(healthUri).timeout(TIMEOUT).GET().build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                "MinIO 健康检查失败，HTTP 状态: " + response.statusCode());
    }

    /**
     * 对配置的支付回调地址执行无副作用 HEAD 检查。
     *
     * <p>支付回调地址未配置时跳过，避免向真实商户接口发送测试请求；配置后只验证
     * 地址可解析且服务未返回 5xx，不会提交订单或触发记账。</p>
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldReachPaymentCallbackEndpointWhenConfigured() throws IOException {
        assumeIntegrationEnabled();
        String callback = System.getProperty("shop.it.payment-callback-url",
                System.getenv("SHOP_TEST_PAYMENT_CALLBACK_URL"));
        assumeTrue(callback != null && !callback.isBlank(), "未配置支付回调地址，跳过检查");
        // HEAD 请求只验证路由可达，不携带支付通知载荷，避免触发业务记账。
        HttpURLConnection connection = (HttpURLConnection) URI.create(callback).toURL().openConnection();
        connection.setRequestMethod("HEAD");
        connection.setConnectTimeout((int) TIMEOUT.toMillis());
        connection.setReadTimeout((int) TIMEOUT.toMillis());
        int status = connection.getResponseCode();
        assertTrue(status < 500, "支付回调地址返回服务端错误: " + status);
        connection.disconnect();
    }

    /**
     * 判断集成测试开关，支持系统属性覆盖环境变量。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    private void assumeIntegrationEnabled() {
        String enabled = System.getProperty("shop.it.enabled", System.getenv("SHOP_IT_ENABLED"));
        // 默认跳过外部依赖测试，保证 mvn test 在无基础设施环境中仍可运行。
        assumeTrue("true".equalsIgnoreCase(enabled), "未开启外部依赖集成测试（SHOP_IT_ENABLED=true）");
    }

    /**
     * 建立带超时的 TCP 连接并断言连接成功。
     *
     * @param name 依赖名称
     * @param host 主机
     * @param port 端口
     * @author Henfon
     * @date 2026-08-31
     */
    private void assertTcpReachable(String name, String host, int port) throws IOException {
        // try-with-resources 确保探测结束立即释放连接，避免测试批量运行时耗尽端口。
        try (Socket ignored = openSocket(host, port)) {
            assertTrue(ignored.isConnected(), name + " TCP 连接未建立");
        }
    }

    /**
     * 打开 TCP 套接字并配置读写超时。
     *
     * @param host 主机
     * @param port 端口
     * @return 已连接套接字
     * @throws IOException 连接失败
     * @author Henfon
     * @date 2026-08-31
     */
    private Socket openSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        // 连接和读取均设置短超时，避免集成环境异常时阻塞整个测试流水线。
        socket.connect(new InetSocketAddress(host, port), (int) TIMEOUT.toMillis());
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        return socket;
    }

    /**
     * 从 Redis 套接字读取一行 RESP 响应，避免等待服务端主动关闭连接。
     *
     * @param socket Redis 套接字
     * @return RESP 首行文本
     * @throws IOException 读取失败
     * @author Henfon
     * @date 2026-08-31
     */
    private String readLine(Socket socket) throws IOException {
        StringBuilder response = new StringBuilder();
        int value;
        // Redis 会保持连接，按换行符读取首行而不能等待 EOF。
        while ((value = socket.getInputStream().read()) >= 0) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                response.append((char) value);
            }
        }
        return response.toString();
    }

    /**
     * 读取环境变量，不存在或为空时使用默认值。
     *
     * @param key 环境变量名
     * @param defaultValue 默认值
     * @return 配置值
     * @author Henfon
     * @date 2026-08-31
     */
    private String env(String key, String defaultValue) {
        // 支持 -Dshop.test.* 系统属性覆盖环境变量，便于 CI 注入临时服务地址。
        String value = System.getProperty(key.toLowerCase().replace('_', '.'), System.getenv(key));
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 读取整数环境变量，不合法时快速失败并提示配置错误。
     *
     * @param key 环境变量名
     * @param defaultValue 默认端口
     * @return 端口值
     * @author Henfon
     * @date 2026-08-31
     */
    private int intEnv(String key, int defaultValue) {
        String value = env(key, String.valueOf(defaultValue));
        // 端口配置错误应直接失败，避免把配置问题误判成服务不可达。
        return Integer.parseInt(value);
    }
}
