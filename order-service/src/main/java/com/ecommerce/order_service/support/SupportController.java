package com.ecommerce.order_service.support;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ecommerce.order_service.model.OrderRepository;
import java.time.Instant;
import java.util.*;
@RestController
public class SupportController {
    private final TicketRepository tickets;private final OrderRepository orders;
    public SupportController(TicketRepository tickets,OrderRepository orders){this.tickets=tickets;this.orders=orders;}
    public record Create(@Pattern(regexp="[0-9a-fA-F-]{36}") String orderId,@NotBlank @Size(max=150) String subject,@NotBlank @Size(max=2000) String message){}
    public record Reply(@NotBlank @Size(max=2000) String message){}
    public record Status(@NotNull TicketStatus status){}
    public enum TicketStatus{OPEN,IN_PROGRESS,RESOLVED}
    public record View(String id,String orderId,String subject,String status,Instant createdAt,List<SupportTicket.Message> messages){}
    private View view(SupportTicket t){return new View(t.id,t.orderId,t.subject,t.status,t.createdAt,List.copyOf(t.messages));}
    private boolean admin(Authentication a){return a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"));}
    private SupportTicket owned(String id,Authentication a){var t=tickets.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ticket not found"));if(!t.userId.equals((Long)a.getPrincipal())&&!admin(a))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Ticket not found");return t;}
    @PostMapping("/api/support") @Transactional
    public View create(Authentication a,@Valid @RequestBody Create r){
        Long user=(Long)a.getPrincipal();
        if(r.orderId()!=null){var order=orders.findById(r.orderId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));if(!order.userId.equals(user))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");}
        SupportTicket t=new SupportTicket();t.userId=user;t.orderId=r.orderId();t.subject=r.subject().trim();t.messages.add(new SupportTicket.Message(r.message().trim(),false));return view(tickets.save(t));
    }
    @GetMapping("/api/support") @Transactional(readOnly=true)
    public Page<View> mine(Authentication a,@RequestParam(defaultValue="0") @Min(0) int page){return tickets.findByUserIdOrderByCreatedAtDesc((Long)a.getPrincipal(),PageRequest.of(page,20)).map(this::view);}
    @GetMapping("/api/admin/support") @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
    public Page<View> all(@RequestParam(defaultValue="0") @Min(0) int page){return tickets.findAll(PageRequest.of(page,20,Sort.by("createdAt").descending())).map(this::view);}
    @PostMapping("/api/support/{id}/messages") @Transactional
    public View reply(Authentication a,@PathVariable UUID id,@Valid @RequestBody Reply r){var t=owned(id.toString(),a);if(t.messages.size()>=100)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket message limit reached");t.messages.add(new SupportTicket.Message(r.message().trim(),admin(a)));if(!admin(a))t.status="OPEN";return view(t);}
    @PatchMapping("/api/admin/support/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public View status(Authentication a,@PathVariable UUID id,@Valid @RequestBody Status r){var t=owned(id.toString(),a);t.status=r.status().name();return view(t);}
}
