# http-test-automation

Talk to an HTTP service from tests through one pooled connection layer. You write a **service client** for your service; tests call its endpoint methods, never raw paths.

## A service client

```java
public class UsersClient extends HttpServiceClient {

    public UsersClient() {
        super("users-service.properties");   // service config on the classpath
    }

    public HttpResponse getUser(int id) {
        return get("/api/v1/users/" + id);
    }

    public HttpResponse findUsers(String name) {
        return get(HttpRequest.to("/api/v1/users")
                .query("name", name)
                .header("X-Trace", "t1"));
    }

    public HttpResponse createUser(User user) {
        return post(HttpRequest.to("/api/v1/users").body(RequestBody.json(user)));
    }

    public HttpResponse cancelJob(int id) {
        return post("/jobs/" + id + "/cancel");   // no body
    }

    public HttpResponse deleteUser(int id) {
        return delete("/api/v1/users/" + id);
    }
}
```

`post`, `put` and `patch` take an optional body: `RequestBody.json(model)` is written with the client's body codec and sent as `application/json`; `RequestBody.text(content, contentType)` is sent as is. A `Content-Type` header on the request wins over the body's. `get` and `delete` reject a body.

Create one per suite and close it in teardown (or use try-with-resources): it owns the connection pool.

## Conventions

- **Return `HttpResponse`, not the model.** The test keeps the status and headers, so it can assert a 404 or a 401; it reads the model when it needs it with `response.bodyAs(User.class)`.
- **Dynamic headers live in your client.** `defaultHeaders(...)` is fixed when the client is built. For a value that changes, such as a renewing token, build it per request in your subclass:

  ```java
  public HttpResponse getUser(int id) {
      return get(HttpRequest.to("/api/v1/users/" + id).header("Authorization", "Bearer " + token()));
  }
  ```

## Secrets

Tokens, users and passwords live in environment variables, never in a service config. The service config names the variable that holds each secret, and the client asks for it by that name:

```properties
# payments-qa.properties
base.url=https://payments.qa.example.com
secret.api.token=PAYMENTS_QA_TOKEN
```

```java
public HttpResponse getInvoice(int id) {
    return get(HttpRequest.to("/invoices/" + id).header("Authorization", "Bearer " + secret("api.token")));
}
```

A config per environment (`payments-dev.properties` with `secret.api.token=PAYMENTS_DEV_TOKEN`) points the same client at that environment's variable. A missing `secret.<name>` key or an unset variable fails the call, naming the key or the variable and never the value.

## Configuration

Settings come from the service config and `HttpTransportBuilder`; a value given to the builder beats the service config, which beats the default. Environment variables are not read for settings: they are for secrets only (see `docs/adr/0001` at the repository root). Pass a builder to the constructor for anything beyond the config file:

```java
super(new HttpTransportBuilder()
        .configResource("users-service.properties")
        .defaultHeaders(Headers.of("Accept", "application/json"))
        .bodyCodec(myCodec));               // optional: JSON with Jackson by default
```

## Logging

The library logs through SLF4J under `com.company.qa.http`; add a backend to see the logs, such as `ch.qos.logback:logback-classic` as a test dependency. Without one, SLF4J prints a single warning and the logs are dropped.

| Level | What |
|---|---|
| `INFO` | Each service client created: its base URL and where it came from. |
| `DEBUG` | The settings it resolved, with default header names; each request, with status and time; the variable each secret was read from. |

```text
INFO  Service client on https://payments.qa.example.com (base.url from service config 'payments-qa.properties')
DEBUG GET https://payments.qa.example.com/invoices?customer=…&page=… -> 200 in 41 ms
```

Values that may be secret are never logged: not secrets, not header values, not query values.
