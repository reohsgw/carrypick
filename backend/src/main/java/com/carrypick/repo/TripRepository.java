package com.carrypick.repo;

import com.carrypick.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByTravelerIdOrderByDepartDateAsc(Long travelerId);

    /** Trips of this traveler that have not ended yet. */
    List<Trip> findByTravelerIdAndReturnDateGreaterThanEqualOrderByDepartDateAsc(Long travelerId, LocalDate today);
}
