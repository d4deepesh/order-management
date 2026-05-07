package com.example.ordermgmt.repository;

import com.example.ordermgmt.entity.Order;
import com.example.ordermgmt.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ORDER REPOSITORY
 *
 * Concept: Data Access Layer --Only talks to database
 *
 * INTERVIEW POINTS
 *
 * JpaRepository<Entity, PrimaryKeyType> provides:
 *   save(entity)         --> INSERT or UPDATE
 *   findById(id)         --> SELECT by PK, returns Optional<T>
 *   findAll()            --> SELECT all
 *   findAll(pageable)    --> SELECT with LIMIT/OFFSET + COUNT(*)
 *   deleteById(id)       --> DELETE by PK
 *   existsById(id)       --> SELECT COUNT(*) > 0
 *   count()              --> SELECT COUNT(*)
 *
 * @Repository is OPTIONAL on JpaRepository interfaces
 * Spring Data auto-detects and creates proxy implementation.
 * @Repository adds exception translation (wraps SQL exceptions
 * into Spring DataAccessException hierarchy).
 *
 * METHOD NAMING CONVENTION -- Spring Data derives SQL:
 *   findBy{Field}                --> WHERE field = ?
 *   findBy{Field}And{Field}      --> WHERE f1 = ? AND f2 = ?
 *   findBy{Field}In              --> WHERE field IN (?)
 *   findBy{Field}Like            --> WHERE field LIKE ?
 *   findBy{Field}GreaterThan     --> WHERE field > ?
 *   findBy{Field}Between         --> WHERE field BETWEEN ? AND ?
 *   countBy{Field}               --> SELECT COUNT(*) WHERE field = ?
 *   existsBy{Field}              --> SELECT COUNT(*) > 0 WHERE field = ?
 *   deleteBy{Field}              --> DELETE WHERE field = ?
 *
 * Page<T> vs Slice<T>:
 *   Page  --> 2 queries: data + COUNT(*) -- has totalElements
 *   Slice --> 1 query: data only         -- only hasNext()
 *   Use Slice for infinite scroll (no total count needed = faster)
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ---- Method name Derived Queries

    // Spring Data JPA reads the method name and automatically builds SQL like:
    // SELECT * FROM orders WHERE status = ?
    // + COUNT(*) FROM order WHERE status = ? (for Page)
    Page<Order> findByStatus (OrderStatus status, Pageable pageable);

    // SELECT * FROM orders WHERE customer_email = ?
    Page<Order> findByCustomerEmail(String customerEmail, Pageable pageable);

    // SELECT * FROM orders WHERE status = ? AND customer_email = ?
    Page<Order> findByStatusAndCustomerEmail(
            OrderStatus status, String customerEmail, Pageable pageable);

    // SELECT COUNT(*) FROM orders WHERE customer_email = ?
    long countByCustomerEmail(String customerEmail);

    // SELECT * FROM orders WHERE customer_email=? ORDER BY created_at DESC
    List<Order> findByCustomerEmailOrderByCreatedAtDesc();

    // SELECT count > 0 FROM orders WHERE item = ? AND status = ?
    boolean existsByItemAndStatus(String item, OrderStatus status);


    // ---- Custom JPQL Queries ----

    /**
     * JPQL (Java Persistence Query Language)
     * Operates on ENTITY names & FIELD names (not table/column names)
     * o.price refers to Order.price field, not the DB column
     */

    @Query("SELECT o FROM Order o " +
            "WHERE o.price > :minPrice " +
            "AND o.status = :status " +
            "ORDER BY o.price DESC")
    List<Order> findExpensiveOrderByStatus(
            @Param("minPrice") Double minPrice,
            @Param("status") OrderStatus status);

    /**
     * Paginated JPQL with multiple conditions
     * countQuery is REQUIRED when using JOIN FETCH with pagination
     * without it, Hibernate does in-memory pagination (dangerous)
     */
    @Query(
            value = "SELECT o FROM Order o " +
                    "WHERE (:status IS NULL OR o.status = :status) " +
                    "AND (:email IS NULL OR o.customerEmail = :email)",
            countQuery = "SELECT COUNT(o) FROM Order o " +
                    "WHERE (:status IS NULL OR o.status = :status) " +
                    "AND (:email IS NULL OR o.customerEmail = :email)"
    )
    Page<Order> findByFilters(
            @Param("status") OrderStatus status,
            @Param("email") String email,
            Pageable pageable);

    // ---- Native SQL Query ----
    /**
     * nativeQuery = true: raw SQL (uses table/column names not entity names)
     * Use only when JPQL cannot express the query
     * (e.g., DB-specific functions, complex joins)
     */
    @Query(
            value = "SELECT * FROM orders " +
                    "WHERE created_at >= :fromDate " +
                    "ORDER BY created_at DESC",
            nativeQuery = true
    )
    List<Order> findOrdersCreatedAfter(
            @Param("fromDate") LocalDateTime fromDate);


    // ---- Modifying Query (UPDATE/DELETE) ----

    /**
     * @Modifying: required for UPDATE/DELETE queries
     * @Transactional: modifying queries must run in a transaction
     * clearAutomatically: clears persistence context after update
     * (avoids stale entity cache issues)
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = :status " +
            "WHERE o.id = :id")
    int updateOrderStatus(
            @Param("id") Long id,
            @Param("status") OrderStatus status);
}
