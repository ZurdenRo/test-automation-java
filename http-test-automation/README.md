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
}
```

Create one per suite and close it in teardown (or use try-with-resources): it owns the connection pool.

## Conventions

- **Return `HttpResponse`, not the model.** The test keeps the status and headers, so it can assert a 404 or a 401; it reads the model when it needs it with `response.bodyAs(User.class)`.
- **Dynamic headers live in your client.** `defaultHeaders(...)` is fixed when the client is built. For a value that changes, such as a renewing token, build it per request in your subclass:

  ```java
  public HttpResponse getUser(int id) {
      return get(HttpRequest.to("/api/v1/users/" + id).header("Authorization", "Bearer " + token()));
  }
  ```

## Configuration

Settings come from the service config, environment variables and `HttpTransportBuilder`; a value given to the builder beats an environment variable, which beats the service config, which beats the default. Pass a builder to the constructor for anything beyond the config file:

```java
super(new HttpTransportBuilder()
        .configResource("users-service.properties")
        .defaultHeaders(Headers.of("Accept", "application/json"))
        .bodyCodec(myCodec));               // optional: JSON with Jackson by default
```
