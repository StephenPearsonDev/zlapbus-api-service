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
import dev.stephenpearson.ztm_tracker_api_service.service.LineStreamService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/lines")
public class LineStreamController {

    private static final Logger log = LoggerFactory.getLogger(LineStreamController.class);
    private final LineStreamService lineStreamService;

    public LineStreamController(LineStreamService lineStreamService) {
        this.lineStreamService = lineStreamService;
    }

    @GetMapping(value = "/{line}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLineVehicles(
            @PathVariable String line,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        return Flux.interval(Duration.ZERO, Duration.ofSeconds(10))
                   .map(tick -> lineStreamService.buildEvent(line, type, ids, accept));
    }

    @GetMapping(value = "/{line}", produces = { "application/octet-stream", MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<?> checkLine(
            @PathVariable String line,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

    	if (line == null || line.isBlank()) {
    	    return lineStreamService.buildError("Line must not be empty", accept);
    	}

    	return lineStreamService.buildResponse(line, type, ids, accept);

    }

}