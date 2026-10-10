package com.agentflow.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class GitHubTicketClientTest {
    final ObjectMapper json = new ObjectMapper();
    HttpServer server;
    GitHubTicketClient client;
    AtomicInteger requests;
    final TicketPolicy.Arguments args = new TicketPolicy.Arguments("Dhruv-Sharma29/test", "Cannot log in", "Issue body\n<!-- agentflow-ticket:test -->");
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requests = new AtomicInteger();
        client = new GitHubTicketClient(new GitHubTicketConfig(true, args.repository(), "test-token"), json,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }
    @AfterEach void close() { server.stop(0); }
    void respond(int status, String body) {
        server.createContext("/repos/Dhruv-Sharma29/test/issues", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            if (exchange.getRequestMethod().equals("POST")) {
                var data = json.readTree(exchange.getRequestBody());
                assertThat(data.size()).isEqualTo(2);
                assertThat(data.path("title").asText()).isEqualTo(args.title());
                assertThat(data.path("body").asText()).isEqualTo(args.body());
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    String issue() throws Exception {
        return json.writeValueAsString(Map.of("number", 42, "title", args.title(), "body", args.body(),
                "html_url", "https://github.com/Dhruv-Sharma29/test/issues/42"));
    }
    @Test void sendsOnlyFrozenFieldsAndReturnsTrustedIssueLink() throws Exception {
        respond(201, issue());
        var result = client.create(args, Instant.now().plusSeconds(10));
        assertThat(result.number()).isEqualTo(42);
        assertThat(result.url()).isEqualTo("https://github.com/Dhruv-Sharma29/test/issues/42");
        assertThat(requests).hasValue(1);
    }
    @Test void serverErrorIsUnknownAndNeverRetried() {
        respond(503, "secret remote message");
        assertThatThrownBy(() -> client.create(args, Instant.now().plusSeconds(10)))
                .isInstanceOf(GitHubTicketClient.Failure.class).hasMessage("TICKET_OUTCOME_UNKNOWN");
        assertThat(requests).hasValue(1);
    }
    @Test void rejectsCredentialsWithoutLeakingRemoteMessages() {
        respond(401, "test-token secret");
        assertThatThrownBy(() -> client.create(args, Instant.now().plusSeconds(10))).hasMessage("TICKET_AUTHENTICATION");
        assertThat(requests).hasValue(1);
    }
    @Test void rejectsRedirectsAsUnknown() {
        respond(302, "redirect");
        assertThatThrownBy(() -> client.create(args, Instant.now().plusSeconds(10))).hasMessage("TICKET_OUTCOME_UNKNOWN");
        assertThat(requests).hasValue(1);
    }
    @Test void responseSizeIsBounded() {
        respond(201, "x".repeat(70_000));
        assertThatThrownBy(() -> client.create(args, Instant.now().plusSeconds(10))).hasMessage("TICKET_OUTCOME_UNKNOWN");
    }
    @Test void expiresBeforeDispatch() {
        respond(201, "{}");
        assertThatThrownBy(() -> client.create(args, Instant.now().minusSeconds(1))).hasMessage("TICKET_NOT_DISPATCHED");
        assertThat(requests).hasValue(0);
    }
    @Test void reconcilesWithGetAndRejectsWrongPayload() throws Exception {
        respond(200, issue());
        assertThat(client.find(args, 42).number()).isEqualTo(42);
        assertThatThrownBy(() -> client.find(new TicketPolicy.Arguments(args.repository(), "Other", args.body()), 42))
                .hasMessage("TICKET_RECONCILIATION_MISMATCH");
        assertThat(requests).hasValue(2);
    }
    @Test void maliciousLinkCannotBecomeResult() throws Exception {
        var issue = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(issue());
        issue.put("html_url", "https://evil.example/steal"); respond(201, issue.toString());
        assertThatThrownBy(() -> client.create(args, Instant.now().plusSeconds(10))).hasMessage("TICKET_OUTCOME_UNKNOWN");
    }

    @Test void malformedSuccessIsUnknown() {
        respond(201, "not json");
        assertThatThrownBy(() -> client.create(args,Instant.now().plusSeconds(10))).hasMessage("TICKET_OUTCOME_UNKNOWN");
        assertThat(requests).hasValue(1);
    }
    @Test void trailingJsonCannotConfirmAnIssue() throws Exception {
        respond(201, issue()+" {}");
        assertThatThrownBy(() -> client.create(args,Instant.now().plusSeconds(10))).hasMessage("TICKET_OUTCOME_UNKNOWN");
    }
    @Test void duplicateJsonCannotConfirmAnIssue() throws Exception {
        respond(201,issue().replaceFirst("\\{", "{\"number\":42,"));
        assertThatThrownBy(() -> client.create(args,Instant.now().plusSeconds(10))).hasMessage("TICKET_OUTCOME_UNKNOWN");
    }
    @Test void stalledResponseExpiresWithoutAnotherDispatch() {
        server.createContext("/repos/Dhruv-Sharma29/test/issues", exchange -> {
            requests.incrementAndGet();
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        }); server.start();
        long started=System.nanoTime();
        assertThatThrownBy(() -> client.create(args,Instant.now().plusMillis(200))).hasMessage("TICKET_OUTCOME_UNKNOWN");
        assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(2));
        assertThat(requests).hasValue(1);
    }
    @Test void disabledOrWrongDestinationNeverReceivesCredentials() {
        respond(201, "{}");
        var disabled=new GitHubTicketClient(new GitHubTicketConfig(false,args.repository(),"test-token"),json,URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
        assertThatThrownBy(() -> disabled.create(args,Instant.now().plusSeconds(10))).hasMessage("TICKET_CONNECTOR_DISABLED");
        assertThatThrownBy(() -> client.create(new TicketPolicy.Arguments("attacker/repo",args.title(),args.body()),Instant.now().plusSeconds(10))).hasMessage("TICKET_DESTINATION_DENIED");
        assertThat(requests).hasValue(0);
    }
}
