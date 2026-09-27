# Prometheus graphs

Open <http://localhost:9090/query>, select the **Graph** tab, paste a PromQL
expression below, and choose a time range. The **Table** tab shows the current
values. Prometheus evaluates the recording rules every 15 seconds and retains
data for seven days. Rate and latency graphs need at least five minutes of
samples and some HTTP traffic before they become meaningful.

| Graph | PromQL expression | Unit |
|---|---|---|
| Scrape availability | `up{job=~"oauth|keycloak|prometheus"}` | 1 = up, 0 = down |
| HTTP requests by service and status | `sum by (job, status) (service:http_requests:rate5m)` | requests/second |
| HTTP requests by route | `sum by (job, method, uri) (service:http_requests:rate5m{uri!~"/health|/metrics|/actuator/.*"})` | requests/second |
| HTTP 5xx errors | `service:http_errors:rate5m` | errors/second |
| HTTP error percentage | `100 * service:http_errors:rate5m / clamp_min(sum by (job) (service:http_requests:rate5m), 0.000001)` | percent |
| Average HTTP duration | `service:http_request_duration:avg5m` | seconds |
| OAuth HTTP p95 duration by route | `oauth:http_request_duration:p95_5m` | seconds |
| OAuth HTTP p99 duration by route | `oauth:http_request_duration:p99_5m` | seconds |
| OAuth heap usage | `sum(jvm_memory_used_bytes{job="oauth",area="heap"})` | bytes |
| Keycloak heap usage | `base_memory_usedHeap_bytes{job="keycloak"}` | bytes |
| OAuth CPU usage | `process_cpu_usage{job="oauth"} * 100` | percent of one CPU |
| Keycloak CPU usage | `base_cpu_processCpuLoad{job="keycloak"} * 100` | percent of one CPU |
| OAuth JVM threads | `jvm_threads_live_threads{job="oauth"}` | threads |
| Keycloak JVM threads | `base_thread_count{job="keycloak"}` | threads |
| OAuth garbage collection time | `sum(rate(jvm_gc_pause_seconds_sum{job="oauth"}[5m]))` | seconds/second |
| Prometheus scrape duration | `scrape_duration_seconds{job=~"oauth|keycloak|prometheus"}` | seconds |
| Prometheus samples per scrape | `scrape_samples_scraped{job=~"oauth|keycloak|prometheus"}` | samples |

Use the metric browser in the Prometheus UI to explore every exported series.
The native interface can graph arbitrary PromQL queries and compare them over
time, but it does not provide persistent multi-panel dashboards. Query results
also depend on whether the application has emitted the relevant metric: for
example, a route-specific graph stays empty until that route receives traffic.
