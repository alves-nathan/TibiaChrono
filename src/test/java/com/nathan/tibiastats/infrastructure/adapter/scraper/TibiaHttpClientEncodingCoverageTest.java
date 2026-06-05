package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TibiaHttpClientEncodingCoverageTest {
    @Test
    void worldAndGuildHttpClientsEncodeBlankNullAndWhitespaceValuesWithoutNetworkAccess() throws Exception {
        assertThat(invokeEncode(TibiaWorldHttpClient.class, null)).isEmpty();
        assertThat(invokeEncode(TibiaWorldHttpClient.class, "Wintera Test")).isEqualTo("Wintera+Test");
        assertThat(invokeEncode(TibiaGuildHttpClient.class, null)).isEmpty();
        assertThat(invokeEncode(TibiaGuildHttpClient.class, "Raw Raw")).isEqualTo("Raw+Raw");
    }

    @Test
    void characterHttpClientRejectsNullNameBeforeOpeningNetworkConnection() {
        TibiaCharacterHttpClient client = new TibiaCharacterHttpClient();

        assertThatThrownBy(() -> client.fetchCharacterDetailsDocument(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static String invokeEncode(Class<?> type, String value) throws Exception {
        Method method = type.getDeclaredMethod("encode", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }
}
