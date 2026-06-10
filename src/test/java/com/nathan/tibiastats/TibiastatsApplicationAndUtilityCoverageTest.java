package com.nathan.tibiastats;

import com.nathan.tibiastats.util.JsonConverter;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class TibiastatsApplicationAndUtilityCoverageTest {
    @Test
    void applicationMainDelegatesToSpringApplicationRun() {
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            TibiastatsApplication.main(args);

            spring.verify(() -> SpringApplication.run(TibiastatsApplication.class, args));
        }
    }

    @Test
    void jsonConverterCanBeInstantiatedAndSerializesSimpleObjects() {
        assertThat(new JsonConverter()).isNotNull();
        assertThat(JsonConverter.toJson(Map.of("value", 123))).isEqualTo("{\"value\":123}");
    }
}
