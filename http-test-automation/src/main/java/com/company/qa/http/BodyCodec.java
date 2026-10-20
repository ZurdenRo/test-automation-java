package com.company.qa.http;

/**
 * Turns model objects into request bodies and response bodies back into model objects, with one configuration for
 * both directions. The library bundles a JSON codec (Jackson) and uses it by default; implement this only to replace
 * it, e.g. to use another JSON library or special mapper settings.
 */
public interface BodyCodec {

    <T> T read(String body, Class<T> type) throws Exception;

    String write(Object model) throws Exception;
}
