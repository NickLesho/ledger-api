package io.github.nicklesho.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests: the real application on a random port, a real Postgres 17
 * container, real HTTP. Each test creates its own accounts and idempotency keys,
 * so tests are independent of each other and of execution order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferApiIT {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---------------------------------------------------------------- scenarios

    @Test
    void successfulTransferMovesMoneyAndAppearsInHistory() {
        UUID from = createAccount("100.00");
        UUID to = createAccount("0.00");

        ResponseEntity<Map<String, Object>> response = transfer(newKey(), from, to, "30.00");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).containsEntry("status", "COMPLETED").containsEntry("amount", "30.00");
        assertThat(balanceOf(from)).isEqualTo("70.00");
        assertThat(balanceOf(to)).isEqualTo("30.00");
        assertThat(historyOf(from)).hasSize(1);
        assertThat(historyOf(to)).hasSize(1);
    }

    @Test
    void insufficientFundsReturns422ProblemAndIsReplayedOnRetry() {
        UUID from = createAccount("10.00");
        UUID to = createAccount("0.00");
        String key = newKey();

        ResponseEntity<Map<String, Object>> first = transfer(key, from, to, "30.00");
        ResponseEntity<Map<String, Object>> retry = transfer(key, from, to, "30.00");

        assertThat(first.getStatusCode().value()).isEqualTo(422);
        assertThat(first.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) first.getBody().get("type")).endsWith("/insufficient-funds");

        // The rejection is recorded, so a retry gets the same answer rather than a fresh attempt.
        assertThat(retry.getStatusCode().value()).isEqualTo(422);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");

        assertThat(balanceOf(from)).isEqualTo("10.00");
        assertThat(balanceOf(to)).isEqualTo("0.00");
        assertThat(historyOf(from)).hasSize(1);
    }

    @Test
    void duplicateIdempotencyKeyReturnsOriginalResultAndMovesMoneyOnce() {
        UUID from = createAccount("100.00");
        UUID to = createAccount("0.00");
        String key = newKey();

        ResponseEntity<Map<String, Object>> first = transfer(key, from, to, "30.00");
        ResponseEntity<Map<String, Object>> second = transfer(key, from, to, "30.00");

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isNull();
        assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(balanceOf(from)).isEqualTo("70.00");
        assertThat(historyOf(from)).hasSize(1);
    }

    @Test
    void reusingKeyWithDifferentPayloadReturns409() {
        UUID from = createAccount("100.00");
        UUID to = createAccount("0.00");
        String key = newKey();
        transfer(key, from, to, "30.00");

        ResponseEntity<Map<String, Object>> response = transfer(key, from, to, "99.00");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat((String) response.getBody().get("type")).endsWith("/idempotency-key-reused");
        assertThat(balanceOf(from)).isEqualTo("70.00");
    }

    @Test
    void unknownAccountReturns404Problem() {
        ResponseEntity<Map<String, Object>> response = get("/accounts/" + UUID.randomUUID());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat((String) response.getBody().get("type")).endsWith("/account-not-found");
    }

    // ------------------------------------------------------------- concurrency

    /**
     * Two simultaneous 60.00 transfers from a 100.00 account: exactly one may succeed.
     * Without the row lock, both would read 100.00 and the second write would either
     * overdraw or fail with an optimistic-lock conflict, so this test would fail.
     */
    @Test
    void concurrentTransfersFromSameAccountNeverOverdraw() throws Exception {
        UUID from = createAccount("100.00");
        UUID to = createAccount("0.00");

        List<Integer> statuses = runConcurrently(2,
                i -> transfer(newKey(), from, to, "60.00").getStatusCode().value());

        assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        assertThat(balanceOf(from)).isEqualTo("40.00");
        assertThat(balanceOf(to)).isEqualTo("60.00");
    }

    /**
     * Two threads hammer transfers in opposite directions (A->B and B->A). Without
     * ID-ordered locking, Postgres would detect deadlocks and abort some transactions.
     * With it, every transfer succeeds and the total is conserved.
     */
    @Test
    void opposingConcurrentTransfersDoNotDeadlock() throws Exception {
        UUID a = createAccount("1000.00");
        UUID b = createAccount("1000.00");
        int transfersPerThread = 20;

        List<List<Integer>> results = runConcurrently(2, thread -> {
            List<Integer> statuses = new ArrayList<>();
            for (int n = 0; n < transfersPerThread; n++) {
                ResponseEntity<Map<String, Object>> response = thread == 0
                        ? transfer(newKey(), a, b, "1.00")
                        : transfer(newKey(), b, a, "1.00");
                statuses.add(response.getStatusCode().value());
            }
            return statuses;
        });

        assertThat(results).allSatisfy(statuses -> assertThat(statuses).containsOnly(201));
        assertThat(balanceOf(a)).isEqualTo("1000.00");
        assertThat(balanceOf(b)).isEqualTo("1000.00");
    }

    /**
     * A client retries while the original request is still in flight. Both requests
     * pass the fast-path key lookup; the second waits on the row locks, re-checks
     * the key, finds the committed transfer and replays it.
     */
    @Test
    void concurrentRetriesWithSameKeyMoveMoneyOnce() throws Exception {
        UUID from = createAccount("100.00");
        UUID to = createAccount("0.00");
        String key = newKey();

        List<ResponseEntity<Map<String, Object>>> responses =
                runConcurrently(2, i -> transfer(key, from, to, "30.00"));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode().value()).isEqualTo(201));
        assertThat(responses.get(0).getBody().get("id")).isEqualTo(responses.get(1).getBody().get("id"));
        assertThat(responses).filteredOn(r -> "true".equals(r.getHeaders().getFirst("Idempotent-Replayed")))
                .hasSize(1);
        assertThat(balanceOf(from)).isEqualTo("70.00");
        assertThat(historyOf(from)).hasSize(1);
    }

    // ------------------------------------------------------------------ helpers

    private UUID createAccount(String openingBalance) {
        ResponseEntity<Map<String, Object>> response = post("/accounts", null,
                Map.of("owner", "IT Owner", "currency", "CAD", "openingBalance", openingBalance));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private ResponseEntity<Map<String, Object>> transfer(String key, UUID from, UUID to, String amount) {
        return post("/transfers", key,
                Map.of("fromAccountId", from.toString(), "toAccountId", to.toString(), "amount", amount));
    }

    private String balanceOf(UUID accountId) {
        return (String) get("/accounts/" + accountId).getBody().get("balance");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historyOf(UUID accountId) {
        return (List<Map<String, Object>>) get("/accounts/" + accountId + "/transfers").getBody().get("content");
    }

    private ResponseEntity<Map<String, Object>> post(String path, String idempotencyKey, Map<String, ?> body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> setIdempotencyKey(headers, idempotencyKey))
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // Error statuses are asserted by the tests, not thrown.
                })
                .toEntity(JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> get(String path) {
        return client.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                })
                .toEntity(JSON_MAP);
    }

    private static void setIdempotencyKey(HttpHeaders headers, String key) {
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * Runs the task on N threads that all start at the same instant (a start-gate latch),
     * to maximize real overlap rather than relying on thread scheduling luck.
     */
    private static <T> List<T> runConcurrently(int threads, IntFunction<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.apply(index);
                }));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}