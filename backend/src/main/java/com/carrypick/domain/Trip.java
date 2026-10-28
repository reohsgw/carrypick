package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User traveler;

    @Column(nullable = false, length = 2)
    private String countryCode;

    private String city;

    @Column(nullable = false)
    private LocalDate departDate;

    @Column(nullable = false)
    private LocalDate returnDate;

    @Column(length = 500)
    private String luggageNote;

    private Instant createdAt = Instant.now();

    protected Trip() {}

    public Trip(User traveler, String countryCode, String city, LocalDate departDate, LocalDate returnDate, String luggageNote) {
        this.traveler = traveler;
        this.countryCode = countryCode;
        this.city = city;
        this.departDate = departDate;
        this.returnDate = returnDate;
        this.luggageNote = luggageNote;
    }

    /** True while the trip has not ended yet. */
    public boolean isUpcomingOrOngoing(LocalDate today) {
        return !returnDate.isBefore(today);
    }

    public Long getId() { return id; }
    public User getTraveler() { return traveler; }
    public String getCountryCode() { return countryCode; }
    public String getCity() { return city; }
    public LocalDate getDepartDate() { return departDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public String getLuggageNote() { return luggageNote; }
    public Instant getCreatedAt() { return createdAt; }
}
