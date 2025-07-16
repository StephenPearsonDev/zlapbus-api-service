package dev.stephenpearson.ztm_tracker_api_service.service;

import java.util.Base64;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocation;
import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocationList;

@Service
public class LineStreamService {

    private final VehicleService vehicleService;

    public LineStreamService(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    public ServerSentEvent<String> buildEvent(String line, List<String> type, List<String> ids, String accept) {
    	
    	List<VehicleLocation> chosen = vehicleService.getVehicles(type, List.of(line), ids);

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
                throw new RuntimeException("failed to serialize protobuf to JSON", e);
            }
        } else {
            String b64 = Base64.getEncoder().encodeToString(list.toByteArray());
            return ServerSentEvent.<String>builder().event("vehicles").data(b64).build();
        }
    }
    

    public ResponseEntity<?> buildResponse(String line, List<String> type, List<String> ids, String accept) {
        List<VehicleLocation> chosen = vehicleService.getVehicles(type, List.of(line), ids);

        if (chosen.isEmpty()) {
            return buildError("No vehicles found for line " + line, accept);
        }

        VehicleLocationList list = VehicleLocationList.newBuilder().addAllVehicles(chosen).build();

        if (accept.contains("json")) {
            try {
                String json = JsonFormat.printer().print(list);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("failed to serialize protobuf to JSON", e);
            }
        } else {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(list.toByteArray());
        }
    }

    
    public ResponseEntity<?> buildError(String message, String accept) {
        if (accept.contains("json")) {
            return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"" + message + "\"}");
        } else {
            return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message);
        }
    }
}
