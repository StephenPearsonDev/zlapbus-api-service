package dev.stephenpearson.ztm_tracker_api_service.scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.stephenpearson.ztm_api.protobuf.VehicleLocationOuterClass.VehicleLocation;
import dev.stephenpearson.ztm_tracker_api_service.cache.VehicleCache;

@Component
public class VehiclePoller {
    private static final Logger log = LoggerFactory.getLogger(VehiclePoller.class);

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final VehicleCache cache;
    private final TaskExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VehiclePoller(RedisTemplate<String, byte[]> redisTemplate,
                         VehicleCache cache,
                         TaskExecutor pollerExecutor) {
        this.redisTemplate = redisTemplate;
        this.cache = cache;
        this.executor = pollerExecutor;
    }

    @Scheduled(fixedDelay = 5_000)
    public void schedulePoll() {
        executor.execute(this::doPoll);
    }

    private void doPoll() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Previous redis polling still running - skipping it this time");
            return;
        }

        try {
            Set<String> keys = scanKeys("ztm:buses:*", "ztm:trams:*");
            log.info("Redis result: found {} keys", keys.size());

            List<VehicleLocation> all = redisTemplate.opsForValue()
                .multiGet(keys)
                .stream()
                .filter(Objects::nonNull)
                .map(data -> {
                    try {
                        return VehicleLocation.parseFrom(data);
                    } catch (Exception e) {
                        log.error("Proto parse failed", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            log.info("Caching {} vehicle locations", all.size());
            cache.update(all);

        } catch (Exception e) {
            log.error("Unexpected error in poller", e);
        } finally {
            running.set(false);
        }
    }

 
    private final Set<String> scanKeys(String... patterns) {
        Set<String> keys = new HashSet<>();
        for (String pattern : patterns) {
            try {
                ScanOptions opts = ScanOptions.scanOptions()
                                             .match(pattern)
                                             .count(500)
                                             .build();
                try (Cursor<String> cursor = redisTemplate.scan(opts)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan Redis keys for pattern: {}", pattern, e);
            }
        }
        return keys;
    }
}