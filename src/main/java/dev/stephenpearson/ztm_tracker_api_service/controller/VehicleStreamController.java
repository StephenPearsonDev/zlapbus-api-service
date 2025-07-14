package dev.stephenpearson.ztm_tracker_api_service.controller;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocation;
import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocationList;
import dev.stephenpearson.ztm_tracker_api_service.cache.VehicleCache;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class VehicleStreamController {

    private static final Logger log = LoggerFactory.getLogger(VehicleStreamController.class);

    private final VehicleCache cache;

    public VehicleStreamController(VehicleCache cache) {
        this.cache = cache;
    }

    @GetMapping(value = "/sse/vehicles", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamVehicles(
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> lines,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        return Flux.interval(Duration.ZERO, Duration.ofSeconds(10))
                   .map(tick -> buildEvent(type, lines, ids, accept));
    }

    @GetMapping(value = "/vehicles",
                produces = { "application/octet-stream", MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<?> getVehicles(
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> lines,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        List<VehicleLocation> chosen = filterVehicles(type, lines, ids);
        if ((lines != null && !lines.isEmpty()) && chosen.isEmpty()) {
            log.warn("Lines specified ({}) but no vehicles matched", lines);
            if (accept.contains("json")) {
                return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"No matching vehicles for specified lines.\"}");
            } else {
                return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No matching vehicles for specified lines.");
            }
        }

        return buildResponse(chosen, accept);
    }

    private List<VehicleLocation> filterVehicles(List<String> type, List<String> lines, List<String> ids) {
        Stream<VehicleLocation> stream = Stream.empty();

        boolean includeTrams = type == null || type.contains("trams");
        boolean includeBuses = type == null || type.contains("buses");

        log.debug("Filtering vehicles - types: {}, lines: {}, ids: {}", type, lines, ids);

        if (lines != null && !lines.isEmpty()) {
            List<String> matched = matchLines(lines, includeBuses, includeTrams);
            if (matched.isEmpty()) {
                log.info("No cache keys matched input lines: {}", lines);
                return List.of();
            }

            for (String line : matched) {
                int tramCount = includeTrams ? cache.getTramsByLine(line).size() : 0;
                int busCount  = includeBuses ? cache.getBusesByLine(line).size() : 0;
                log.info("Line '{}': {} trams, {} buses", line, tramCount, busCount);
            }

            if (includeTrams) {
                stream = Stream.concat(stream,
                    matched.stream().flatMap(line -> cache.getTramsByLine(line).stream()));
            }
            if (includeBuses) {
                stream = Stream.concat(stream,
                    matched.stream().flatMap(line -> cache.getBusesByLine(line).stream()));
            }
        } else {
            if (includeTrams) {
                stream = Stream.concat(stream,
                    cache.getTramsByLine().values().stream().flatMap(List::stream));
            }
            if (includeBuses) {
                stream = Stream.concat(stream,
                    cache.getBusesByLine().values().stream().flatMap(List::stream));
            }
        }

        if (ids != null && !ids.isEmpty()) {
            stream = stream.filter(v -> ids.contains(v.getVehicleNumber()));
        }

        List<VehicleLocation> result = stream.toList();
        log.info("Returning {} vehicles after filtering", result.size());

        return result;
    }

    private List<String> matchLines(List<String> lines, boolean includeBuses, boolean includeTrams) {
        if (lines == null || lines.isEmpty()) return List.of();

        Set<String> normalisedInput = lines.stream()
                                           .filter(Objects::nonNull)
                                           .map(String::trim)
                                           .map(String::toLowerCase)
                                           .collect(Collectors.toSet());

        Stream<String> allKeys = Stream.empty();

        if (includeTrams) {
            allKeys = Stream.concat(allKeys, cache.getTramsByLine().keySet().stream());
        }
        if (includeBuses) {
            allKeys = Stream.concat(allKeys, cache.getBusesByLine().keySet().stream());
        }

        List<String> result = allKeys
            .filter(Objects::nonNull)
            .filter(k -> {
                String keyLower = k.trim().toLowerCase();
                return normalisedInput.stream().anyMatch(input ->
                    keyLower.equals(input) || keyLower.endsWith(input));
            })
            .distinct()
            .toList();

        log.info("Input lines: {}", lines);
        log.info("Matched cache keys: {}", result);

        return result;
    }

    private ServerSentEvent<String> buildEvent(List<String> type, List<String> lines, List<String> ids, String accept) {
        List<VehicleLocation> chosen = filterVehicles(type, lines, ids);
        if ((lines != null && !lines.isEmpty()) && chosen.isEmpty()) {
            return ServerSentEvent.<String>builder()
                .event("warning")
                .data("{\"error\":\"No vehicles found for those lines.\"}")
                .build();
        }

        VehicleLocationList list = VehicleLocationList.newBuilder().addAllVehicles(chosen).build();

        if (accept.contains("json")) {
            try {
                String json = JsonFormat.printer().print(list);
                return ServerSentEvent.<String>builder().event("vehicles").data(json).build();
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("failed to serialize protobuf→JSON", e);
            }
        } else {
            String b64 = Base64.getEncoder().encodeToString(list.toByteArray());
            return ServerSentEvent.<String>builder().event("vehicles").data(b64).build();
        }
    }

    private ResponseEntity<?> buildResponse(List<VehicleLocation> chosen, String accept) {
        VehicleLocationList list = VehicleLocationList.newBuilder().addAllVehicles(chosen).build();

        if (accept.contains("json")) {
            try {
                String json = JsonFormat.printer().print(list);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("failed to serialize protobuf→JSON", e);
            }
        } else {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(list.toByteArray());
        }
    }
}
