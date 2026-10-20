package com.company.qa.http;

/**
 * Turns a response body into a model object. The library bundles a JSON reader (Jackson) and uses it by default;
 * implement this only to replace it, e.g. to use another JSON library or special mapper settings.
 */
public interface BodyDeserializer {

    <T> T read(String body, Class<T> type) throws Exception;
}
