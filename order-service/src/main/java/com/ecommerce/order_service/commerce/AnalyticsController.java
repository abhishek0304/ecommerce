package com.ecommerce.order_service.commerce;
import com.ecommerce.order_service.model.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/api/admin/analytics") @PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {
    private final OrderRepository orders;private final ObjectMapper json;
    public AnalyticsController(OrderRepository orders,ObjectMapper json){this.orders=orders;this.json=json;}
    public record Popular(Long productId,String name,long units,BigDecimal merchandiseValue){}
    public record Report(int days,long orderCount,BigDecimal bookedSales,BigDecimal collectedRevenue,BigDecimal refunds,long cancelledOrders,double cancellationRate,Map<String,Long> statuses,List<Popular> popular,List<Map<String,Object>> daily){}
    @GetMapping @Transactional(readOnly=true)
    public Report report(@RequestParam(defaultValue="30") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(365) int days){
        var page=orders.findByCreatedAtGreaterThanEqual(Instant.now().minus(days,ChronoUnit.DAYS),PageRequest.of(0,10000));
        if(page.getTotalElements()>10000)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"Narrow the reporting period to at most 10000 orders");
        BigDecimal booked=BigDecimal.ZERO,collected=BigDecimal.ZERO,refunds=BigDecimal.ZERO;long cancelled=0;
        Map<String,Long> statuses=new TreeMap<>();Map<Long,Popular> popular=new HashMap<>();Map<String,BigDecimal> daily=new TreeMap<>();
        for(var o:page){statuses.merge(o.status,1L,Long::sum);if(Set.of("CANCELLED","EXPIRED","FAILED").contains(o.status))cancelled++;
            BigDecimal total=o.total==null?BigDecimal.ZERO:o.total;
            if("REFUNDED".equals(o.paymentStatus))refunds=refunds.add(total);
            if(Set.of("PAID","COLLECTED","REFUND_PENDING","REFUND_FAILED").contains(o.paymentStatus))collected=collected.add(total);
            if(!Set.of("CONFIRMED","PROCESSING","SHIPPED","DELIVERED").contains(o.status))continue;
            booked=booked.add(total);daily.merge(o.createdAt.atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString(),total,BigDecimal::add);
            if(o.itemsJson!=null)try{for(var item:json.readTree(o.itemsJson)){Long id=item.path("productId").asLong();var old=popular.getOrDefault(id,new Popular(id,item.path("name").asText(),0,BigDecimal.ZERO));long units=item.path("quantity").asLong();popular.put(id,new Popular(id,old.name(),old.units()+units,old.merchandiseValue().add(item.path("price").decimalValue().multiply(BigDecimal.valueOf(units)))));}}catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
        long count=page.getTotalElements();List<Map<String,Object>> dailyRows=new ArrayList<>();daily.forEach((date,total)->dailyRows.add(Map.of("date",date,"sales",total)));
        return new Report(days,count,booked,collected,refunds,cancelled,count==0?0:100.0*cancelled/count,statuses,popular.values().stream().sorted(Comparator.comparingLong(Popular::units).reversed()).limit(10).toList(),dailyRows);
    }
}
