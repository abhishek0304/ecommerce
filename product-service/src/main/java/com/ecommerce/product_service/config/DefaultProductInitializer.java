package com.ecommerce.product_service.config;

import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.products.seed-enabled", havingValue = "true", matchIfMissing = true)
public class DefaultProductInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DefaultProductInitializer.class);
    private final ProductRepository products;

    public DefaultProductInitializer(ProductRepository products) {
        this.products = products;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (products.count() != 0) {
            log.info("Skipping default products: the products table already contains data");
            return;
        }
        List<Product> defaults = List.of(
                product("DEMO-MOUSE-001", "Wireless Mouse", "Compact wireless mouse with USB receiver.", "799.00", 50, "Electronics"),
                product("DEMO-KEYBOARD-001", "USB Keyboard", "Full-size keyboard with a USB connection.", "1299.00", 35, "Electronics"),
                product("DEMO-HEADPHONES-001", "Wireless Headphones", "Over-ear headphones with rechargeable battery.", "2499.00", 25, "Electronics"),
                product("DEMO-BACKPACK-001", "Laptop Backpack", "Everyday backpack with a padded laptop compartment.", "1499.00", 40, "Accessories"),
                product("DEMO-BOTTLE-001", "Stainless Steel Water Bottle", "Reusable 750 ml stainless steel bottle.", "599.00", 60, "Home"),
                product("DEMO-NOTEBOOK-001", "Ruled Notebook", "A5 ruled notebook for everyday notes.", "149.00", 100, "Stationery"));
        products.saveAllAndFlush(defaults);
        log.info("Inserted {} default products", defaults.size());
    }

    private Product product(String sku, String name, String description, String price, int stock, String category) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setCategory(category);
        product.setActive(true);
        return product;
    }
}
