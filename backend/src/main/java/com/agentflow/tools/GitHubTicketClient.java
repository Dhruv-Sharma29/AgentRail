package com.agentflow.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Fixed HTTPS destination, no redirects or creation retries, bounded body and deadline. */
@Component
public class GitHubTicketClient {
    public record Result(int number, String url) {}
    public static class Failure extends RuntimeException { public Failure(String code) { super(code); } }
    private final GitHubTicketConfig config;
    private final ObjectMapper json;
    private final URI api;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    @Autowired public GitHubTicketClient(GitHubTicketConfig config, ObjectMapper json) {
        this(config, json, URI.create("https://api.github.com"));
    }
    // Only package-local tests can substitute a loopback HTTP server.
    GitHubTicketClient(GitHubTicketConfig config, ObjectMapper json, URI api) { this.config=config; this.json=json; this.api=api; }
    public Result create(TicketPolicy.Arguments args, Instant deadline) {
        if (!Instant.now().isBefore(deadline)) throw new Failure("TICKET_NOT_DISPATCHED");
        return request(args, null, deadline);
    }
    public Result find(TicketPolicy.Arguments args, int number) {
        if (number < 1) throw new Failure("TICKET_RECONCILIATION_MISMATCH");
        return request(args, number, Instant.now().plusSeconds(10));
    }
    private Result request(TicketPolicy.Arguments args, Integer number, Instant deadline) {
        new TicketPolicy(config).validate(args, "USER");
        String failure = number == null ? "TICKET_OUTCOME_UNKNOWN" : "TICKET_RECONCILIATION_FAILED";
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            long millis = Math.min(10_000, Duration.between(Instant.now(), deadline).toMillis());
            if (millis <= 0) throw new Failure(number == null ? "TICKET_NOT_DISPATCHED" : failure);
            var request = HttpRequest.newBuilder(api.resolve("/repos/" + args.repository() + "/issues" + (number == null ? "" : "/" + number)))
                    .timeout(Duration.ofMillis(millis)).header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + config.token()).header("X-GitHub-Api-Version", "2026-03-10")
                    .header("User-Agent", "AgentFlow");
            if (number == null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(Map.of("title", args.title(), "body", args.body()))));
            else request.GET();
            pending = http.sendAsync(request.build(), info -> new LimitedBody());
            var response = pending.get(millis, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (number == null && (status == 401 || status == 403)) throw new Failure("TICKET_AUTHENTICATION");
            if (number == null && Set.of(400,404,410,422,429).contains(status)) throw new Failure("TICKET_REJECTED");
            if (status != (number == null ? 201 : 200)) throw new Failure(failure);
            com.fasterxml.jackson.databind.JsonNode value = json.readerFor(com.fasterxml.jackson.databind.JsonNode.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(response.body());
            if (value == null || !value.path("number").isIntegralNumber() || !value.path("number").canConvertToInt()) throw new Failure(failure);
            int found = value.path("number").asInt();
            String url = "https://github.com/" + args.repository() + "/issues/" + found;
            if (found <= 0 || value.has("pull_request") || !url.equals(value.path("html_url").asText())
                    || !args.title().equals(value.path("title").asText()) || !args.body().equals(value.path("body").asText())
                    || (number != null && number != found)) throw new Failure(number == null ? failure : "TICKET_RECONCILIATION_MISMATCH");
            return new Result(found, url);
        } catch (Failure safe) { throw safe; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new Failure(failure); }
        catch (Exception unknown) { throw new Failure(failure); }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 65_536 - bytes.size()) { subscription.cancel(); body.completeExceptionally(new IllegalStateException()); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { body.completeExceptionally(new IllegalStateException()); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
