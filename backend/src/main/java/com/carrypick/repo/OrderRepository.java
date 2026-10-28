package com.carrypick.repo;

import com.carrypick.domain.Enums;
import com.carrypick.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<Order> findByTravelerIdOrderByCreatedAtDesc(Long travelerId);

    Optional<Order> findFirstByRequestIdOrderByCreatedAtDesc(Long requestId);

    /** Rows of [productName, count] for the popular ranking. */
    @Query("""
            select o.productName, count(o) from CpOrder o
            where o.countryCode = :country and o.status in :statuses and o.createdAt >= :since
            group by o.productName
            order by count(o) desc, max(o.createdAt) desc
            """)
    List<Object[]> rankProducts(@Param("country") String country,
                                @Param("statuses") Collection<Enums.OrderStatus> statuses,
                                @Param("since") Instant since);
}
