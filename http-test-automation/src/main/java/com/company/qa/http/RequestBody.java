package com.company.qa.http;

import java.util.Objects;

/** The body of a POST, PUT or PATCH. Its content type is sent as {@code Content-Type} unless the request sets one. */
public final class RequestBody {

    private final String text;
    private final Object model;
    private final String contentType;

    private RequestBody(String text, Object model, String contentType) {
        this.text = text;
        this.model = model;
        this.contentType = contentType;
    }

    /** {@code content} sent as is, encoded as UTF-8. */
    public static RequestBody text(String content, String contentType) {
        return new RequestBody(Objects.requireNonNull(content, "content"), null,
                Objects.requireNonNull(contentType, "contentType"));
    }

    /** {@code model} written as JSON by the service client's {@link BodyCodec} when the request is sent. */
    public static RequestBody json(Object model) {
        return new RequestBody(null, Objects.requireNonNull(model, "model"), "application/json");
    }

    /** The text to send: given as is, or written from the model with {@code codec}. */
    String content(BodyCodec codec) throws Exception {
        return text != null ? text : codec.write(model);
    }

    /** For error messages: the type of the model a JSON body is written from. */
    String modelTypeName() {
        return model == null ? "text" : model.getClass().getSimpleName();
    }

    String contentType() {
        return contentType;
    }
}
