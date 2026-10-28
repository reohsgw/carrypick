package com.carrypick.service;

import com.carrypick.common.ApiException;
import com.carrypick.domain.*;
import com.carrypick.repo.OfferRepository;
import com.carrypick.repo.OrderRepository;
import com.carrypick.repo.RequestRepository;
import com.carrypick.repo.TripRepository;
import com.carrypick.web.dto.Dtos.*;
import com.carrypick.web.dto.Forms.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Trips, offers, requests, search and the popular ranking. */
@Service
public class MarketService {
    private static final int RANKING_DAYS = 30;
    private static final Set<Enums.OrderStatus> COUNTED = EnumSet.of(
            Enums.OrderStatus.PAID, Enums.OrderStatus.AWAITING_APPROVAL, Enums.OrderStatus.PURCHASED,
            Enums.OrderStatus.DELIVERED, Enums.OrderStatus.COMPLETED, Enums.OrderStatus.SETTLED);

    private final TripRepository trips;
    private final OfferRepository offers;
    private final RequestRepository requests;
    private final OrderRepository orders;
    private final AuthService auth;
    private final Mapper mapper;

    public MarketService(TripRepository trips, OfferRepository offers, RequestRepository requests,
                         OrderRepository orders, AuthService auth, Mapper mapper) {
        this.trips = trips;
        this.offers = offers;
        this.requests = requests;
        this.orders = orders;
        this.auth = auth;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- maintenance

    /** Close offers of ended trips and expire requests past their deadline. Runs hourly and on startup. */
    @Scheduled(initialDelay = 5_000, fixedDelay = 3_600_000)
    @Transactional
    public void housekeeping() {
        LocalDate today = Today.get();
        offers.closeEnded(today, Enums.OfferStatus.CLOSED);
        requests.expireOld(today, Enums.RequestStatus.OPEN, Enums.RequestStatus.EXPIRED);
    }

    // ---------------------------------------------------------------- trips (T2)

    @Transactional
    public TripDto createTrip(Long userId, TripForm f) {
        User me = auth.require(userId);
        LocalDate today = Today.get();
        if (!Countries.exists(f.countryCode())) throw ApiException.badRequest("COUNTRY", "Unsupported country");
        if (!f.departDate().isAfter(today)) {
            throw ApiException.badRequest("TRIP_DATE", "Departure date must be in the future");
        }
        if (f.returnDate().isBefore(f.departDate())) {
            throw ApiException.badRequest("TRIP_DATE", "Return date must be on or after the departure date");
        }
        Trip t = trips.save(new Trip(me, f.countryCode().toUpperCase(), blankToNull(f.city()), f.departDate(),
                f.returnDate(), blankToNull(f.luggageNote())));
        return TripDto.of(t);
    }

    @Transactional(readOnly = true)
    public List<TripDto> myTrips(Long userId, boolean includePast) {
        List<Trip> list = includePast
                ? trips.findByTravelerIdOrderByDepartDateAsc(userId)
                : trips.findByTravelerIdAndReturnDateGreaterThanEqualOrderByDepartDateAsc(userId, Today.get());
        return list.stream().map(TripDto::of).toList();
    }

    // ---------------------------------------------------------------- offers (B2, B4, T3)

    @Transactional(readOnly = true)
    public List<OfferDto> offersByCountry(String country) {
        return offers.findByCountryCodeAndStatusOrderByCreatedAtDesc(country.toUpperCase(), Enums.OfferStatus.ACTIVE)
                .stream().map(mapper::offer).toList();
    }

    @Transactional(readOnly = true)
    public OfferDto offer(Long id) {
        return mapper.offer(findOffer(id));
    }

    @Transactional(readOnly = true)
    public List<OfferDto> myOffers(Long userId) {
        return offers.findByTravelerIdOrderByCreatedAtDesc(userId).stream().map(mapper::offer).toList();
    }

    @Transactional
    public OfferDto createOffer(Long userId, OfferForm f) {
        User me = auth.require(userId);
        Trip trip = trips.findById(f.tripId()).orElseThrow(() -> ApiException.notFound("Trip"));
        if (!trip.getTraveler().getId().equals(userId)) throw ApiException.forbidden("This is not your trip");
        if (!trip.getDepartDate().isAfter(Today.get())) {
            throw ApiException.badRequest("TRIP_STARTED", "You can only add offers to trips that have not started");
        }
        Offer o = offers.save(new Offer(me, trip, f.productName().trim(), blankToNull(f.store()),
                blankToNull(f.category()), blankToNull(f.description()), blankToNull(f.imageUrl()),
                f.estPrice(), f.reward(), f.quantity()));
        return mapper.offer(o);
    }

    @Transactional
    public OfferDto updateOffer(Long userId, Long offerId, OfferForm f) {
        Offer o = findOffer(offerId);
        if (!o.getTraveler().getId().equals(userId)) throw ApiException.forbidden("This is not your offer");
        if (o.getStatus() == Enums.OfferStatus.CLOSED) throw ApiException.conflict("OFFER_CLOSED", "Offer is closed");
        o.update(f.productName().trim(), blankToNull(f.store()), blankToNull(f.category()),
                blankToNull(f.description()), blankToNull(f.imageUrl()), f.estPrice(), f.reward(), f.quantity());
        return mapper.offer(o);
    }

    @Transactional
    public OfferDto closeOffer(Long userId, Long offerId) {
        Offer o = findOffer(offerId);
        if (!o.getTraveler().getId().equals(userId)) throw ApiException.forbidden("This is not your offer");
        o.setStatus(Enums.OfferStatus.CLOSED);
        return mapper.offer(o);
    }

    public Offer findOffer(Long id) {
        return offers.findById(id).orElseThrow(() -> ApiException.notFound("Offer"));
    }

    // ---------------------------------------------------------------- requests (B5, T5, T6)

    @Transactional
    public RequestDto createRequest(Long userId, RequestForm f) {
        User me = auth.require(userId);
        if (!Countries.exists(f.countryCode())) throw ApiException.badRequest("COUNTRY", "Unsupported country");
        if (f.deadline().isBefore(Today.get().plusDays(1))) {
            throw ApiException.badRequest("DEADLINE", "Deadline must be at least tomorrow");
        }
        PurchaseRequest r = requests.save(new PurchaseRequest(me, f.countryCode().toUpperCase(),
                f.productName().trim(), blankToNull(f.productLink()), blankToNull(f.imageUrl()),
                blankToNull(f.options()), blankToNull(f.note()), f.maxBudget(), f.reward(), f.deadline()));
        return mapper.request(r);
    }

    @Transactional(readOnly = true)
    public List<RequestDto> myRequests(Long userId) {
        return requests.findByBuyerIdOrderByCreatedAtDesc(userId).stream().map(mapper::request).toList();
    }

    @Transactional(readOnly = true)
    public RequestDto request(Long id) {
        return mapper.request(findRequest(id));
    }

    /**
     * Request board (T5): open requests in countries where I have a trip that is not over yet
     * and that returns on or before the request deadline. Excludes my own requests.
     */
    @Transactional(readOnly = true)
    public List<RequestDto> openRequestsForTraveler(Long userId) {
        LocalDate today = Today.get();
        List<Trip> myTrips = trips.findByTravelerIdAndReturnDateGreaterThanEqualOrderByDepartDateAsc(userId, today);
        if (myTrips.isEmpty()) return List.of();
        Set<String> countries = myTrips.stream().map(Trip::getCountryCode).collect(Collectors.toSet());
        return requests.findByStatusAndCountryCodeInOrderByDeadlineAsc(Enums.RequestStatus.OPEN, countries).stream()
                .filter(r -> !r.getBuyer().getId().equals(userId))
                .filter(r -> !r.getDeadline().isBefore(today))
                .filter(r -> matchingTrip(myTrips, r) != null)
                .map(mapper::request)
                .toList();
    }

    @Transactional
    public RequestDto cancelRequest(Long userId, Long requestId) {
        PurchaseRequest r = findRequest(requestId);
        if (!r.getBuyer().getId().equals(userId)) throw ApiException.forbidden("This is not your request");
        if (r.getStatus() != Enums.RequestStatus.OPEN) {
            throw ApiException.conflict("REQUEST_NOT_OPEN", "Only open requests can be cancelled");
        }
        r.setStatus(Enums.RequestStatus.CANCELLED);
        return mapper.request(r);
    }

    public PurchaseRequest findRequest(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Request"));
    }

    /** A trip of the traveler to the request's country that ends by the deadline and is not over. */
    public Trip matchingTrip(List<Trip> travelerTrips, PurchaseRequest r) {
        LocalDate today = Today.get();
        return travelerTrips.stream()
                .filter(t -> t.getCountryCode().equals(r.getCountryCode()))
                .filter(t -> !t.getReturnDate().isBefore(today))
                .filter(t -> !t.getReturnDate().isAfter(r.getDeadline()))
                .findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- departures board (B1)

    /** Upcoming trips (not yet departed) that have at least one bookable offer, soonest first. */
    @Transactional(readOnly = true)
    public List<BoardRow> board() {
        LocalDate today = Today.get();
        Map<Long, List<Offer>> byTrip = offers.findAll().stream()
                .filter(o -> o.isBookable(today))
                .collect(Collectors.groupingBy(o -> o.getTrip().getId()));
        return byTrip.values().stream()
                .map(list -> {
                    Trip t = list.get(0).getTrip();
                    return new BoardRow(t.getId(), t.getCountryCode(), t.getCity(), t.getDepartDate(),
                            t.getReturnDate(), t.getTraveler().getName(), list.size());
                })
                .sorted(Comparator.comparing(BoardRow::departDate))
                .limit(12)
                .toList();
    }

    // ---------------------------------------------------------------- search & ranking (B1, B3)

    @Transactional(readOnly = true)
    public SearchDto search(String q, String country) {
        String query = q == null ? "" : q.trim();
        String c = country == null || country.isBlank() ? null : country.toUpperCase();
        List<OfferDto> offerHits = offers.search(query, Enums.OfferStatus.ACTIVE).stream()
                .filter(o -> c == null || o.getCountryCode().equals(c))
                .map(mapper::offer).toList();
        List<RequestDto> requestHits = requests.search(query, Enums.RequestStatus.OPEN).stream()
                .filter(r -> c == null || r.getCountryCode().equals(c))
                .map(mapper::request).toList();
        return new SearchDto(offerHits, requestHits);
    }

    /** Top products by paid-or-later orders in the last 30 days. All data is demo data. */
    @Transactional(readOnly = true)
    public RankingDto ranking(String country) {
        String c = country.toUpperCase();
        Instant since = Instant.now().minus(Duration.ofDays(RANKING_DAYS));
        List<Object[]> rows = orders.rankProducts(c, COUNTED, since);
        List<RankingItem> items = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rows) {
            if (rank > 10) break;
            String name = (String) row[0];
            long count = ((Number) row[1]).longValue();
            Offer active = offers.findByCountryCodeAndProductNameIgnoreCaseAndStatus(c, name, Enums.OfferStatus.ACTIVE)
                    .stream().findFirst().orElse(null);
            items.add(new RankingItem(rank++, name, count, active != null ? active.getId() : null,
                    active != null ? active.getImageUrl() : null, active != null ? active.getCategory() : null));
        }
        return new RankingDto(c, true, RANKING_DAYS, items);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
