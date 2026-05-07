package com.example.ordermgmt.dto.response;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ORDER RESPONSE DTO
 *
 * CONCEPT: What the server RETURNS to the client
 *
 * INTERVIEW POINTS
 * - Contains only field client could see
 * - Hides internal fields (raw entity relations, sensitive data)
 * - @JacksonXmlRootElement -- required for XML serialization
 *  Without this, XML has no root element name --> invalid XML
 *
 *  CONTENT NEGOTIATION
 *  - Accept: application/json --> Jackson serializes to JSON
 *  - Accept: application/xml  --> Jackson XML serializes to XML
 * Same Java object -- different output format based on Accept header
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "order") // XML root element
public class OrderResponse {

    private Long id;
    private String item;
    private Integer qty;
    private Double price;
    private Double totalValue;          // computed: qty * price
    private String customerEmail;
    private String customerPhone;
    private String status;
    private String deliveryAddress;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
