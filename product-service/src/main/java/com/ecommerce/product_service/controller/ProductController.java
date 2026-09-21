package com.ecommerce.product_service.controller;

import com.ecommerce.product_service.dto.ProductRequest;
import com.ecommerce.product_service.dto.ProductResponse;
import com.ecommerce.product_service.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final com.ecommerce.product_service.service.ProductSearch search;

    public ProductController(ProductService productService, com.ecommerce.product_service.service.ProductSearch search) {
        this.productService = productService;
        this.search = search;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(product.id())
                .toUri();
        return ResponseEntity.created(location).body(product);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String category,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.math.BigDecimal minPrice,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.math.BigDecimal maxPrice,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean inStock,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(search.search(query, category, minPrice, maxPrice, inStock, pageable, false));
    }

    @GetMapping("/admin/catalog")
    public Page<ProductResponse> adminCatalog(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return search.search(null, null, null, null, null, pageable, true);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id, org.springframework.security.core.Authentication auth) {
        ProductResponse product = productService.getProduct(id);
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!product.active() && !admin) throw new com.ecommerce.product_service.exception.ProductNotFoundException(id);
        return ResponseEntity.ok(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
