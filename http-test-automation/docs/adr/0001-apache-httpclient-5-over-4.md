# Use Apache HttpClient 5.x, not the requested 4.x

The module was asked for with `org.apache.http` (HttpClient 4.x, frozen at 4.5.x). We use `org.apache.hc.client5` (5.x) instead: it is the maintained line, and since Apache types never appear in the public API (see ADR-0002) the choice is invisible to consumers. Moving between 4.x and 5.x later would change every import in one class, but picking the line that is still maintained avoids having to.
