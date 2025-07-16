package dev.stephenpearson.ztm_tracker_api_service.controller;

import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocation;
import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocationList;
import dev.stephenpearson.ztm_tracker_api_service.service.VehicleService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
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
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VehicleStreamController {

    private static final Logger log = LoggerFactory.getLogger(VehicleStreamController.class);

    private final VehicleService vehicleService;

    public VehicleStreamController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
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

        List<VehicleLocation> chosen = vehicleService.getVehicles(type, lines, ids);

        if (lines != null && !lines.isEmpty() && chosen.isEmpty()) {
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

    private ServerSentEvent<String> buildEvent(
            List<String> type,
            List<String> lines,
            List<String> ids,
            String accept) {

        List<VehicleLocation> chosen = vehicleService.getVehicles(type, lines, ids);

        if (lines != null && !lines.isEmpty() && chosen.isEmpty()) {
            return ServerSentEvent.<String>builder()
                .event("warning")
                .data("{\"error\":\"No vehicles found for those lines.\"}")
                .build();
        }

        VehicleLocationList list = VehicleLocationList
            .newBuilder()
            .addAllVehicles(chosen)
            .build();

        if (accept.contains("json")) {
            try {
                String json = JsonFormat.printer().print(list);
                return ServerSentEvent.<String>builder()
                                     .event("vehicles")
                                     .data(json)
                                     .build();
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("failed to serialize protobuf→JSON", e);
            }
        } else {
            String b64 = Base64.getEncoder().encodeToString(list.toByteArray());
            return ServerSentEvent.<String>builder()
                                 .event("vehicles")
                                 .data(b64)
                                 .build();
        }
    }

    private ResponseEntity<?> buildResponse(
            List<VehicleLocation> chosen,
            String accept) {

        VehicleLocationList list = VehicleLocationList
            .newBuilder()
            .addAllVehicles(chosen)
            .build();

        if (accept.contains("json")) {
            try {
                String json = JsonFormat.printer().print(list);
                return ResponseEntity.ok()
                                     .contentType(MediaType.APPLICATION_JSON)
                                     .body(json);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("failed to serialize protobuf→JSON", e);
            }
        } else {
            return ResponseEntity.ok()
                                 .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                 .body(list.toByteArray());
        }
    }
}
