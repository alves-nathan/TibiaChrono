package com.nathan.tibiastats.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonConverterTest {
    @Test
    void convertsSimpleObjectsToJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Raw Raw");
        payload.put("level", 100);

        assertThat(JsonConverter.toJson(payload))
                .isEqualTo("{\"name\":\"Raw Raw\",\"level\":100}");
    }

    @Test
    void wrapsJacksonFailuresInRuntimeException() {
        Object unsupported = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        };

        assertThatThrownBy(() -> JsonConverter.toJson(unsupported))
                .isInstanceOf(RuntimeException.class);
    }
}
