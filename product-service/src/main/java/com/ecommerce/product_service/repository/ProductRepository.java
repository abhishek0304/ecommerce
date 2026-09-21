package com.ecommerce.product_service.repository;

import com.ecommerce.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Product> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
    java.util.Optional<Product> findLocked(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select p.version from Product p where p.id = :id")
    java.util.Optional<Long> findVersion(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
}
