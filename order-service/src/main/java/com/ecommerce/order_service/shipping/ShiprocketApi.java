package com.ecommerce.order_service.shipping;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="shipping-shiprocket",url="${shipping.shiprocket.base-url:https://apiv2.shiprocket.in/v1/external}",configuration=com.ecommerce.order_service.client.ProviderFeignConfiguration.class)
public interface ShiprocketApi {
    @PostMapping("/orders/create/adhoc") JsonNode create(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body);
    @PostMapping("/orders/create/return") JsonNode returns(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body);
    @PostMapping("/courier/assign/awb") JsonNode assign(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body);
    @PostMapping("/courier/generate/label") JsonNode label(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body);
    @PostMapping("/courier/generate/pickup") JsonNode pickup(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body);
    @GetMapping("/courier/track/awb/{awb}") JsonNode track(@RequestHeader("Authorization") String auth,@PathVariable("awb") String awb);
}
