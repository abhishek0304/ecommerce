package com.ecommerce.order_service.commerce;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class DeliveryService {
    private final DeliveryRepository rules;
    public DeliveryService(DeliveryRepository rules) {this.rules=rules;}
    public record Quote(BigDecimal fee,int minDays,int maxDays,String postalCode) {}
    @Transactional(readOnly=true)
    public Quote quote(String postalCode,BigDecimal subtotal) {
        // Historical persisted checkouts may predate postal-code capture. Public quotes validate it in the controller.
        String code=postalCode==null?"":postalCode.trim();
        var rule=rules.findAll().stream().filter(r->r.active&&code.startsWith(r.postalPrefix))
            .max(java.util.Comparator.comparingInt(r->r.postalPrefix.length())).orElse(null);
        if(rule==null) return new Quote(BigDecimal.ZERO.setScale(2),5,7,code);
        return new Quote(subtotal.compareTo(rule.freeAbove)>=0?BigDecimal.ZERO.setScale(2):rule.fee,rule.minDays,rule.maxDays,code);
    }
}
