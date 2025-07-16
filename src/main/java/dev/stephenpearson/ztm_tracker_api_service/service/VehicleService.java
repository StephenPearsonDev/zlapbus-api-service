package dev.stephenpearson.ztm_tracker_api_service.service;

import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocation;
import dev.stephenpearson.ztm_tracker_api_service.cache.VehicleCache;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class VehicleService {

    private final VehicleCache cache;

    public VehicleService(VehicleCache cache) {
        this.cache = cache;
    }

    public List<VehicleLocation> getVehicles(
            List<String> types,
            List<String> lines,
            List<String> ids) {

        Stream<VehicleLocation> stream = Stream.empty();
        boolean includeTrams = types == null || types.contains("trams");
        boolean includeBuses = types == null || types.contains("buses");

        if (lines != null && !lines.isEmpty()) {
            List<String> matched = matchLines(lines, includeBuses, includeTrams);
            if (matched.isEmpty()) {
                return List.of();
            }
            if (includeTrams) {
                stream = matched.stream()
                                .flatMap(l -> cache.getTramsByLine(l).stream());
            }
            if (includeBuses) {
                stream = Stream.concat(stream,
                        matched.stream()
                               .flatMap(l -> cache.getBusesByLine(l).stream()));
            }
        } else {
            if (includeTrams) {
                stream = cache.getTramsByLine().values()
                              .stream()
                              .flatMap(List::stream);
            }
            if (includeBuses) {
                stream = Stream.concat(stream,
                        cache.getBusesByLine().values()
                             .stream()
                             .flatMap(List::stream));
            }
        }

        if (ids != null && !ids.isEmpty()) {
            stream = stream.filter(v -> ids.contains(v.getVehicleNumber()));
        }

        return stream.toList();
    }

    private List<String> matchLines(
            List<String> lines,
            boolean includeBuses,
            boolean includeTrams) {

        Set<String> normalized = lines.stream()
                                      .filter(Objects::nonNull)
                                      .map(String::trim)
                                      .map(String::toLowerCase)
                                      .collect(Collectors.toSet());

        Stream<String> keys = Stream.empty();
        if (includeTrams) {
            keys = cache.getTramsByLine().keySet().stream();
        }
        if (includeBuses) {
            keys = Stream.concat(
                keys,
                cache.getBusesByLine().keySet().stream()
            );
        }

        return keys
            .filter(Objects::nonNull)
            .filter(k -> {
                String kl = k.trim().toLowerCase();
                return normalized.stream()
                                 .anyMatch(inp -> kl.equals(inp) || kl.endsWith(inp));
            })
            .distinct()
            .toList();
    }
}
