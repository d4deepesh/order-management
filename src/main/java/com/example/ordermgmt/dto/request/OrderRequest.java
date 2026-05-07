package com.example.ordermgmt.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ORDER REQUEST DTO
 *
 * CONCEPT: What the CLIENT SENDS to the server
 *
 * DTO = Data Transfer Object
 * - Decouples API contract from internal Entity
 * - Client never sees internal fields (id, createdAt, status)
 * - Validation annotations live HERE, not on Entity
 *
 * INTERVIEW POINTS:
 *
 * @NotNull -- value must not be null (works on any type)
 * @NotBlank -- String must not be null/empty/whitespace
 * @NotEmpty -- String/Collection not null and not empty (allows whitespace)
 * @Min -- number >= value
 * @Max -- number <= value
 * @Positive -- number > 0
 * @Email -- must be valid email format
 * @Pattern - must match regex
 * @Size -- String length or Collection size within min/max
 *
 *  @Valid on @RequestBody triggers validation BEFORE controller method runs
 *  Failure throws MethodArgumentNotValidException --> 400 Bad Request
 *
 * @Data generates:
 *
 * getters
 * setters
 * toString()
 * equals()
 * hashCode()
 * required constructor
 *
 * For DTOs, this is usually fine because DTOs are:
 *
 * simple data carriers
 * not managed by JPA/Hibernate
 * not part of persistence identity logic
 */

@Data  // safe in DTOs but not for Entity class
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotBlank(message = "Item is required and cannot be blank")
    @Size(min=2, max=200, message = "item must be between 2 and 200 characters")
    private String item;

    @NotNull(message = "qty is required")
    @Min(value = 1, message = "qty must be at least 1")
    @Max(value = 10000, message = "qty cannot exceed 10000")
    private Integer qty;

    @NotNull(message = "price is required")
    @Positive(message = "price must be a positive number")
    @DecimalMin(value = "0.01",
            message = "price must be at least 0.01")
    private Double price;

    @NotBlank(message = "customerEmail is required")
    @Email(message = "customerEmail must be a valid email address")
    private String customerEmail;

    // optional -- validated only if provided
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "customerPhone must be valid 10-digit Indian mobile number"
    )
    private String customerPhone;

    // optional fields
    private String deliveryAddress;

    @Size(max = 500, message = "remarks cannot exceed 500 characters")
    private String remarks;

}
