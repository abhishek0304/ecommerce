package com.ecommerce.order_service.commerce;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class DeliveryController {
    private final DeliveryService service;private final DeliveryRepository rules;
    public DeliveryController(DeliveryService service,DeliveryRepository rules){this.service=service;this.rules=rules;}
    public record Request(@NotBlank @Size(max=20) String postalCode,@NotNull @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal subtotal) {}
    public record Rule(@NotBlank @Pattern(regexp="[0-9]{1,6}") String postalPrefix,@NotNull @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal fee,
        @NotNull @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal freeAbove,@Min(1) @Max(60) int minDays,@Min(1) @Max(90) int maxDays,boolean active) {}
    @PostMapping("/api/delivery/quote") public DeliveryService.Quote quote(@Valid @RequestBody Request request){return service.quote(request.postalCode(),request.subtotal());}
    @GetMapping("/api/admin/delivery") @PreAuthorize("hasRole('ADMIN')") public List<DeliveryRule> list(){return rules.findAll();}
    @PutMapping("/api/admin/delivery") @PreAuthorize("hasRole('ADMIN')")
    public DeliveryRule save(@Valid @RequestBody Rule request){
        if(request.maxDays()<request.minDays()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Maximum days must be at least minimum days");
        DeliveryRule r=new DeliveryRule();r.postalPrefix=request.postalPrefix();r.fee=request.fee();r.freeAbove=request.freeAbove();r.minDays=request.minDays();r.maxDays=request.maxDays();r.active=request.active();return rules.save(r);
    }
}
