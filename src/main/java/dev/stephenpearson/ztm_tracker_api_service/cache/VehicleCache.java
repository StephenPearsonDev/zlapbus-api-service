package dev.stephenpearson.ztm_tracker_api_service.cache;

import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocation;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class VehicleCache {

    private final AtomicReference<Map<String, List<VehicleLocation>>> busesByLine = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, List<VehicleLocation>>> tramsByLine = new AtomicReference<>(Map.of());
    private final AtomicReference<List<VehicleLocation>> allVehicles =  new AtomicReference<>(List.of());

    public void update(List<VehicleLocation> all) {
        Map<String, List<VehicleLocation>> buses  = new HashMap<>();
        Map<String, List<VehicleLocation>> trams  = new HashMap<>();

        for (VehicleLocation v : all) {
            String line = v.getLine();
            String type = v.getType();   

            if (line == null || line.isBlank() ||
                type == null || type.isBlank()) continue;
            
            //TODO use enum like other service for type
            switch (type) {
                case "trams" ->
                    trams.computeIfAbsent(line, l -> new ArrayList<>()).add(v);
                case "buses" ->
                    buses.computeIfAbsent(line, l -> new ArrayList<>()).add(v);
            }
        }

        busesByLine.set(Map.copyOf(buses));
        tramsByLine.set(Map.copyOf(trams));
        allVehicles.set(List.copyOf(all));
    }


    public List<VehicleLocation> getAllVehicles() {
        return allVehicles.get();
    }

    public Map<String, List<VehicleLocation>> getBusesByLine() {
        return busesByLine.get();
    }

    public Map<String, List<VehicleLocation>> getTramsByLine() {
        return tramsByLine.get();
    }

    public List<VehicleLocation> getBusesByLine(String line) {
        return busesByLine.get().getOrDefault(line, List.of());
    }

    public List<VehicleLocation> getTramsByLine(String line) {
        return tramsByLine.get().getOrDefault(line, List.of());
    }

    public Set<String> getAllBusLines() {
        return busesByLine.get().keySet();
    }

    public Set<String> getAllTramLines() {
        return tramsByLine.get().keySet();
    }

   
}
