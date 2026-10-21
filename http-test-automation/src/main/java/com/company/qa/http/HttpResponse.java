package com.company.qa.http;

import java.net.URI;

/** What came back from the server, whatever its status. */
public final class HttpResponse {

    private static final int BODY_EXCERPT = 200;

    private final int status;
    private final String body;
    private final URI finalUri;
    private final Headers headers;
    private final BodyCodec codec;

    HttpResponse(int status, String body, URI finalUri, Headers headers,
                 BodyCodec codec) {
        this.status = status;
        this.body = body;
        this.finalUri = finalUri;
        this.headers = headers;
        this.codec = codec;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }

    /** The URI that produced this response, after any redirects that were followed. */
    public URI finalUri() {
        return finalUri;
    }

    public Headers headers() {
        return headers;
    }

    /**
     * Deserializes the body with the service client's {@link BodyCodec}.
     *
     * @throws HttpTransportException if the body cannot be read as {@code type}
     */
    public <T> T bodyAs(Class<T> type) {
        try {
            return codec.read(body, type);
        } catch (Exception e) {
            String shown = body.length() > BODY_EXCERPT ? body.substring(0, BODY_EXCERPT) + "..." : body;
            throw new HttpTransportException("HTTP " + status + " from " + finalUri + ": could not read the body as "
                    + type.getSimpleName() + " (" + e.getMessage() + "). Body: " + shown, e);
        }
    }
}
