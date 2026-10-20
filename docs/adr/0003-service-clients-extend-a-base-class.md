# Consumers extend `HttpServiceClient`, whose `get` methods are protected

A consumer's service client (`MyServiceClient`) extends the library's abstract `HttpServiceClient` instead of holding a transport it builds itself. The base class owns the connection pool and the config, inherits `close()`, and exposes `get(...)` as `protected`, so tests call `client.getUser(42)` and never raw paths. The raw transport (`HttpTransport`, its builder's `build()`) is package-private: the base class is the only way in, which is what makes `protected` mean something.

We first chose composition: a public interface plus a builder, and the consumer holds a field. The consumer asked for inheritance instead, to write less (no field, no `close()`), and `protected` recovers the encapsulation that composition gave.

## Considered options

- **Composition (rejected)**: nothing the consumer inherits becomes its API, and a transport can be swapped for a fake. But every client repeats the field, the builder call and `close()`.
- **Inheritance with public `get` (rejected)**: tests could bypass the client's endpoint methods and hardcode paths.

## Consequences

- Every `protected` member of `HttpServiceClient` is API we cannot change without breaking subclasses owned by other teams. Keep that surface to the four `get` overloads, `close()` and two constructors.
- A consumer client can extend no other class.
- There is no public seam to substitute the transport; a consumer test that needs a fake server uses a real local one, as ours do.
- Supersedes the composition shape (interface + builder) agreed earlier; ADR-0002 still holds, since `HttpServiceClient` exposes no third-party type.
