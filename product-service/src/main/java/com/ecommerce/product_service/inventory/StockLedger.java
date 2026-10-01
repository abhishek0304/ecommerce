package com.ecommerce.product_service.inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class StockLedger {
    private final MovementRepository movements;
    public StockLedger(MovementRepository movements) { this.movements=movements; }
    @Transactional
    public void record(Long productId,int delta,int resultingQuantity,String reason,String reference) {
        if(delta==0) return;
        StockMovement m=new StockMovement(); m.productId=productId; m.delta=delta; m.resultingQuantity=resultingQuantity;
        m.reason=reason; m.reference=reference; movements.save(m);
    }
}
