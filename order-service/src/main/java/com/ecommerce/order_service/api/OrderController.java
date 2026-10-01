package com.ecommerce.order_service.api;
import com.ecommerce.order_service.api.OrderDtos.*;
import com.ecommerce.order_service.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
	private final OrderService service;

	public OrderController(OrderService service) {
		this.service = service;
	}

	private Long user(Authentication auth) {
		return (Long) auth.getPrincipal();
	}

	@PostMapping
	public ResponseEntity<OrderView> create(Authentication auth, @RequestHeader("Authorization") String bearer,
			@RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,128}") String key,
			@Valid @RequestBody Checkout request) {
		return response(service.create(user(auth), key, request, bearer));
	}

	@GetMapping
	public Page<OrderView> list(Authentication auth, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return service.list(user(auth), page, size);
	}

	@GetMapping("/{id}")
	public OrderView get(Authentication auth, @PathVariable UUID id) {
		return service.get(id.toString(), user(auth));
	}

    @GetMapping("/attempts/{key}")
    public OrderView attempt(Authentication auth,@PathVariable @Pattern(regexp="[A-Za-z0-9_-]{8,128}") String key){return service.attempt(user(auth),key);}

	@PostMapping("/{id}/cancel")
	public ResponseEntity<OrderView> cancel(Authentication auth, @PathVariable UUID id) {
		return response(service.cancel(id.toString(), user(auth)));
	}

	@PostMapping("/{id}/payments/verify")
	public OrderView verify(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Verify request) {
		return service.verify(id.toString(), user(auth), request);
	}

	private ResponseEntity<OrderView> response(OrderView order) {
		HttpStatus status = switch (order.status()) {
		case "CREATING", "RESERVED", "CANCELLING" -> HttpStatus.ACCEPTED;
		case "FAILED" -> HttpStatus.CONFLICT;
		default -> HttpStatus.OK;
		};
		return ResponseEntity.status(status).header("Location", "/api/orders/" + order.id()).body(order);
	}
}
