package com.ecommerce.product_service;

import com.ecommerce.product_service.config.DefaultProductInitializer;
import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:productseed;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.products.seed-enabled=true"
})
@Transactional
class DefaultProductInitializerTests {
    @Autowired ProductRepository products;
    @Autowired DefaultProductInitializer initializer;

    @Test
    void startupSeedsAnEmptyDatabaseWithPurchasableProducts() {
        assertThat(products.findAll()).hasSize(6).allSatisfy(product -> {
            assertThat(product.getId()).isNotNull();
            assertThat(product.getSku()).startsWith("DEMO-");
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getPrice()).isPositive();
            assertThat(product.getStockQuantity()).isPositive();
            assertThat(product.isActive()).isTrue();
            assertThat(product.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void repeatedStartupDoesNotDuplicateOrOverwriteProducts() {
        Product edited = products.findAll().getFirst();
        edited.setName("User edited name");
        edited.setPrice(new BigDecimal("42.00"));
        edited.setStockQuantity(0);
        edited.setActive(false);
        products.saveAndFlush(edited);

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(products.count()).isEqualTo(6);
        Product persisted = products.findById(edited.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("User edited name");
        assertThat(persisted.getPrice()).isEqualByComparingTo("42.00");
        assertThat(persisted.getStockQuantity()).isZero();
        assertThat(persisted.isActive()).isFalse();
    }

    @Test
    void skipsSeedingWhenOnlyACustomProductExists() {
        products.deleteAllInBatch();
        Product custom = new Product();
        custom.setSku("CUSTOM-001");
        custom.setName("My product");
        custom.setPrice(new BigDecimal("10.00"));
        custom.setStockQuantity(2);
        products.saveAndFlush(custom);

        initializer.run(new DefaultApplicationArguments());

        assertThat(products.findAll()).hasSize(1).extracting(Product::getSku).containsExactly("CUSTOM-001");
    }
}
