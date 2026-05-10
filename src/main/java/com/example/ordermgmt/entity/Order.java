package com.example.ordermgmt.entity;


import com.example.ordermgmt.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * ORDER ENTITY
 *
 * Concept: Entity = DB table mapping
 *
 * RULES:
 * - Entity is never returned directly to client (use DTO)
 * - Entity is NEVER sent directly by client (use DTO)
 * - JPA annotations live here, not in DTOs
 *
 * INTERVIEW POINTS:
 *
 * - @Entity marks as JPA-managed class
 * - @Table maps to specific DB table name
 * - @Id marks primary key
 * - @GeneratedValue -- DB auto-generates ID (IDENTITY = auto-increment)
 * - @Enumerated(STRING) -- stores "PENDING" not 0 in DB
 * - @Column(nullable = false) -- DB- level NOT NULL constraint
 *
 * Instead of @Data use @Getter, @Setter explicitly in case of Entity class
 * Using @Data on entities can cause problems like:
 *
 * recursive toString()
 * broken equals/hashCode
 * lazy loading triggered accidentally
 * Hibernate proxy issues
 */

@Entity
@Table(name="orders")
@Getter
@Setter
@AllArgsConstructor
@Builder

// PROTECTED: Hibernate uses reflection -- access level irrelevant to JPA
// Application code CANNOT do: new Order() -- must use Builder
// @NoArgsConstructor --> Required by JPA (a no-argument constructor. Without it → runtime error.)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString

// REQUIRED: tells Spring Data to apply auditing to this entity
// AuditingEntityListener intercepts JPA lifecycle events
// and populates @CreatedDate, @LastModifiedDate automatically
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // product name
    @Column(name="item", nullable = false, length = 200)
    private String item;

    @Column(name="qty", nullable = false)
    private Integer qty;

    // unit price
    @Column(name = "price", nullable = false)
    private Double price;

    // customer email
    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    // customer phone
    @Column(name = "customer_phone")
    private String customerPhone;

    // current order status
    // @Enumerated(STRING) stores "PENDING" text not integer index
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    // delivery address
    @Column(name = "delivery_address")
    private String deliveryAddress;

    // remarks or special instructions
    @Column(name = "remarks", length = 500)
    private String remarks;

    // AUDIT FIELDS -- Spring Data sets these automatically

    // set ONCE on INSERT, never updated
    // updatable = false --> Hibernate never includes in UPDATE SQL
    @CreatedDate
    @Column(name = "created_at",
            nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    // set on INSERT, updated on every UPDATE automatically
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // calculated field -- not stored in DB
    @Transient
    public Double getTotalValue() {
        if (qty == null || price == null) return 0.0;
        return qty * price;
    }
}
