package com.jmifx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FxJsonTest {

    @Test
    void plainValue() {
        assertEquals("{\"name\":\"Berlin\"}", FxJson.obj("name", "Berlin"));
    }

    @Test
    void escapesQuotesBackslashNewlineAndControlChars() {
        String json = FxJson.obj("name", "São \"Paulo\"\\\n\u0000\u001f");
        assertEquals("{\"name\":\"São \\\"Paulo\\\"\\\\\\n\\u0000\\u001f\"}", json);
    }

    @Test
    void nullValueBecomesNullLiteral() {
        assertEquals("{\"name\":null}", FxJson.obj("name", null));
    }

    @Test
    void stringValueExtractsTopLevelMember() {
        String tokenResponse = "{\"access_token\":\"Q6zvq8qGMUrN1VgouerOp4TJrry2f8oq\","
                + "\"token_type\":\"Bearer\",\"expires_in\":3599}";
        assertEquals("Q6zvq8qGMUrN1VgouerOp4TJrry2f8oq",
                FxJson.stringValue(tokenResponse, "access_token"));
        assertEquals("Bearer", FxJson.stringValue(tokenResponse, "token_type"));
    }

    @Test
    void stringValueReturnsNullForMissingKey() {
        String json = "{\"token_type\":\"Bearer\",\"expires_in\":3599}";
        assertNull(FxJson.stringValue(json, "access_token"));
        assertNull(FxJson.stringValue("", "access_token"));
        assertNull(FxJson.stringValue("not json", "access_token"));
    }

    @Test
    void stringValueUnescapesValue() {
        assertEquals("say \"hi\"", FxJson.stringValue("{\"m\":\"say \\\"hi\\\"\"}", "m"));
        assertEquals("a\\b", FxJson.stringValue("{\"m\":\"a\\\\b\"}", "m"));
        assertEquals("line\nnext", FxJson.stringValue("{\"m\":\"line\\nnext\"}", "m"));
        assertEquals("tab\t", FxJson.stringValue("{\"m\":\"tab\\t\"}", "m"));
        assertEquals("ctrl\u0001", FxJson.stringValue("{\"m\":\"ctrl\\u0001\"}", "m"));
    }
}
