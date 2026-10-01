package com.ecommerce.product_service.catalog;
import com.ecommerce.product_service.dto.*;
import com.ecommerce.product_service.service.ProductService;
import com.ecommerce.product_service.repository.ProductRepository;
import com.ecommerce.product_service.inventory.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/v1/products")
public class CatalogManagement {
    private final ProductService service; private final ProductRepository products; private final VariantRepository variants;
    private final MovementRepository movements; private final StockLedger ledger;
    public CatalogManagement(ProductService service,ProductRepository products,VariantRepository variants,MovementRepository movements,StockLedger ledger) {
        this.service=service;this.products=products;this.variants=variants;this.movements=movements;this.ledger=ledger;
    }
    public record VariantRequest(@NotBlank @Size(max=100) String label,@Size(max=50) String size,@Size(max=50) String color,@NotNull @Valid ProductRequest product) {}
    public record VariantView(Long id,Long parentId,String label,String size,String color,ProductResponse product) {}
    @GetMapping("/{id}/variants") @Transactional(readOnly=true)
    public List<VariantView> list(@PathVariable Long id) {
        Long parent=variants.findByProductId(id).map(v->v.parentId).orElse(id);
        if(!service.getProduct(parent).active()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");
        return variants.findByParentIdOrderById(parent).stream().map(v->new VariantView(v.id,v.parentId,v.label,v.size,v.color,service.getProduct(v.productId)))
            .filter(v->v.product().active()).toList();
    }
    @PostMapping("/admin/{id}/variants") @Transactional
    public VariantView create(@PathVariable Long id,@Valid @RequestBody VariantRequest request) {
        products.findLocked(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
        if(variants.findByProductId(id).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Create variants on the parent product");
        ProductResponse product=service.createProduct(request.product());
        ProductVariant v=new ProductVariant();v.parentId=id;v.productId=product.id();v.label=request.label().trim();v.size=request.size();v.color=request.color();variants.save(v);
        return new VariantView(v.id,id,v.label,v.size,v.color,product);
    }
    public record Adjustment(@NotNull @Min(-1000000) @Max(1000000) Integer delta,@NotBlank @Size(max=150) String reference) {}
    @PostMapping("/admin/{id}/stock") @Transactional
    public ProductResponse replenish(@PathVariable Long id,@Valid @RequestBody Adjustment request) {
        var p=products.findLocked(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
        long result=(long)p.getStockQuantity()+request.delta();
        if(result<0||result>1000000) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock must remain between 0 and 1000000");
        p.setStockQuantity((int)result); ledger.record(id,request.delta(),(int)result,"ADMIN_ADJUSTMENT",request.reference());products.flush();return service.getProduct(id);
    }
    @GetMapping("/admin/{id}/movements")
    public Page<StockMovement> history(@PathVariable Long id,@RequestParam(defaultValue="0") @Min(0) int page) { return movements.findByProductIdOrderByCreatedAtDesc(id,PageRequest.of(page,30)); }
    @GetMapping("/admin/low-stock")
    public Page<ProductResponse> lowStock(@RequestParam(defaultValue="5") @Min(0) @Max(1000000) int threshold,@RequestParam(defaultValue="0") @Min(0) int page) {
        return products.findAll((root,q,cb)->cb.and(cb.isTrue(root.get("active")),cb.lessThanOrEqualTo(root.get("stockQuantity"),threshold)),PageRequest.of(page,30,Sort.by("stockQuantity")))
            .map(p->service.getProduct(p.getId()));
    }
}
