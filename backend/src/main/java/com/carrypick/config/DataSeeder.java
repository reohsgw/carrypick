package com.carrypick.config;

import com.carrypick.domain.*;
import com.carrypick.repo.*;
import com.carrypick.service.PricingService;
import com.carrypick.service.Today;
import com.carrypick.web.dto.Dtos.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Creates demo accounts, trips, offers, requests and some finished orders (for the ranking).
 * Runs only when app.seed-demo-data=true and the database has no users yet.
 * Every account here is marked test=true, and the ranking is labelled "Demo data" in the app.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_PASSWORD = "carrypick123";

    private final AppProperties props;
    private final UserRepository users;
    private final TripRepository trips;
    private final OfferRepository offers;
    private final RequestRepository requests;
    private final OrderRepository orders;
    private final SettlementRepository settlements;
    private final PasswordEncoder encoder;
    private final PricingService pricing;

    public DataSeeder(AppProperties props, UserRepository users, TripRepository trips, OfferRepository offers,
                      RequestRepository requests, OrderRepository orders, SettlementRepository settlements,
                      PasswordEncoder encoder, PricingService pricing) {
        this.props = props;
        this.users = users;
        this.trips = trips;
        this.offers = offers;
        this.requests = requests;
        this.orders = orders;
        this.settlements = settlements;
        this.encoder = encoder;
        this.pricing = pricing;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seedDemoData() || users.count() > 0) return;
        LocalDate today = Today.get();
        String pw = encoder.encode(DEMO_PASSWORD);

        User minji = users.save(new User("buyer@carrypick.test", pw, "Minji (Buyer)", Enums.Mode.BUYER, true));
        User jun = users.save(new User("buyer2@carrypick.test", pw, "Jun (Buyer)", Enums.Mode.BUYER, true));
        User kenji = users.save(new User("traveler@carrypick.test", pw, "Kenji (Traveler)", Enums.Mode.TRAVELER, true));
        User linh = users.save(new User("traveler2@carrypick.test", pw, "Linh (Traveler)", Enums.Mode.TRAVELER, true));

        Trip tokyo = trips.save(new Trip(kenji, "JP", "Tokyo", today.plusDays(10), today.plusDays(17),
                "One spare carry-on, about 7 kg"));
        Trip osaka = trips.save(new Trip(kenji, "JP", "Osaka", today.plusDays(40), today.plusDays(45),
                "Half a suitcase free"));
        Trip paris = trips.save(new Trip(linh, "FR", "Paris", today.plusDays(20), today.plusDays(28),
                "Small items only, about 3 kg"));
        Trip bangkok = trips.save(new Trip(linh, "TH", "Bangkok", today.plusDays(6), today.plusDays(12),
                "Plenty of space"));
        Trip nyc = trips.save(new Trip(kenji, "US", "New York", today.plusDays(25), today.plusDays(33),
                "One empty suitcase"));

        Offer tokyoBanana = offers.save(new Offer(kenji, tokyo, "Tokyo Banana (8 pcs)", "Haneda Airport shop", "Snacks",
                "Classic banana custard sponge cakes. Best before about 7 days after purchase.", null, 12000, 4000, 6));
        Offer royce = offers.save(new Offer(kenji, tokyo, "Royce' Nama Chocolate", "Royce' Ginza", "Snacks",
                "Needs to stay cool; I will carry it in an insulated bag.", null, 18000, 5000, 4));
        Offer anessa = offers.save(new Offer(kenji, tokyo, "Anessa Perfect UV Sunscreen 60ml", "Matsumoto Kiyoshi", "Beauty",
                "Japanese drugstore price.", null, 22000, 5000, 5));
        offers.save(new Offer(kenji, tokyo, "Uniqlo Japan-limited graphic T-shirt", "Uniqlo Ginza", "Fashion",
                "Tell me the size in the order note after booking.", null, 20000, 6000, 3));
        offers.save(new Offer(kenji, osaka, "Kit Kat regional flavors set", "Shin-Osaka station", "Snacks",
                "Assorted regional flavors, about 6 boxes.", null, 25000, 7000, 3));
        Offer laduree = offers.save(new Offer(linh, paris, "Ladurée macarons (box of 12)", "Ladurée Champs-Élysées", "Snacks",
                "Fragile; will hand-carry.", null, 52000, 12000, 2));
        offers.save(new Offer(linh, paris, "Diptyque Baies candle 190g", "Diptyque Saint-Germain", "Home",
                "Paris boutique price.", null, 98000, 15000, 2));
        offers.save(new Offer(linh, paris, "Bioderma Sensibio H2O 500ml", "Pharmacie Monge", "Beauty",
                "French pharmacy price, much cheaper than Korea.", null, 16000, 5000, 4));
        Offer mama = offers.save(new Offer(linh, bangkok, "Mama Tom Yum instant noodles (10 pack)", "Big C", "Food",
                "Family pack.", null, 7000, 3000, 8));
        offers.save(new Offer(linh, bangkok, "Naraya cotton bag", "Naraya Siam", "Fashion",
                "Choose color in the order note.", null, 9000, 3000, 5));
        offers.save(new Offer(kenji, nyc, "Trader Joe's tote bag", "Trader Joe's Manhattan", "Fashion",
                "Mini or regular size.", null, 6000, 4000, 6));
        offers.save(new Offer(kenji, nyc, "Levi's 501 Original jeans", "Levi's Times Square", "Fashion",
                "US pricing; tell me waist and length.", null, 95000, 15000, 2));

        requests.save(new PurchaseRequest(minji, "JP", "Tokyo-limited character plush (medium)", null, null,
                "Any Tokyo-only design", "Character store in Ikebukuro or Shibuya", 45000, 10000, today.plusDays(30)));
        requests.save(new PurchaseRequest(minji, "FR", "Le Labo Santal 33 50ml", null, null,
                "50ml", "Paris boutique only please", 260000, 20000, today.plusDays(35)));
        requests.save(new PurchaseRequest(jun, "TH", "Jim Thompson silk scarf", null, null,
                "Blue tones", null, 70000, 10000, today.plusDays(20)));

        // Finished demo orders so the country ranking has something to show (labelled "Demo data" in the app).
        demoOrder(jun, tokyoBanana, 5);
        demoOrder(minji, tokyoBanana, 3);
        demoOrder(jun, royce, 2);
        demoOrder(jun, anessa, 1);
        demoOrder(minji, laduree, 2);
        demoOrder(jun, mama, 1);

        log.info("Demo data created. Log in with buyer@carrypick.test or traveler@carrypick.test / {}", DEMO_PASSWORD);
    }

    private void demoOrder(User buyer, Offer offer, int howMany) {
        for (int i = 0; i < howMany; i++) {
            Quote q = pricing.quote(offer.getEstPrice(), offer.getReward());
            Order o = new Order(buyer, offer.getTraveler(), offer, null, offer.getProductName(), offer.getCountryCode(),
                    offer.getImageUrl(), 1, q.budget(), q.reward(), q.fee(), q.tax(), offer.getTrip().getReturnDate());
            Instant when = Instant.now().minus(Duration.ofDays(3 + i * 2L));
            o.setCreatedAt(when);
            o.setPaidAt(when);
            o.setActualPrice(q.budget());
            o.setPurchasedAt(when);
            o.setDeliveredAt(when);
            o.setCompletedAt(when);
            o.setSettledAt(when);
            o.setStatus(Enums.OrderStatus.SETTLED);
            orders.save(o);
            settlements.save(new Settlement(o, q.budget() + q.reward(), 0, q.fee()));
        }
    }
}
