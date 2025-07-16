package dev.stephenpearson.ztm_tracker_api_service.controller;

import dev.stephenpearson.ztm_tracker_api_service.service.StreamService;
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
import java.util.List;

@RestController
@RequestMapping("/api")
public class VehicleStreamController {

    private final StreamService streamService;

    public VehicleStreamController(StreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/sse/vehicles", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamVehicles(
    		@RequestParam(required = false) List<String> type,
    		@RequestParam(required = false) List<String> lines,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        return Flux.interval(Duration.ZERO, Duration.ofSeconds(10))
                   .map(tick -> streamService.toSseEvent(type, lines, ids, accept));
    }
    
    
    //For the polling fallback - TODO: check if front end actually polls the protobuf as well as json - just JSON prob sufficient
    @GetMapping(value = "/vehicles", produces = { "application/octet-stream", MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<?> getVehicles(
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> lines,
            @RequestParam(required = false) List<String> ids,
            @RequestHeader(value = "Accept", defaultValue = "application/octet-stream") String accept) {

        return streamService.toResponse(type, lines, ids, accept);
    }
}
