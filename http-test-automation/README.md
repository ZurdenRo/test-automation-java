# http-test-automation

Talk to an HTTP service from tests through one pooled connection layer. You write a **service client** for your service; tests call its endpoint methods, never raw paths.

## A service client

```java
public class UsersClient extends HttpServiceClient {

    public UsersClient(String env) {
        super("users", env);   // services.users in test-automation-<env>.yaml on the classpath
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

Tokens, users and passwords live in environment variables, never in the config. The service's section names the variable that holds each secret under `secrets`, and the client asks for it by that name:

```yaml
# test-automation-qa.yaml
services:
  payments:
    base-url: https://payments.qa.example.com
    secrets:
      api-token: PAYMENTS_QA_TOKEN
```

```java
public HttpResponse getInvoice(int id) {
    return get(HttpRequest.to("/invoices/" + id).header("Authorization", "Bearer " + secret("api-token")));
}
```

The file of another environment (`test-automation-dev.yaml` with `api-token: PAYMENTS_DEV_TOKEN`) points the same client at that environment's variable. A missing `services.<service>.secrets.<name>` key or an unset variable fails the call, naming the key or the variable and never the value.

## Configuration

Settings live in YAML on the test classpath (`src/test/resources`): `test-automation-<env>.yaml` for each environment, laid over an optional `test-automation.yaml` with what every environment shares, each service under `services.<name>`. Other modules read their own sections of the same files. A test passes the suite's environment to the client (`new UsersClient(environment())` in a UI test; the library does not know TestNG).

```yaml
# test-automation.yaml                      # test-automation-qa.yaml
services:                                   services:
  users:                                      users:
    response-timeout-ms: 60000                  base-url: https://users.qa.example.com
    headers:
      Accept: application/json
```

| Key under `services.<name>` | Default | |
|---|---|---|
| `base-url` | | Required, unless the builder gives it. |
| `connect-timeout-ms` | `10000` | |
| `response-timeout-ms` | `30000` | |
| `max-connections` | `20` | |
| `follow-redirects` | `true` | |
| `headers.<Name>` | | Sent with every request. |
| `secrets.<name>` | | The environment variable that holds a secret. |

A value given to `HttpTransportBuilder` beats the file, which beats the default. A key that is not in the table fails the build of the client, naming the key and the file. Environment variables are not read for settings: they are for secrets only (see `docs/adr/0001` at the repository root). Pass a builder to the constructor for anything beyond the config:

```java
super(new HttpTransportBuilder()
        .service("users", env)
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
INFO  Service client on https://payments.qa.example.com (base-url from test-automation-qa.yaml)
DEBUG GET https://payments.qa.example.com/invoices?customer=…&page=… -> 200 in 41 ms
```

Values that may be secret are never logged: not secrets, not header values, not query values.
