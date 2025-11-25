package hu.porkolab.chaosSymphony.orderapi.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    
    List<Order> findByStatus(OrderStatus status);

    
    List<Order> findByCustomerId(String customerId);

    
    long countByStatus(OrderStatus status);

    
    @Query("SELECT o FROM Order o WHERE o.total > :threshold ORDER BY o.createdAt DESC")
    List<Order> findHighValueOrders(java.math.BigDecimal threshold);
}
