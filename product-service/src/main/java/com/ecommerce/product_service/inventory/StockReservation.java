package com.ecommerce.product_service.inventory;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {
    @Id public String id;
    @Version public Long version;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public String state = "RESERVED";
    @ElementCollection
    @CollectionTable(name = "stock_reservation_items", joinColumns = @JoinColumn(name = "reservation_id"))
    @OrderColumn(name = "line_number")
    public List<ReservationLine> items = new ArrayList<>();
    protected StockReservation() {}
    public StockReservation(String id, Long userId) { this.id = id; this.userId = userId; }
}
