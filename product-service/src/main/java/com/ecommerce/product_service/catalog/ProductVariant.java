package com.ecommerce.product_service.catalog;
import jakarta.persistence.*;
@Entity @Table(name="product_variants", uniqueConstraints=@UniqueConstraint(columnNames={"product_id"}))
public class ProductVariant {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long parentId;
    @Column(nullable=false) public Long productId;
    @Column(nullable=false, length=100) public String label;
    @Column(length=50) public String size;
    @Column(length=50) public String color;
}
