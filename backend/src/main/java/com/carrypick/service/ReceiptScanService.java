package com.carrypick.service;

import com.carrypick.common.ApiException;
import com.carrypick.config.AppProperties;
import com.carrypick.web.dto.Dtos.ReceiptItem;
import com.carrypick.web.dto.Dtos.ReceiptScan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * AI receipt reader: a photo of a receipt in any language -> store, items, total, currency,
 * a KRW estimate, and customs flags (alcohol, supplements, medicine, meat, tobacco).
 *
 * The traveler always sees the result as an editable form before saving, because a model can misread.
 * The API key stays on this server; the browser never sees it.
 */
@Service
public class ReceiptScanService {
    private static final Logger log = LoggerFactory.getLogger(ReceiptScanService.class);
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> CATEGORIES =
            Set.of("alcohol", "tobacco", "supplement", "medicine", "meat", "food", "cosmetics", "fashion", "electronics", "other");
    private static final Set<String> CUSTOMS_SENSITIVE = Set.of("alcohol", "tobacco", "supplement", "medicine", "meat");

    private static final String PROMPT = """
            You read shop receipts photographed by travelers. Return ONLY a JSON object, no prose, no code fences:
            {
              "store": string or null,
              "date": "YYYY-MM-DD" or null,
              "currency": ISO 4217 code such as "JPY", "USD", "EUR" (infer from symbols and language),
              "total": number, the final amount paid including tax, in that currency,
              "items": [ { "name": string (keep the original language, add a short English gloss in parentheses if not English),
                           "price": number or null, "quantity": integer or null,
                           "category": one of "alcohol","tobacco","supplement","medicine","meat","food","cosmetics","fashion","electronics","other" } ],
              "note": string or null (anything unclear, e.g. "total partly hidden")
            }
            If the image is not a receipt, return {"error": "not_a_receipt"}.
            """;

    private final AppProperties.Ai cfg;
    private final Map<String, Double> fx;
    private final ObjectMapper json;
    private final RestClient http = RestClient.create();

    public ReceiptScanService(AppProperties props, ObjectMapper json) {
        this.cfg = props.ai();
        this.fx = props.fx() == null ? Map.of() : props.fx();
        this.json = json;
    }

    public ReceiptScan scan(MultipartFile image) {
        if (image == null || image.isEmpty()) throw ApiException.badRequest("FILE_REQUIRED", "Please attach a receipt photo");
        if (!IMAGE_TYPES.contains(image.getContentType())) {
            throw ApiException.badRequest("FILE_TYPE", "Receipt must be a JPG, PNG, WEBP or GIF photo");
        }
        if (image.getSize() > MAX_BYTES) throw ApiException.badRequest("FILE_TOO_LARGE", "Receipt photo must be 5MB or smaller");

        if (cfg == null || cfg.isMock()) return sample();

        String text = callClaude(image);
        return parse(text, false, cfg.model());
    }

    // ------------------------------------------------------------------ Claude call

    private String callClaude(MultipartFile image) {
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded image", e);
        }
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "max_tokens", 1024,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64", "media_type", image.getContentType(), "data", base64)),
                                Map.of("type", "text", "text", PROMPT)))));
        try {
            JsonNode res = http.post()
                    .uri(cfg.apiUrl())
                    .header("x-api-key", cfg.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (res == null || !res.path("content").isArray() || res.path("content").isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY", "The AI returned no result. Enter the price manually.");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : res.path("content")) {
                if ("text".equals(block.path("type").asText())) sb.append(block.path("text").asText());
            }
            return sb.toString();
        } catch (RestClientResponseException e) {
            log.warn("Claude API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_FAILED", "Could not read the receipt right now. Enter the price manually.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Claude API call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_FAILED", "Could not read the receipt right now. Enter the price manually.");
        }
    }

    // ------------------------------------------------------------------ parsing + enrichment

    ReceiptScan parse(String text, boolean mock, String model) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?", "").replaceFirst("```\\s*$", "").trim();
        }
        int start = cleaned.indexOf('{'), end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_UNREADABLE", "Could not read this receipt. Enter the price manually.");
        }
        JsonNode n;
        try {
            n = json.readTree(cleaned.substring(start, end + 1));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_UNREADABLE", "Could not read this receipt. Enter the price manually.");
        }
        if (n.hasNonNull("error")) {
            throw ApiException.badRequest("NOT_A_RECEIPT", "This photo doesn't look like a receipt");
        }

        String currency = text(n, "currency");
        currency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        Double total = n.path("total").isNumber() ? n.path("total").asDouble() : null;

        List<ReceiptItem> items = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>();
        for (JsonNode it : n.path("items")) {
            String cat = text(it, "category");
            cat = cat == null ? "other" : cat.toLowerCase(Locale.ROOT);
            if (!CATEGORIES.contains(cat)) cat = "other";
            if (CUSTOMS_SENSITIVE.contains(cat)) flags.add(cat);
            items.add(new ReceiptItem(text(it, "name"),
                    it.path("price").isNumber() ? it.path("price").asDouble() : null,
                    it.path("quantity").isInt() ? it.path("quantity").asInt() : null,
                    cat));
        }

        Double rate = currency == null ? null : fx.get(currency);
        Long totalKrw = (total != null && rate != null) ? Math.round(total * rate) : null;
        String note = text(n, "note");
        if (total != null && rate == null && currency != null) {
            note = (note == null ? "" : note + " ") + "No exchange rate set for " + currency + ".";
        }
        return new ReceiptScan(mock, model, text(n, "store"), text(n, "date"), currency, total, totalKrw, rate,
                items, List.copyOf(flags), note);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    /** Mock mode: a fixed Japanese receipt so the flow can be demonstrated without an API key. */
    private ReceiptScan sample() {
        String sample = """
                {"store":"羽田空港 東京ばな奈 ショップ","date":null,"currency":"JPY","total":2592,
                 "items":[{"name":"東京ばな奈 8個入 (Tokyo Banana, 8 pcs)","price":1296,"quantity":2,"category":"food"}],
                 "note":"Sample result (mock mode). Set AI_PROVIDER=anthropic and ANTHROPIC_API_KEY to read real receipts."}
                """;
        return parse(sample, true, "mock");
    }
}
