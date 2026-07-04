package com.syxduorealm.war;

import com.syxduorealm.config.JsonObjectParser;
import com.syxduorealm.config.SyxDuoRealmConfig;
import com.syxduorealm.export.CityStateJsonWriter;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WarClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new WarThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final AtomicBoolean requestInFlight = new AtomicBoolean(false);
    private final AtomicReference<WarStatus> lastStatus = new AtomicReference<>(WarStatus.notRun());
    private final AtomicReference<List<WarReport>> reports = new AtomicReference<>(List.of());
    private final AtomicReference<List<WarRequest>> requests = new AtomicReference<>(List.of());
    private final AtomicReference<List<PeaceRequest>> peaceRequests = new AtomicReference<>(List.of());
    private final AtomicReference<List<DiplomacyRelation>> relations = new AtomicReference<>(List.of());
    private final AtomicReference<WarReport> latestReport = new AtomicReference<>();

    public WarStatus refreshReportsAsync(SyxDuoRealmConfig config) {
        if (!requestInFlight.compareAndSet(false, true)) {
            WarStatus status = WarStatus.inFlight("War request already in progress.");
            lastStatus.set(status);
            return status;
        }

        WarStatus started = WarStatus.inFlight("Refreshing war reports.");
        lastStatus.set(started);

        try {
            HttpRequest request = HttpRequest.newBuilder(warStatusUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeStatus, executor);
        } catch (Throwable throwable) {
            requestInFlight.set(false);
            WarStatus failed = WarStatus.failure("Could not start war report request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public WarStatus declareWarAsync(SyxDuoRealmConfig config, String defender) {
        if (!requestInFlight.compareAndSet(false, true)) {
            WarStatus status = WarStatus.inFlight("War request already in progress.");
            lastStatus.set(status);
            return status;
        }

        WarStatus started = WarStatus.inFlight("Declaring async war.");
        lastStatus.set(started);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("roomCode", config.roomCode());
            body.put("attacker", config.playerName());
            body.put("defender", defender);
            body.put("source", "game-client");

            HttpRequest request = HttpRequest.newBuilder(warUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeDeclare, executor);
        } catch (Throwable throwable) {
            requestInFlight.set(false);
            WarStatus failed = WarStatus.failure("Could not start war declaration.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public WarStatus sendWarRequestAsync(SyxDuoRealmConfig config, String toPlayer) {
        if (!requestInFlight.compareAndSet(false, true)) {
            WarStatus status = WarStatus.inFlight("War request already in progress.");
            lastStatus.set(status);
            return status;
        }

        WarStatus started = WarStatus.inFlight("Sending war request.");
        lastStatus.set(started);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("roomCode", config.roomCode());
            body.put("fromPlayer", config.playerName());
            body.put("toPlayer", toPlayer);
            body.put("source", "game-client");

            HttpRequest request = HttpRequest.newBuilder(warRequestUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeWarRequest, executor);
        } catch (Throwable throwable) {
            requestInFlight.set(false);
            WarStatus failed = WarStatus.failure("Could not start war request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public WarStatus acceptWarRequestAsync(SyxDuoRealmConfig config, WarRequest warRequest) {
        if (!requestInFlight.compareAndSet(false, true)) {
            WarStatus status = WarStatus.inFlight("War request already in progress.");
            lastStatus.set(status);
            return status;
        }

        WarStatus started = WarStatus.inFlight("Accepting war request.");
        lastStatus.set(started);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("playerName", config.playerName());

            HttpRequest request = HttpRequest.newBuilder(warRequestAcceptUri(config, warRequest.requestId()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeAcceptRequest, executor);
        } catch (Throwable throwable) {
            requestInFlight.set(false);
            WarStatus failed = WarStatus.failure("Could not start war request accept.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public WarStatus sendPeaceRequestAsync(SyxDuoRealmConfig config, String toPlayer) {
        if (!requestInFlight.compareAndSet(false, true)) {
            WarStatus status = WarStatus.inFlight("War request already in progress.");
            lastStatus.set(status);
            return status;
        }

        WarStatus started = WarStatus.inFlight("Sending peace request.");
        lastStatus.set(started);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("roomCode", config.roomCode());
            body.put("fromPlayer", config.playerName());
            body.put("toPlayer", toPlayer);
            body.put("requestedStatus", "PEACE");
            body.put("source", "game-client");

            HttpRequest request = HttpRequest.newBuilder(peaceRequestUri(config))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completePeaceRequest, executor);
        } catch (Throwable throwable) {
            requestInFlight.set(false);
            WarStatus failed = WarStatus.failure("Could not start peace request.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public WarStatus acceptPeaceRequestAsync(SyxDuoRealmConfig config, PeaceRequest peaceRequest) {
        if (!requestInFlight.compareAndSet(false, true)) {
            WarStatus status = WarStatus.inFlight("War request already in progress.");
            lastStatus.set(status);
            return status;
        }

        WarStatus started = WarStatus.inFlight("Accepting peace request.");
        lastStatus.set(started);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("playerName", config.playerName());

            HttpRequest request = HttpRequest.newBuilder(peaceRequestAcceptUri(config, peaceRequest.requestId()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(CityStateJsonWriter.writeMap(body), StandardCharsets.UTF_8))
                .build();

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenCompleteAsync(this::completeAcceptPeaceRequest, executor);
        } catch (Throwable throwable) {
            requestInFlight.set(false);
            WarStatus failed = WarStatus.failure("Could not start peace request accept.", throwable);
            lastStatus.set(failed);
            logFailure(failed, throwable);
            return failed;
        }

        return started;
    }

    public WarStatus lastStatus() {
        return lastStatus.get();
    }

    public List<WarReport> reports() {
        return reports.get();
    }

    public List<WarRequest> requests() {
        return requests.get();
    }

    public List<PeaceRequest> peaceRequests() {
        return peaceRequests.get();
    }

    public List<DiplomacyRelation> relations() {
        return relations.get();
    }

    public DiplomacyRelation relationWith(String roomCode, String playerName, String friendName) {
        return relations.get().stream()
            .filter(relation -> relation.roomCode().equals(roomCode))
            .filter(relation -> relation.involvesPair(playerName, friendName))
            .findFirst()
            .orElse(DiplomacyRelation.peace(roomCode, playerName, friendName));
    }

    public WarRequest incomingRequest(String playerName) {
        return requests.get().stream()
            .filter(request -> request.incoming(playerName))
            .findFirst()
            .orElse(null);
    }

    public WarRequest outgoingRequest(String playerName) {
        return requests.get().stream()
            .filter(request -> request.outgoing(playerName))
            .findFirst()
            .orElse(null);
    }

    public PeaceRequest incomingPeaceRequest(String playerName) {
        return peaceRequests.get().stream()
            .filter(request -> request.incoming(playerName))
            .findFirst()
            .orElse(null);
    }

    public PeaceRequest outgoingPeaceRequest(String playerName) {
        return peaceRequests.get().stream()
            .filter(request -> request.outgoing(playerName))
            .findFirst()
            .orElse(null);
    }

    public WarReport latestReport() {
        return latestReport.get();
    }

    private void completeStatus(HttpResponse<String> response, Throwable throwable) {
        requestInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            WarStatus failed = WarStatus.failure("War report request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            WarStatus failed = WarStatus.httpFailure("Server rejected war report request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] war reports FAILED: " + failed.error());
            return;
        }

        try {
            Map<String, Object> json = JsonObjectParser.parse(response.body());
            List<WarReport> parsedReports = parseReports(json.get("wars"));
            List<WarRequest> parsedRequests = parseRequests(json.get("warRequests"));
            List<PeaceRequest> parsedPeaceRequests = parsePeaceRequests(json.get("peaceRequests"));
            List<DiplomacyRelation> parsedRelations = parseDiplomacy(json.get("diplomacy"));
            reports.set(parsedReports);
            requests.set(parsedRequests);
            peaceRequests.set(parsedPeaceRequests);
            relations.set(parsedRelations);
            if (!parsedReports.isEmpty()) {
                latestReport.set(parsedReports.get(0));
            }
            WarStatus status = WarStatus.success(
                "Loaded " + parsedReports.size() + " war report(s), "
                    + parsedRequests.size() + " war request(s), "
                    + parsedPeaceRequests.size() + " peace request(s), "
                    + parsedRelations.size() + " relation(s).",
                statusCode
            );
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] war reports SUCCESS: " + status.message());
        } catch (Exception e) {
            WarStatus failed = WarStatus.failure("Could not parse war reports.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private void completeWarRequest(HttpResponse<String> response, Throwable throwable) {
        requestInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            WarStatus failed = WarStatus.failure("War request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            WarStatus failed = WarStatus.httpFailure("Server rejected war request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] war request FAILED: " + failed.error());
            return;
        }

        try {
            WarRequest request = parseRequest(JsonObjectParser.parse(response.body()).get("warRequest"));
            List<WarRequest> updated = new ArrayList<>(requests.get());
            updated.removeIf(existing -> existing.requestId().equals(request.requestId()));
            updated.add(0, request);
            requests.set(List.copyOf(updated));
            WarStatus status = WarStatus.success("War request sent to " + request.toPlayer() + ".", statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] war request SUCCESS: " + status.message());
        } catch (Exception e) {
            WarStatus failed = WarStatus.failure("Could not parse war request.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private void completePeaceRequest(HttpResponse<String> response, Throwable throwable) {
        requestInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            WarStatus failed = WarStatus.failure("Peace request failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            WarStatus failed = WarStatus.httpFailure("Server rejected peace request.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] peace request FAILED: " + failed.error());
            return;
        }

        try {
            PeaceRequest request = parsePeaceRequest(JsonObjectParser.parse(response.body()).get("peaceRequest"));
            upsertPeaceRequest(request);
            WarStatus status = WarStatus.success("Peace request sent to " + request.toPlayer() + ".", statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] peace request SUCCESS: " + status.message());
        } catch (Exception e) {
            WarStatus failed = WarStatus.failure("Could not parse peace request.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private void completeAcceptPeaceRequest(HttpResponse<String> response, Throwable throwable) {
        requestInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            WarStatus failed = WarStatus.failure("Peace request accept failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            WarStatus failed = WarStatus.httpFailure("Server rejected peace request accept.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] peace accept FAILED: " + failed.error());
            return;
        }

        try {
            Map<String, Object> json = JsonObjectParser.parse(response.body());
            PeaceRequest request = parsePeaceRequest(json.get("peaceRequest"));
            upsertPeaceRequest(request);

            Object relationValue = json.get("relation");
            String message = "Peace request accepted.";
            if (relationValue instanceof Map<?, ?>) {
                DiplomacyRelation relation = parseRelation(relationValue);
                upsertRelation(relation);
                message = "Peace request accepted: relation is " + relation.status() + ".";
            }

            WarStatus status = WarStatus.success(message, statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] peace accept SUCCESS: " + status.message());
        } catch (Exception e) {
            WarStatus failed = WarStatus.failure("Could not parse accepted peace result.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private void completeAcceptRequest(HttpResponse<String> response, Throwable throwable) {
        requestInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            WarStatus failed = WarStatus.failure("War request accept failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            WarStatus failed = WarStatus.httpFailure("Server rejected war request accept.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] war accept FAILED: " + failed.error());
            return;
        }

        try {
            Map<String, Object> json = JsonObjectParser.parse(response.body());
            WarRequest request = parseRequest(json.get("warRequest"));

            List<WarRequest> updatedRequests = new ArrayList<>(requests.get());
            updatedRequests.removeIf(existing -> existing.requestId().equals(request.requestId()));
            updatedRequests.add(0, request);
            requests.set(List.copyOf(updatedRequests));

            Object war = json.get("war");
            String message = "War request accepted.";
            if (war instanceof Map<?, ?>) {
                WarReport report = parseReport(war);
                latestReport.set(report);
                List<WarReport> updatedReports = new ArrayList<>(reports.get());
                updatedReports.removeIf(existing -> existing.warId().equals(report.warId()));
                updatedReports.add(0, report);
                reports.set(List.copyOf(updatedReports));
                upsertRelation(DiplomacyRelation.warFromReport(report));
                message = "War request accepted: " + report.summary();
            }

            WarStatus status = WarStatus.success(message, statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] war accept SUCCESS: " + status.message());
        } catch (Exception e) {
            WarStatus failed = WarStatus.failure("Could not parse accepted war result.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private void completeDeclare(HttpResponse<String> response, Throwable throwable) {
        requestInFlight.set(false);

        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            WarStatus failed = WarStatus.failure("War declaration failed.", cause);
            lastStatus.set(failed);
            logFailure(failed, cause);
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            WarStatus failed = WarStatus.httpFailure("Server rejected war declaration.", statusCode, response.body());
            lastStatus.set(failed);
            System.err.println("[Syx Duo Realm] war declaration FAILED: " + failed.error());
            return;
        }

        try {
            WarReport report = parseReport(JsonObjectParser.parse(response.body()).get("war"));
            latestReport.set(report);
            List<WarReport> updated = new ArrayList<>(reports.get());
            updated.removeIf(existing -> existing.warId().equals(report.warId()));
            updated.add(0, report);
            reports.set(List.copyOf(updated));
            upsertRelation(DiplomacyRelation.warFromReport(report));
            WarStatus status = WarStatus.success("War resolved: " + report.summary(), statusCode);
            lastStatus.set(status);
            System.out.println("[Syx Duo Realm] war declaration SUCCESS: " + status.message());
        } catch (Exception e) {
            WarStatus failed = WarStatus.failure("Could not parse war result.", e);
            lastStatus.set(failed);
            logFailure(failed, e);
        }
    }

    private List<WarReport> parseReports(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<WarReport> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(parseReport(item));
            }
        }
        return List.copyOf(out);
    }

    private List<WarRequest> parseRequests(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<WarRequest> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(parseRequest(item));
            }
        }
        return List.copyOf(out);
    }

    private List<PeaceRequest> parsePeaceRequests(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<PeaceRequest> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(parsePeaceRequest(item));
            }
        }
        return List.copyOf(out);
    }

    private List<DiplomacyRelation> parseDiplomacy(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }

        List<DiplomacyRelation> out = new ArrayList<>();
        Object relationsValue = map.get("relations");
        if (relationsValue instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    out.add(parseRelation(item));
                }
            }
        }

        Object targetRelation = map.get("targetRelation");
        if (targetRelation instanceof Map<?, ?> target) {
            DiplomacyRelation relation = parseRelation(target);
            out.removeIf(existing -> existing.relationId().equals(relation.relationId()));
            out.add(0, relation);
        }

        return List.copyOf(out);
    }

    private void upsertPeaceRequest(PeaceRequest request) {
        List<PeaceRequest> updated = new ArrayList<>(peaceRequests.get());
        updated.removeIf(existing -> existing.requestId().equals(request.requestId()));
        updated.add(0, request);
        peaceRequests.set(List.copyOf(updated));
    }

    private void upsertRelation(DiplomacyRelation relation) {
        List<DiplomacyRelation> updated = new ArrayList<>(relations.get());
        updated.removeIf(existing -> existing.relationId().equals(relation.relationId()));
        updated.add(0, relation);
        relations.set(List.copyOf(updated));
    }

    private WarRequest parseRequest(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("warRequest must be an object");
        }
        return new WarRequest(
            stringValue(map.get("requestId")),
            stringValue(map.get("roomCode")),
            stringValue(map.get("fromPlayer")),
            stringValue(map.get("toPlayer")),
            stringValue(map.get("status")),
            stringValue(map.get("createdAt")),
            stringValue(map.get("updatedAt")),
            stringValue(map.get("resolvedWarId")),
            stringValue(map.get("note"))
        );
    }

    private PeaceRequest parsePeaceRequest(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("peaceRequest must be an object");
        }
        return new PeaceRequest(
            stringValue(map.get("requestId")),
            stringValue(map.get("roomCode")),
            stringValue(map.get("fromPlayer")),
            stringValue(map.get("toPlayer")),
            stringValue(map.get("requestedStatus")),
            stringValue(map.get("status")),
            stringValue(map.get("createdAt")),
            stringValue(map.get("updatedAt")),
            stringValue(map.get("note"))
        );
    }

    private DiplomacyRelation parseRelation(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("relation must be an object");
        }
        return new DiplomacyRelation(
            stringValue(map.get("relationId")),
            stringValue(map.get("roomCode")),
            stringValue(map.get("playerA")),
            stringValue(map.get("playerB")),
            stringValue(map.get("status")),
            stringValue(map.get("source")),
            stringValue(map.get("createdAt")),
            stringValue(map.get("updatedAt")),
            stringValue(map.get("lastWarId"))
        );
    }

    private WarReport parseReport(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("war must be an object");
        }
        return new WarReport(
            stringValue(map.get("warId")),
            stringValue(map.get("roomCode")),
            stringValue(map.get("attacker")),
            stringValue(map.get("defender")),
            stringValue(map.get("winner")),
            stringValue(map.get("loser")),
            intValue(map.get("attackerScore")),
            intValue(map.get("defenderScore")),
            intValue(map.get("attackerRoll")),
            intValue(map.get("defenderRoll")),
            intValue(map.get("attackerEstimatedLosses")),
            intValue(map.get("defenderEstimatedLosses")),
            intValue(map.get("margin")),
            stringValue(map.get("summary")),
            stringValue(map.get("createdAt"))
        );
    }

    private URI warUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/war", null, null);
    }

    private URI warReportsUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        String query = "roomCode=" + encodeQuery(config.roomCode()) + "&playerName=" + encodeQuery(config.playerName());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/war_reports", query, null);
    }

    private URI warStatusUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        String query = "roomCode=" + encodeQuery(config.roomCode()) + "&playerName=" + encodeQuery(config.playerName());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/war_status", query, null);
    }

    private URI warRequestUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/war_request", null, null);
    }

    private URI warRequestAcceptUri(SyxDuoRealmConfig config, String requestId) {
        URI server = URI.create(config.serverUrl());
        String path = apiBasePath(server) + "/war_request/" + encodePathSegment(requestId) + "/accept";
        return URI.create(server.getScheme() + "://" + server.getAuthority() + path);
    }

    private URI peaceRequestUri(SyxDuoRealmConfig config) throws URISyntaxException {
        URI server = URI.create(config.serverUrl());
        return new URI(server.getScheme(), server.getAuthority(), apiBasePath(server) + "/peace_request", null, null);
    }

    private URI peaceRequestAcceptUri(SyxDuoRealmConfig config, String requestId) {
        URI server = URI.create(config.serverUrl());
        String path = apiBasePath(server) + "/peace_request/" + encodePathSegment(requestId) + "/accept";
        return URI.create(server.getScheme() + "://" + server.getAuthority() + path);
    }

    private String apiBasePath(URI server) {
        String path = server.getPath();
        if (path == null || path.isBlank()) {
            return "/api";
        }
        if (path.endsWith("/state")) {
            return path.substring(0, path.length() - "/state".length());
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private String encodeQuery(String value) {
        return value
            .replace("%", "%25")
            .replace(" ", "%20")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace("?", "%3F");
    }

    private String encodePathSegment(String value) {
        return encodeQuery(value)
            .replace("/", "%2F")
            .replace("#", "%23");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void logFailure(WarStatus status, Throwable throwable) {
        System.err.println("[Syx Duo Realm] war FAILED: " + status.message());
        if (status.error() != null) {
            System.err.println("[Syx Duo Realm] war error: " + status.error());
        }
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static final class WarThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Syx Duo Realm War HTTP");
            thread.setDaemon(true);
            return thread;
        }
    }
}
