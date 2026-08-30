package com.kaffe.common.rules.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKernelV1GoldenTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesTheVersionedGoldenContract() throws Exception {
        JsonNode fixture = fixture();
        assertThat(fixture.path("schemaVersion").asText())
                .isEqualTo(RuleKernelV1.CONTRACT_VERSION);

        JsonNode rawDefinition = fixture.path("definition");
        RuleKernelV1.Definition definition = new RuleKernelV1.Definition(
                OffsetDateTime.parse(rawDefinition.path("startsAt").asText()),
                OffsetDateTime.parse(rawDefinition.path("endsAt").asText()),
                java.util.List.of(new RuleKernelV1.WeeklyWindow(
                        rawDefinition.path("dayOfWeek").asInt(),
                        LocalTime.parse(rawDefinition.path("timeFrom").asText()),
                        LocalTime.parse(rawDefinition.path("timeTo").asText()))),
                RuleKernelV1.Eligibility.only(textSet(rawDefinition.path("paymentMethods"))),
                RuleKernelV1.Eligibility.only(textSet(rawDefinition.path("orderChannels")))
        );

        for (JsonNode testCase : fixture.path("cases")) {
            RuleKernelV1.Decision decision = RuleKernelV1.evaluate(
                    definition,
                    new RuleKernelV1.Context(
                            testCase.path("active").asBoolean(),
                            OffsetDateTime.parse(testCase.path("effectiveAt").asText()),
                            ZoneId.of(testCase.path("zoneId").asText()),
                            testCase.path("paymentMethod").asText(),
                            testCase.path("orderChannel").asText()
                    )
            );

            assertThat(decision.eligible())
                    .as(testCase.path("name").asText())
                    .isEqualTo(testCase.path("eligible").asBoolean());
            assertThat(decision.reason().code())
                    .as(testCase.path("name").asText())
                    .isEqualTo(testCase.path("reason").asText());
            assertThat(decision.contractVersion()).isEqualTo(RuleKernelV1.CONTRACT_VERSION);
        }
    }

    @Test
    void rejectsAnEmptyCustomEligibilityInsteadOfTreatingItAsOther() {
        assertThatThrownBy(() -> RuleKernelV1.Eligibility.only(Set.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one code");
    }

    private JsonNode fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/rule-parity/unified-rule-kernel-v1.json")) {
            if (input == null) {
                throw new IllegalStateException("Unified rule fixture is missing");
            }
            return objectMapper.readTree(input);
        }
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
