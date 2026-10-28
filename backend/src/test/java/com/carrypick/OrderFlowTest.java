package com.carrypick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the two flows in the capstone plan, using the seeded demo accounts
 * and mock payment mode:
 *   Flow A: buyer books a traveler's offer -> pay -> purchase -> deliver -> confirm -> settled
 *   Flow B: buyer posts a request -> traveler accepts -> pay -> over budget -> approve + extra pay -> ... settled
 * Plus: payment failure + retry, wrong-role access, and amount tampering.
 */
@SpringBootTest(properties = {"app.payment.mode=mock", "app.upload-dir=target/test-uploads"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OrderFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String PW = "carrypick123";

    @Test
    void flowA_bookOffer_happyPath() throws Exception {
        String buyer = login("buyer@carrypick.test");
        String traveler = login("traveler@carrypick.test");

        long offerId = firstBookableOffer("JP");
        JsonNode order = body(post("/api/orders", buyer, "{\"offerId\":" + offerId + ",\"quantity\":1}"), 201);
        long orderId = order.get("id").asLong();
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");

        pay(buyer, orderId, "mock_ok_1");
        assertStatus(buyer, orderId, "PAID");

        long budget = order.get("budget").asLong();
        purchase(traveler, orderId, budget - 1000); // under budget
        assertStatus(buyer, orderId, "PURCHASED");

        body(post("/api/orders/" + orderId + "/deliver", traveler, null), 200);
        JsonNode done = body(post("/api/orders/" + orderId + "/confirm", buyer, null), 200);
        assertThat(done.get("status").asText()).isEqualTo("SETTLED");
        assertThat(done.get("settlement").get("buyerRefund").asLong()).isEqualTo(1000);
        assertThat(done.get("settlement").get("travelerPayout").asLong())
                .isEqualTo(budget - 1000 + order.get("reward").asLong());
    }

    @Test
    void flowB_requestAccepted_overBudgetApproved() throws Exception {
        String buyer = login("buyer@carrypick.test");
        String traveler = login("traveler@carrypick.test");

        // Kenji (traveler) has a Tokyo trip returning in 17 days; deadline 30 days covers it.
        String deadline = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(30).toString();
        JsonNode req = body(post("/api/requests", buyer, """
                {"countryCode":"JP","productName":"Test matcha set","maxBudget":30000,"reward":5000,"deadline":"%s"}
                """.formatted(deadline)), 201);
        long requestId = req.get("id").asLong();

        JsonNode board = body(get("/api/requests/open").header("Authorization", "Bearer " + traveler), 200);
        assertThat(board.findValues("id").stream().anyMatch(n -> n.asLong() == requestId)).isTrue();

        JsonNode order = body(post("/api/requests/" + requestId + "/accept", traveler, null), 201);
        long orderId = order.get("id").asLong();
        pay(buyer, orderId, "mock_ok_2");

        purchase(traveler, orderId, 36000); // 6,000 over the 30,000 budget
        JsonNode waiting = body(get("/api/orders/" + orderId).header("Authorization", "Bearer " + buyer), 200);
        assertThat(waiting.get("status").asText()).isEqualTo("AWAITING_APPROVAL");
        assertThat(waiting.get("extraItemAmount").asLong()).isEqualTo(6000);

        JsonNode extra = body(post("/api/orders/" + orderId + "/approve", buyer, null), 200);
        assertThat(extra.get("type").asText()).isEqualTo("EXTRA");
        confirm(buyer, extra.get("tossOrderId").asText(), extra.get("amount").asLong(), "mock_ok_3");
        assertStatus(buyer, orderId, "PURCHASED");

        body(post("/api/orders/" + orderId + "/deliver", traveler, null), 200);
        JsonNode done = body(post("/api/orders/" + orderId + "/confirm", buyer, null), 200);
        assertThat(done.get("status").asText()).isEqualTo("SETTLED");
        assertThat(done.get("settlement").get("travelerPayout").asLong()).isEqualTo(36000 + 5000);
    }

    @Test
    void paymentFailure_thenRetrySucceeds() throws Exception {
        String buyer = login("buyer@carrypick.test");
        long offerId = firstBookableOffer("FR");
        long orderId = body(post("/api/orders", buyer, "{\"offerId\":" + offerId + ",\"quantity\":1}"), 201)
                .get("id").asLong();

        pay(buyer, orderId, "mock_fail_card");
        assertStatus(buyer, orderId, "PAYMENT_FAILED");

        pay(buyer, orderId, "mock_ok_retry");
        assertStatus(buyer, orderId, "PAID");
    }

    @Test
    void securityRules() throws Exception {
        String buyer = login("buyer@carrypick.test");
        String traveler = login("traveler@carrypick.test");
        String otherBuyer = login("buyer2@carrypick.test");
        long offerId = firstBookableOffer("JP");
        long orderId = body(post("/api/orders", buyer, "{\"offerId\":" + offerId + ",\"quantity\":1}"), 201)
                .get("id").asLong();

        // A stranger cannot see the order.
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + otherBuyer))
                .andExpect(status().isNotFound());
        // The traveler cannot pay or confirm for the buyer.
        mvc.perform(post("/api/payments/prepare", traveler, "{\"orderId\":" + orderId + "}"))
                .andExpect(status().isForbidden());
        // The buyer cannot skip ahead to "confirm received".
        mvc.perform(post("/api/orders/" + orderId + "/confirm", buyer, null))
                .andExpect(status().isConflict());
        // A tampered amount is rejected.
        JsonNode prep = body(post("/api/payments/prepare", buyer, "{\"orderId\":" + orderId + "}"), 200);
        mvc.perform(post("/api/payments/confirm", buyer, """
                        {"paymentKey":"mock_ok","tossOrderId":"%s","amount":%d}
                        """.formatted(prep.get("tossOrderId").asText(), prep.get("amount").asLong() - 1)))
                .andExpect(status().isBadRequest());
        // No token at all.
        mvc.perform(get("/api/orders/me")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private String login(String email) throws Exception {
        JsonNode res = body(post("/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"), 200);
        return res.get("token").asText();
    }

    private long firstBookableOffer(String country) throws Exception {
        JsonNode list = body(get("/api/offers").param("country", country), 200);
        for (JsonNode o : list) {
            if (o.get("bookable").asBoolean()) return o.get("id").asLong();
        }
        throw new AssertionError("No bookable offer in " + country);
    }

    private void pay(String token, long orderId, String paymentKey) throws Exception {
        JsonNode prep = body(post("/api/payments/prepare", token, "{\"orderId\":" + orderId + "}"), 200);
        confirm(token, prep.get("tossOrderId").asText(), prep.get("amount").asLong(), paymentKey);
    }

    private void confirm(String token, String tossOrderId, long amount, String paymentKey) throws Exception {
        body(post("/api/payments/confirm", token, """
                {"paymentKey":"%s","tossOrderId":"%s","amount":%d}
                """.formatted(paymentKey, tossOrderId, amount)), 200);
    }

    private void purchase(String token, long orderId, long actualPrice) throws Exception {
        MockMultipartFile receipt = new MockMultipartFile("receipt", "receipt.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/orders/" + orderId + "/purchase")
                        .file(receipt)
                        .param("actualPrice", String.valueOf(actualPrice))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void assertStatus(String token, long orderId, String expected) throws Exception {
        JsonNode o = body(get("/api/orders/" + orderId).header("Authorization", "Bearer " + token), 200);
        assertThat(o.get("status").asText()).isEqualTo(expected);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(
            String url, String token, String jsonBody) {
        var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (jsonBody != null) b.content(jsonBody);
        return b;
    }

    private JsonNode body(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus)
            throws Exception {
        ResultActions r = mvc.perform(request).andExpect(status().is(expectedStatus));
        String s = r.andReturn().getResponse().getContentAsString();
        return s.isEmpty() ? json.createObjectNode() : json.readTree(s);
    }
}
