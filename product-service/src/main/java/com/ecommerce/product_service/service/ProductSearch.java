package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.ProductResponse;
import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProductSearch {
    private final ProductRepository products;
    public ProductSearch(ProductRepository products) { this.products = products; }
    public Page<ProductResponse> search(String query, String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, Pageable page, boolean admin) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid price range");
        for (Sort.Order sort : page.getSort()) if (!Set.of("id", "name", "price", "createdAt", "stockQuantity").contains(sort.getProperty()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
        Pageable bounded = PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 100), page.getSort().and(Sort.by("id")));
        Specification<Product> spec = (root, q, cb) -> {
            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!admin) filters.add(cb.isTrue(root.get("active")));
            if (query != null && !query.isBlank()) {
                String term = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                filters.add(cb.like(cb.lower(root.get("name")), "%" + term + "%", '\\'));
            }
            if (category != null && !category.isBlank()) filters.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            if (minPrice != null) filters.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            if (maxPrice != null) filters.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            if (inStock != null) filters.add(inStock ? cb.greaterThan(root.get("stockQuantity"), 0) : cb.equal(root.get("stockQuantity"), 0));
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return products.findAll(spec, bounded).map(p -> new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(), p.getStockQuantity(), p.getCategory(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt(), p.getImageUrl()));
    }
}
