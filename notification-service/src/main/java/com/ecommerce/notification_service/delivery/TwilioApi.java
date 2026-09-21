package com.ecommerce.notification_service.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "twilio", url = "${messages.twilio.base-url:https://api.twilio.com}",
             configuration = ProviderFeignConfiguration.class)
public interface TwilioApi {
    @PostMapping(value = "/2010-04-01/Accounts/{id}/Messages.json", consumes = "application/x-www-form-urlencoded")
    JsonNode send(@PathVariable("id") String account, @RequestHeader("Authorization") String authorization,
                  @RequestBody Map<String, String> form);
}
