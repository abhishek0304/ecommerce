package com.ecommerce.product_service.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VariantRepository extends JpaRepository<ProductVariant,Long> {
    List<ProductVariant> findByParentIdOrderById(Long parentId);
    Optional<ProductVariant> findByProductId(Long productId);
    boolean existsByParentId(Long parentId);
}
