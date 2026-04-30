package com.ricestoremanagement.repository;

import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
	List<Order> findByShipperIdOrderByIdDesc(Long shipperId);

	List<Order> findByShipperIdAndStatusOrderByIdDesc(Long shipperId, OrderStatus status);
	List<Order> findByStatusOrderByIdDesc(OrderStatus status);
}
