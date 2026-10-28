package com.carrypick;

import com.carrypick.common.ApiException;
import com.carrypick.config.AppProperties;
import com.carrypick.service.ReceiptScanService;
import com.carrypick.web.dto.Dtos.ReceiptScan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The receipt reader without a network: mock mode, file checks, and parsing of a model reply. */
class ReceiptScanServiceTest {

    private final ReceiptScanService mockService = service("mock");

    private static ReceiptScanService service(String provider) {
        AppProperties props = new AppProperties("", null, "uploads", false, null, null,
                new AppProperties.Ai(provider, "", "claude-haiku-5-5", "https://api.anthropic.com/v1/messages"),
                Map.of("JPY", 9.2, "USD", 1380.0));
        return new ReceiptScanService(props, new ObjectMapper());
    }

    @Test
    void mockModeReturnsLabelledSample() {
        ReceiptScan r = mockService.scan(new MockMultipartFile("image", "r.png", "image/png", new byte[] {1, 2, 3}));
        assertTrue(r.mock());
        assertEquals("JPY", r.currency());
        assertEquals(2592.0, r.total());
        assertEquals(Math.round(2592 * 9.2), r.totalKrw());
    }

    @Test
    void rejectsNonImages() {
        ApiException e = assertThrows(ApiException.class, () ->
                mockService.scan(new MockMultipartFile("image", "r.pdf", "application/pdf", new byte[] {1})));
        assertEquals("FILE_TYPE", e.getCode());
    }

    @Test
    void parsesFencedReplyAndFlagsCustomsItems() throws Exception {
        String reply = """
                ```json
                {"store":"Duty Free","date":"2026-11-10","currency":"usd","total":95.5,
                 "items":[{"name":"Whisky 700ml","price":60,"quantity":1,"category":"alcohol"},
                          {"name":"Omega-3","price":35.5,"quantity":2,"category":"supplement"},
                          {"name":"Mug","price":null,"quantity":null,"category":"kitchenware"}],
                 "note":null}
                ```""";
        var parse = ReceiptScanService.class.getDeclaredMethod("parse", String.class, boolean.class, String.class);
        parse.setAccessible(true);
        ReceiptScan r = (ReceiptScan) parse.invoke(mockService, reply, false, "test");

        assertEquals("USD", r.currency());
        assertEquals(Math.round(95.5 * 1380), r.totalKrw());
        assertEquals(List.of("alcohol", "supplement"), r.customsFlags());
        assertEquals("other", r.items().get(2).category());
    }
}
