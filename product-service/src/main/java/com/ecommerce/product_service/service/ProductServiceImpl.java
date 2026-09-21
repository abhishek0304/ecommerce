package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.ProductRequest;
import com.ecommerce.product_service.dto.ProductResponse;
import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.exception.DuplicateResourceException;
import com.ecommerce.product_service.exception.ProductNotFoundException;
import com.ecommerce.product_service.repository.ProductRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {
	private final ProductRepository productRepository;
    private final com.ecommerce.product_service.config.ProductReadCache cache;
    private final com.ecommerce.product_service.inventory.ReservationRepository reservations;

	public ProductServiceImpl(ProductRepository productRepository, com.ecommerce.product_service.inventory.ReservationRepository reservations, com.ecommerce.product_service.config.ProductReadCache cache) {
        this.cache = cache;
		this.productRepository = productRepository;
        this.reservations = reservations;
	}

	@Override
	@Transactional
	public ProductResponse createProduct(ProductRequest request) {
		String sku = normalizedSku(request.sku());
		if (productRepository.existsBySku(sku))
			throw new DuplicateResourceException("A product with sku '" + sku + "' already exists");
		Product product = new Product();
		apply(request, product, sku);
		return toResponse(productRepository.save(product));
	}

	@Override
	public Page<ProductResponse> getProducts(Pageable pageable) {
		return productRepository.findAll(pageable).map(this::toResponse);
	}

	@Override
	public ProductResponse getProduct(Long id) {
		Long version = productRepository.findVersion(id).orElseThrow(() -> new ProductNotFoundException(id));
        return cache.get(id, version, () -> toResponse(findById(id)));
	}

	@Override
	@Transactional
	public ProductResponse updateProduct(Long id, ProductRequest request) {
		Product product = findById(id);
		String sku = normalizedSku(request.sku());
		if (productRepository.existsBySkuAndIdNot(sku, id))
			throw new DuplicateResourceException("A product with sku '" + sku + "' already exists");
		apply(request, product, sku);
		return toResponse(productRepository.save(product));
	}

	@Override
	@Transactional
	public void deleteProduct(Long id) {
		var product = productRepository.findLocked(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (reservations.hasReservation(id)) throw new DuplicateResourceException("Product has reserved or fulfilled stock and cannot be deleted; hide it instead so returns can be restocked");
        productRepository.delete(product);
	}

	private Product findById(Long id) {
		return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
	}

	private String normalizedSku(String sku) {
		return sku.trim().toUpperCase(Locale.ROOT);
	}

	private void apply(ProductRequest r, Product p, String sku) {
		p.setSku(sku);
		p.setName(r.name().trim());
		p.setDescription(r.description());
		p.setPrice(r.price());
		p.setStockQuantity(r.stockQuantity());
		p.setCategory(r.category());
        p.setImageUrl(r.imageUrl());
		p.setActive(r.active() == null || r.active());
	}

	private ProductResponse toResponse(Product p) {
		return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
				p.getStockQuantity(), p.getCategory(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt(), p.getImageUrl());
	}
}
