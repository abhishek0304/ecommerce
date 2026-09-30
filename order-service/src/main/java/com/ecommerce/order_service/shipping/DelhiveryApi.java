package com.ecommerce.order_service.shipping;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="shipping-delhivery",url="${shipping.delhivery.base-url:https://staging-express.delhivery.com}",configuration=com.ecommerce.order_service.client.ProviderFeignConfiguration.class)
public interface DelhiveryApi {
    @PostMapping(value="/api/cmu/create.json",consumes="application/x-www-form-urlencoded")
    JsonNode create(@RequestHeader("Authorization") String auth,@RequestBody String encodedForm);
    @PostMapping("/fm/request/new/") JsonNode pickup(@RequestHeader("Authorization") String auth,@RequestBody java.util.Map<String,Object> body);
    @GetMapping("/api/v1/packages/json/") JsonNode track(@RequestHeader("Authorization") String auth,@RequestParam("waybill") String awb);
    @GetMapping("/api/p/packing_slip") JsonNode label(@RequestHeader("Authorization") String auth,@RequestParam("wbns") String awb,@RequestParam("pdf") String pdf);
}
