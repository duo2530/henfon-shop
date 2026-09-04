package com.henfon.shop.integration.logistics;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** @author Henfon @date 2026-09-04 */
@ConfigurationProperties(prefix = "shop.integration.logistics.alert")
public class LogisticsSyncAlertProperties {
 private boolean enabled; private String webhookUrl; private int connectTimeoutMs=2000; private int readTimeoutMs=3000;
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
 public String getWebhookUrl(){return webhookUrl;} public void setWebhookUrl(String v){webhookUrl=v;}
 public int getConnectTimeoutMs(){return connectTimeoutMs;} public void setConnectTimeoutMs(int v){connectTimeoutMs=v;}
 public int getReadTimeoutMs(){return readTimeoutMs;} public void setReadTimeoutMs(int v){readTimeoutMs=v;}
}
