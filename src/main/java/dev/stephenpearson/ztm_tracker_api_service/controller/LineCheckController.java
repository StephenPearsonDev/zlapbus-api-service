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
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/lines")
public class LineCheckController {

    private static final Logger log = LoggerFactory.getLogger(LineCheckController.class);
    private final VehicleCache cache;

    public LineCheckController(VehicleCache cache) {
        this.cache = cache;
    }

    @GetMapping(value = "/{line}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLineVehicles(
            @PathVariable String line,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        return Flux.interval(Duration.ZERO, Duration.ofSeconds(10))
                   .map(tick -> buildEvent(line, type, ids, accept));
    }

    @GetMapping(value = "/{line}",
                produces = { "application/octet-stream", MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<?> checkLine(
            @PathVariable String line,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        if (line == null || line.isBlank()) {
            if (accept.contains("json")) {
                return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Line must not be empty\"}");
            } else {
                return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Line must not be empty");
            }
        }

        List<VehicleLocation> chosen = filterVehiclesByLine(line, type, ids);

        if (chosen.isEmpty()) {
            log.warn("Line specified ({}) but no vehicles matched", line);
            if (accept.contains("json")) {
                return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"No vehicles found for line " + line + "\"}");
            } else {
                return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No vehicles found for line " + line);
            }
        }

        return buildResponse(chosen, accept);
    }

    private List<VehicleLocation> filterVehiclesByLine(String line, List<String> type, List<String> ids) {
        boolean includeTrams = type == null || type.contains("trams");
        boolean includeBuses = type == null || type.contains("buses");

        log.debug("Filtering vehicles for line '{}' - types: {}, ids: {}", line, type, ids);

        List<String> matched = matchExactLine(line, includeBuses, includeTrams);
        if (matched.isEmpty()) {
            log.info("No exact cache keys matched input line: {}", line);
            return List.of();
        }

        Stream<VehicleLocation> stream = Stream.empty();

        for (String matchedLine : matched) {
            int tramCount = includeTrams ? cache.getTramsByLine(matchedLine).size() : 0;
            int busCount  = includeBuses ? cache.getBusesByLine(matchedLine).size() : 0;
            log.info("Line '{}': {} trams, {} buses", matchedLine, tramCount, busCount);
        }

        if (includeTrams) {
            stream = Stream.concat(stream,
                matched.stream().flatMap(matchedLine -> cache.getTramsByLine(matchedLine).stream()));
        }
        if (includeBuses) {
            stream = Stream.concat(stream,
                matched.stream().flatMap(matchedLine -> cache.getBusesByLine(matchedLine).stream()));
        }

        if (ids != null && !ids.isEmpty()) {
            stream = stream.filter(v -> ids.contains(v.getVehicleNumber()));
        }

        List<VehicleLocation> result = stream.toList();
        log.debug("Returning {} vehicles for line '{}' after filtering", result.size(), line);

        return result;
    }


    private String normalizeLineNumber(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        
        String trimmed = line.trim();
        

        if (trimmed.length() > 1 && Character.isLetter(trimmed.charAt(0))) {
            String prefix = String.valueOf(trimmed.charAt(0));
            String numberPart = trimmed.substring(1);
            String normalizedNumber = numberPart.replaceAll("^0+", "");

            if (normalizedNumber.isEmpty()) {
                normalizedNumber = "0";
            }
            return prefix + normalizedNumber;
        } else {

            String normalized = trimmed.replaceAll("^0+", "");

            if (normalized.isEmpty()) {
                normalized = "0";
            }
            return normalized;
        }
    }


    private List<String> matchExactLine(String line, boolean includeBuses, boolean includeTrams) {
        if (line == null || line.isBlank()) return List.of();

        String originalInput = line.trim().toLowerCase();
        String normalizedInput = normalizeLineNumber(originalInput);

        log.debug("Input line: '{}', normalized to: '{}'", line, normalizedInput);

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
                
                if (keyLower.equals(originalInput)) {
                    return true;
                }
                
                if (keyLower.equals(normalizedInput)) {
                    return true;
                }
                
                String cleanKey = keyLower.replaceAll("^(line_|route_|bus_|tram_)", "");
                if (cleanKey.equals(originalInput) || cleanKey.equals(normalizedInput)) {
                    return true;
                }
                
                String normalizedCleanKey = normalizeLineNumber(cleanKey);
                if (normalizedCleanKey.equals(normalizedInput)) {
                    return true;
                }
                
                if (normalizedInput.length() == 1) {
                    return false;
                }
                
                return keyLower.endsWith(originalInput) || keyLower.endsWith(normalizedInput) ||
                       cleanKey.endsWith(originalInput) || cleanKey.endsWith(normalizedInput);
            })
            .distinct()
            .toList();

        log.info("Input line: '{}' (normalized: '{}'), matched cache keys: {}", line, normalizedInput, result);
        
        if (result.isEmpty()) {
            Set<String> allCacheKeys = Stream.concat(
                includeTrams ? cache.getTramsByLine().keySet().stream() : Stream.empty(),
                includeBuses ? cache.getBusesByLine().keySet().stream() : Stream.empty()
            ).collect(Collectors.toSet());
            
            List<String> partialMatches = allCacheKeys.stream()
                .filter(k -> {
                    String keyLower = k.toLowerCase();
                    return keyLower.contains(normalizedInput) || keyLower.contains(originalInput);
                })
                .toList();
                
            if (!partialMatches.isEmpty()) {
                log.info("No exact matches for '{}' but found partial matches: {}", line, partialMatches);
            } else {
                log.info("No matches at all found for '{}'", line);
            }
        }

        return result;
    }

    private ServerSentEvent<String> buildEvent(String line, List<String> type, List<String> ids, String accept) {
        List<VehicleLocation> chosen = filterVehiclesByLine(line, type, ids);

        if (chosen.isEmpty()) {
            return ServerSentEvent.<String>builder()
                .event("warning")
                .data("{\"error\":\"No vehicles found for line " + line + ".\"}")
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