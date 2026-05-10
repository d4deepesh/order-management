package com.example.ordermgmt.mapper;

import com.example.ordermgmt.dto.request.OrderRequest;
import com.example.ordermgmt.dto.response.OrderResponse;
import com.example.ordermgmt.entity.Order;
import com.example.ordermgmt.enums.OrderStatus;
import org.springframework.stereotype.Component;

/**
 * ORDER MAPPER
 * A mapper is simply a piece of code that converts one object type into another.
 *
 * CONCEPT: Converts between Entity <--> DTO
 *
 * In a typical Spring Boot application, it usually converts between:
 * Entity → DTO
 * DTO → Entity
 *
 * Example idea:
 *
 * Order (Entity)  →  OrderResponse
 * OrderRequest →  Order (Entity)
 *
 * INTERVIEW POINTS:
 *
 * Why mapper is needed:
 * - Entity has JPA annotations, DB specific fields, lazy relations
 * - DTO has validation annotations, client-facing fields only
 * - Direct Entity --> client exposes internal structure (security risk)
 * - Direct Client --> Entity bypasses audit/business fields
 *
 * Production alternatives: Best Practice
 * - MapStruct: generates type-safe mapping code at COMPILE TIME
 *   @Mapper(componentModel = "spring") -- zero runtime overhead
 *   Best choice for production projects
 *
 * - ModelMapper: reflection-based runtime mapping
 *   Simpler setup, slower, less type-safe
 *
 * - Manual mapping (this class): full control, verbose but clear
 *   Good for learning — clearly shows source (DTO fields) → target (Entity fields) mapping
 *
 * RULE: Entity NEVER crosses layer boundaries to client
 *       DTO NEVER crosses layer boundaries to DB
 */

@Component
public class OrderMapper {
    /**
     * Convert client request DTO --> Entity
     * Called in service BEFORE saving to DB
     * Note: id, status, createdAt are not set here.
     * They are set by service logic and @PrePersist
     */

    public Order toEntity(OrderRequest request) {
        if (request == null) return null;

        return Order.builder()
                .item(request.getItem())
                .qty(request.getQty())
                .price(request.getPrice())
                .customerEmail(request.getCustomerEmail())
                .customerPhone(request.getCustomerPhone())
                .deliveryAddress(request.getDeliveryAddress())
                .remarks(request.getRemarks())
                // status set by service, not from client
                .status(OrderStatus.PENDING)
                .build();
    }

    /**
     * Convert Entity --> response DTO
     * Called in service AFTER DB operation
     * Controls EXACTLY what the client sees
     */

    public OrderResponse toResponse(Order order) {
        if (order == null) return null;

        return OrderResponse.builder()
                .id(order.getId())
                .item(order.getItem())
                .qty(order.getQty())
                .price(order.getPrice())
                // computed field: qty * price
                .totalValue(order.getTotalValue())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                // convert enum to string for clean JSON/XML
                .status(order.getStatus() != null
                        ? order.getStatus().name() : null)
                .deliveryAddress(order.getDeliveryAddress())
                .remarks(order.getRemarks())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Apply PUT update (full replace) from DTO to existing Entity
     * Called in service for PUT /orders/{id}
     * Sets all fields -- missing fields become null (full replace semantics)
     */
    public void updateEntityFromRequest(Order order,
                                        OrderRequest request) {
        order.setItem(request.getItem());
        order.setQty(request.getQty());
        order.setPrice(request.getPrice());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setRemarks(request.getRemarks());
        // updatedAt set by @PreUpdate in entity
    }

}
