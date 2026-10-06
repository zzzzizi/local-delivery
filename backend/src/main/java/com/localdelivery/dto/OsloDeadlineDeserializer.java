package com.localdelivery.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public class OsloDeadlineDeserializer extends JsonDeserializer<OffsetDateTime> {
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (OffsetDateTime) context.handleUnexpectedToken(OffsetDateTime.class, parser);
        }
        String value = parser.getText();
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime local = LocalDateTime.parse(value);
                var offsets = OSLO.getRules().getValidOffsets(local);
                if (offsets.size() != 1) {
                    return (OffsetDateTime) context.handleWeirdStringValue(OffsetDateTime.class, value,
                            "Use an explicit offset for an ambiguous or nonexistent Oslo time.");
                }
                return local.atOffset(offsets.getFirst()).withOffsetSameInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException error) {
                return (OffsetDateTime) context.handleWeirdStringValue(OffsetDateTime.class, value,
                        "Deadline must be an ISO date and time.");
            }
        }
    }
}
