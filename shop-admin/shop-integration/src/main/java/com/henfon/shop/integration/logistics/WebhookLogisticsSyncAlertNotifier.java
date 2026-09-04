package com.henfon.shop.integration.logistics;
import com.fasterxml.jackson.databind.ObjectMapper; import org.slf4j.*; import org.springframework.http.MediaType; import org.springframework.http.client.SimpleClientHttpRequestFactory; import org.springframework.stereotype.Component; import org.springframework.util.StringUtils; import org.springframework.web.client.RestClient; import java.util.*;
/** @author Henfon @date 2026-09-04 */
@Component
public class WebhookLogisticsSyncAlertNotifier implements LogisticsSyncAlertNotifier {
 private static final Logger LOGGER=LoggerFactory.getLogger(WebhookLogisticsSyncAlertNotifier.class); private final LogisticsSyncAlertProperties properties; private final ObjectMapper mapper; private final RestClient client;
 /** 创建通知器。 @author Henfon @date 2026-09-04 */
 public WebhookLogisticsSyncAlertNotifier(LogisticsSyncAlertProperties p,ObjectMapper m){properties=p;mapper=m;SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Math.max(p.getConnectTimeoutMs(),500));f.setReadTimeout(Math.max(p.getReadTimeoutMs(),500));client=RestClient.builder().requestFactory(f).build();}
 /** 发送告警。 @author Henfon @date 2026-09-04 */
 public void notifyMaxRetry(Long orderId,String orderNo,String company,String trackingNo,int attempts,String error){if(!properties.isEnabled()||!StringUtils.hasText(properties.getWebhookUrl()))return;try{Map<String,Object> x=new LinkedHashMap<>();x.put("event","LOGISTICS_SYNC_MAX_RETRY");x.put("orderId",orderId);x.put("orderNo",orderNo);x.put("logisticsCompany",company);x.put("trackingNo",trackingNo);x.put("attempts",attempts);x.put("errorMessage",error);client.post().uri(properties.getWebhookUrl()).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(x)).retrieve().toBodilessEntity();}catch(Exception e){LOGGER.warn("物流同步告警 Webhook 发送失败，orderId={}",orderId,e);}}
}
