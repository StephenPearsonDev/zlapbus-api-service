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
public class StreamService {
	
	private final VehicleService vehicleService;

	public StreamService(VehicleService vehicleService) {
		
		this.vehicleService = vehicleService;
		
	}
	
	//could remove lines?
	public ServerSentEvent<String> toSseEvent(List<String> types, List<String> lines, List<String> ids, String accept) {
		
		List<VehicleLocation> chosenVehicles = vehicleService.getVehicles(types, lines, ids);
		
		
		//probably not needed - for this endpoint we are only ever chekcing buses and trams - no lines
		if(lines != null && !lines.isEmpty() && chosenVehicles.isEmpty()) {
			return ServerSentEvent.<String>builder()
					.event("warning")
					.data("{\"error\":\"No vehicles have been found for those lines.\"}")
					.build();
		}
		
		VehicleLocationList vehicleList = VehicleLocationList
				.newBuilder()
				.addAllVehicles(chosenVehicles)
				.build();
		
		if(accept.contains("json")) {
			try {
				String json = JsonFormat.printer().print(vehicleList);
				
				return ServerSentEvent.<String>builder()
						.event("vehicles")
						.data(json)
						.build();
			} catch (InvalidProtocolBufferException e) {
				throw new RuntimeException("faled to serialize the protobuf data to JSON", e);
			}
		} else {
			
			
			String base64 = Base64.getEncoder().encodeToString(vehicleList.toByteArray());
			
			return ServerSentEvent.<String>builder()
					.event("vehicles")
					.data(base64)
					.build();
		 }
		}
	
		public ResponseEntity<?> toResponse(List<String> types,List<String> lines,List<String> ids,String accept) {

	        List<VehicleLocation> chosenVehicles = vehicleService.getVehicles(types, lines, ids);

	        if (lines != null && !lines.isEmpty() && chosenVehicles.isEmpty()) {
	            if (accept.contains("json")) {
	                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"No matching vehicles for specified lines.\"}");
	            } else {
	                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("No matching vehicles for specified lines.");
	            }
	        }

	        VehicleLocationList vehicleList = VehicleLocationList
	            .newBuilder()
	            .addAllVehicles(chosenVehicles)
	            .build();

	        if (accept.contains("json")) {
	            try {
	                String json = JsonFormat.printer().print(vehicleList);
	                return ResponseEntity.ok()
	                                     .contentType(MediaType.APPLICATION_JSON)
	                                     .body(json);
	            } catch (InvalidProtocolBufferException e) {
	                throw new RuntimeException("failed to serialize protobuf→JSON", e);
	            }
	        } else {
	            return ResponseEntity.ok()
	                                 .contentType(MediaType.APPLICATION_OCTET_STREAM)
	                                 .body(vehicleList.toByteArray());
	        }
	    }
	}