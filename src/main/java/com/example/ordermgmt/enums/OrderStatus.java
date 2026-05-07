package com.example.ordermgmt.enums;

/**
 * OrderStatus Enum
 *
 * Interview Point:
 * Enums stored as STRING in DB via @Enumrated(EnumType.String)
 * Never use EnumType.ORDINAL -- adding new values breaks DB integrity
 * EnumType.ORDINAL in Jakarta Persistence / JPA stores enums as their numeric position (0, 1, 2, …) instead of their name.
 *
 *
 * State machine:
 *  * PENDING --> CONFIRMED --> SHIPPED --> DELIVERED
 *  *    |             |
 *  *    v             v
 *  * CANCELLED    CANCELLED
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED
}
