package com.ecommerce.cart_service.controller;

import com.ecommerce.cart_service.dto.CartRequests.*;
import com.ecommerce.cart_service.dto.CartResponse;
import com.ecommerce.cart_service.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
	private final CartService service;

	public CartController(CartService service) {
		this.service = service;
	}

	private Long userId(Authentication auth) {
		return (Long) auth.getPrincipal();
	}

	@GetMapping
	public CartResponse get(Authentication auth) {
		return service.get(userId(auth));
	}

    public record MergeRequest(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max=100) java.util.Map<@jakarta.validation.constraints.NotNull @Positive Long,@jakarta.validation.constraints.NotNull @Positive Integer> items){}
    @PostMapping("/merge")
    public CartResponse merge(Authentication auth,@RequestHeader("Idempotency-Key") @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{8,128}") String key,@Valid @RequestBody MergeRequest request){return service.merge(userId(auth),key,request.items());}

	@PostMapping("/items")
	public CartResponse add(Authentication auth, @Valid @RequestBody AddItem request) {
		return service.add(userId(auth), request.productId(), request.quantity());
	}

	@PutMapping("/items/{productId}")
	public CartResponse update(Authentication auth, @PathVariable @Positive Long productId,
			@Valid @RequestBody UpdateQuantity request) {
		return service.update(userId(auth), productId, request.quantity());
	}

	@DeleteMapping("/items/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(Authentication auth, @PathVariable @Positive Long productId) {
		service.remove(userId(auth), productId);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clear(Authentication auth) {
		service.clear(userId(auth));
	}
}
