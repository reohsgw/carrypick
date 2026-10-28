package com.carrypick.repo;

import com.carrypick.domain.Enums;
import com.carrypick.domain.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RequestRepository extends JpaRepository<PurchaseRequest, Long> {

    List<PurchaseRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<PurchaseRequest> findByStatusAndCountryCodeInOrderByDeadlineAsc(Enums.RequestStatus status, Collection<String> countries);

    /** Keyword search on open requests. Country filtering is done in the service. */
    @Query("""
            select r from PurchaseRequest r
            where r.status = :status
              and lower(r.productName) like lower(concat('%', :q, '%'))
            order by r.createdAt desc
            """)
    List<PurchaseRequest> search(@Param("q") String q, @Param("status") Enums.RequestStatus status);

    /** Expire open requests whose deadline has passed. */
    @Modifying
    @Query("""
            update PurchaseRequest r set r.status = :expired
            where r.status = :open and r.deadline < :today
            """)
    int expireOld(@Param("today") LocalDate today,
                  @Param("open") Enums.RequestStatus open,
                  @Param("expired") Enums.RequestStatus expired);
}
