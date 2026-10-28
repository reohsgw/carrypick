package com.carrypick.web;

import com.carrypick.common.ApiException;
import com.carrypick.config.AuthUser;
import com.carrypick.service.MarketService;
import com.carrypick.service.OrderService;
import com.carrypick.web.dto.Dtos.*;
import com.carrypick.web.dto.Forms.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Countries, search, trips, offers and requests. */
@RestController
@RequestMapping("/api")
public class MarketController {
    private final MarketService market;
    private final OrderService orders;

    public MarketController(MarketService market, OrderService orders) {
        this.market = market;
        this.orders = orders;
    }

    // ---- browse (public)

    /** B1 */
    @GetMapping("/countries/{code}/ranking")
    public RankingDto ranking(@PathVariable String code) {
        return market.ranking(code);
    }

    /** B1: departures board of upcoming traveler trips. */
    @GetMapping("/board")
    public List<BoardRow> board() {
        return market.board();
    }

    /** B3 */
    @GetMapping("/search")
    public SearchDto search(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String country) {
        return market.search(q, country);
    }

    /** B2 */
    @GetMapping("/offers")
    public List<OfferDto> offers(@RequestParam String country) {
        return market.offersByCountry(country);
    }

    /** T4 / T1: the traveler's own offers. */
    @GetMapping("/offers/mine")
    public List<OfferDto> myOffers(@AuthenticationPrincipal AuthUser me) {
        return market.myOffers(requireLogin(me));
    }

    /** B4 */
    @GetMapping("/offers/{id}")
    public OfferDto offer(@PathVariable Long id) {
        return market.offer(id);
    }

    /** T3 */
    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto createOffer(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody OfferForm form) {
        return market.createOffer(me.id(), form);
    }

    /** T3 (edit) */
    @PatchMapping("/offers/{id}")
    public OfferDto updateOffer(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                @Valid @RequestBody OfferForm form) {
        return market.updateOffer(me.id(), id, form);
    }

    @PostMapping("/offers/{id}/close")
    public OfferDto closeOffer(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return market.closeOffer(me.id(), id);
    }

    // ---- trips

    /** T2 */
    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto createTrip(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TripForm form) {
        return market.createTrip(me.id(), form);
    }

    /** T1 */
    @GetMapping("/trips/me")
    public List<TripDto> myTrips(@AuthenticationPrincipal AuthUser me,
                                 @RequestParam(defaultValue = "false") boolean includePast) {
        return market.myTrips(me.id(), includePast);
    }

    // ---- requests

    /** B5 */
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto createRequest(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RequestForm form) {
        return market.createRequest(me.id(), form);
    }

    /** B7 */
    @GetMapping("/requests/me")
    public List<RequestDto> myRequests(@AuthenticationPrincipal AuthUser me) {
        return market.myRequests(me.id());
    }

    /** T5: open requests that match my trips. */
    @GetMapping("/requests/open")
    public List<RequestDto> openRequests(@AuthenticationPrincipal AuthUser me) {
        return market.openRequestsForTraveler(me.id());
    }

    /** T6 */
    @GetMapping("/requests/{id}")
    public RequestDto request(@PathVariable Long id) {
        return market.request(id);
    }

    /** T6: accept -> creates the order. */
    @PostMapping("/requests/{id}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                           @RequestBody(required = false) AcceptForm form) {
        return orders.acceptRequest(me.id(), id, form == null ? null : form.tripId());
    }

    /** B7: buyer withdraws an open request. */
    @DeleteMapping("/requests/{id}")
    public RequestDto cancelRequest(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return market.cancelRequest(me.id(), id);
    }

    private static Long requireLogin(AuthUser me) {
        if (me == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in");
        return me.id();
    }
}
