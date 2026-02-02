# Quickstart: Resilience4j 韌性機制 PoC

## Prerequisites

- Java 17+
- Gradle 8.x
- Docker（可選，用於運行 WireMock 容器）

## Quick Start

### 1. Clone & Build

```bash
git clone <repository-url>
cd resilience4j-poc

# Build project
./gradlew build
```

### 2. Run Tests

```bash
# Run all tests (includes WireMock integration tests)
./gradlew test

# Run specific test class
./gradlew test --tests "RetryIntegrationTest"
./gradlew test --tests "CircuitBreakerIntegrationTest"
./gradlew test --tests "TimeLimiterIntegrationTest"
```

### 3. Run Application

```bash
# Start the Order Service
./gradlew bootRun

# The service will be available at http://localhost:8080
```

### 4. Test Endpoints

```bash
# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"skuCode": "SKU001", "quantity": 2, "unitPrice": 1500.00}
    ],
    "shippingAddress": "台北市信義區松仁路100號"
  }'

# Check circuit breaker status
curl http://localhost:8080/actuator/health

# View resilience metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state
curl http://localhost:8080/actuator/metrics/resilience4j.retry.calls
```

## Project Structure

```
src/main/java/com/example/order/
├── domain/           # Business logic (no framework dependencies)
├── application/      # Use cases and ports
└── infrastructure/   # Adapters, configs, Resilience4j decorators

src/test/java/com/example/order/
├── integration/      # BDD-style integration tests with WireMock
└── unit/             # Unit tests for domain logic
```

## Key Configuration

All Resilience4j configurations are in `src/main/resources/application.yml`:

```yaml
resilience4j:
  retry:
    instances:
      inventoryRetry:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true

  circuitbreaker:
    instances:
      paymentCB:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s

  timelimiter:
    instances:
      shippingTL:
        timeout-duration: 3s
```

## Fault Scenarios

| Scenario | How to Trigger | Expected Behavior |
|----------|----------------|-------------------|
| Retry Success | Mock returns 503, 503, 200 | Auto-recovery after 2 retries |
| Circuit Open | 10 consecutive 500 errors | Fast fail within 50ms |
| Timeout Fallback | Mock delays 5s | Deferred shipping response |

## Monitoring

- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/prometheus`
- **Circuit Breakers**: `GET /actuator/circuitbreakers`
- **Retries**: `GET /actuator/retries`

## Troubleshooting

### Common Issues

1. **WireMock port conflict**: Ensure ports 8081-8083 are available
2. **Timeout in tests**: Increase `@Timeout` annotation values
3. **Circuit breaker not opening**: Check `minimum-number-of-calls` threshold

### Log Analysis

Look for these log patterns:
```
[RETRY] name=inventoryRetry, attempt=2
[CB_STATE] name=paymentCB, from=CLOSED, to=OPEN
[TIMEOUT] name=shippingTL, duration=3000ms
```

## Next Steps

1. Review test scenarios in `src/test/java/.../integration/`
2. Experiment with configuration parameters
3. Monitor metrics via Actuator endpoints
