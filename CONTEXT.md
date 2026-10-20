# HTTP Test Automation

A published library that lets test projects talk to HTTP services through one shared, pooled connection layer, without depending on the underlying HTTP library.

## Language

**Service client**:
A consumer project's own class, written by the consumer, that extends the library's base class and exposes its service's endpoints as methods (e.g. `getUser(42)`). Tests talk to it, never to raw paths.
_Avoid_: Transport, base client, API wrapper

**Transport**:
The library's internal object behind a service client: bound to one base URL, it executes requests over a pool of reusable connections. Consumers never see it.
_Avoid_: Client, HttpClient

**Base URL**:
The scheme, host and optional path prefix a transport is bound to. Requests name only a path relative to it.
_Avoid_: Host, endpoint, root URL

**Request**:
What a service client sends: a path relative to the base URL, plus query params, headers and, except for GET and DELETE, a body. Whatever it does not set (default headers, timeouts) comes from the service config; whatever it sets wins.
_Avoid_: Request options, call

**Service config**:
A `.properties` resource in the consumer's classpath, named by the consumer, holding one transport's settings. A consumer with several services has several.
_Avoid_: Settings file, client config

**Response**:
What came back from the server, whatever its status. A 404 is a response, not a failure.
_Avoid_: Result, reply

**Transport failure**:
No response was obtained at all: unreachable host, refused connection, timeout. A response with an error status is not one.
_Avoid_: HTTP error, request error

**Body codec**:
The component that turns model objects into request bodies and response bodies back into model objects, with one configuration for both directions. The library uses JSON by default; a consumer may replace it with its own.
_Avoid_: Body deserializer, mapper, parser, converter

**Query params**:
The ordered name/value pairs appended to a request path, encoded by the library so callers never build a query string by hand.
_Avoid_: URL params
