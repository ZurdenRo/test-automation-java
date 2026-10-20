package com.company.qa.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads and writes JSON. Unknown JSON fields are ignored so that a new field in the API does not break every test. */
final class JacksonBodyCodec implements BodyCodec {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public <T> T read(String body, Class<T> type) throws Exception {
        return mapper.readValue(body, type);
    }

    @Override
    public String write(Object model) throws Exception {
        return mapper.writeValueAsString(model);
    }
}
