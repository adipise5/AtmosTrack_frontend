package com.example.flinkjob.Utils;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.resps.Tuple;

public class RedisWebSocketBridge implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketBridge.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final JedisPooled jedis;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<String, Long> lastWindowEndByCity = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> lastHotspotAqiByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastHotspotHistoryTsByKey = new ConcurrentHashMap<>();
    private final AqiSocketServer server;

    public RedisWebSocketBridge(String redisHost, int redisPort, int wsPort, long pollMs) {
        this.jedis = new JedisPooled(redisHost, redisPort);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.server = new AqiSocketServer(new InetSocketAddress(wsPort));

        server.start();
        scheduler.scheduleWithFixedDelay(this::publishFromRedisSafely, 0L, pollMs, TimeUnit.MILLISECONDS);

        log.info("RedisWebSocketBridge started (redis={}:{}, ws=0.0.0.0:{}, path=/ws/aqi)", redisHost, redisPort, wsPort);
    }

    private void publishFromRedisSafely() {
        try {
            publishFromRedis();
        } catch (Exception ex) {
            log.warn("Redis websocket publish cycle failed", ex);
        }
    }

    private void publishFromRedis() throws Exception {
        if (server.getConnections().isEmpty()) {
            return;
        }

        Map<String, Object> latestByCity = readLatestByCity();
        List<Map<String, Object>> ranking = readRanking();
        Map<String, Object> hotspotsByKey = readHotspots();

        server.broadcastFrame(frame("latestSnapshot", latestByCity));
        server.broadcastFrame(frame("rankingSnapshot", ranking));
        server.broadcastFrame(frame("hotspotSnapshot", hotspotsByKey));

        for (Map.Entry<String, Object> entry : latestByCity.entrySet()) {
            String city = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> cityPayload)) {
                continue;
            }

            Long currentWindowEnd = toLong(cityPayload.get("windowEndTS"));
            if (currentWindowEnd == null) {
                continue;
            }

            Long previous = lastWindowEndByCity.put(city, currentWindowEnd);
            if (previous == null || currentWindowEnd > previous) {
                server.broadcastFrame(frame("cityUpdate", cityPayload));
                server.broadcastFrame(frame("historySnapshot", historyFrame(city)));
            }
        }

        for (Map.Entry<String, Object> entry : hotspotsByKey.entrySet()) {
            String hotspotKey = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> hotspotPayload)) {
                continue;
            }

            Integer currentAqi = toIntObj(hotspotPayload.get("aqi"));
            if (currentAqi == null) {
                continue;
            }

            Long latestHistoryTs = latestHotspotHistoryTimestamp(hotspotKey);

            Integer previous = lastHotspotAqiByKey.put(hotspotKey, currentAqi);
            Long previousHistoryTs = lastHotspotHistoryTsByKey.put(hotspotKey, latestHistoryTs);

            boolean aqiChanged = previous == null || !previous.equals(currentAqi);
            boolean historyAdvanced = latestHistoryTs != null
                    && (previousHistoryTs == null || latestHistoryTs > previousHistoryTs);

            if (aqiChanged || historyAdvanced) {
                server.broadcastFrame(frame("hotspotUpdate", hotspotPayload));
                server.broadcastFrame(frame("historySnapshot", hotspotHistoryFrame(hotspotKey)));
            }
        }
    }

    private Map<String, Object> readLatestByCity() {
        Map<String, String> raw = jedis.hgetAll("aqi:latest");
        Map<String, Object> latestByCity = new HashMap<>();

        for (Map.Entry<String, String> entry : raw.entrySet()) {
            try {
                Map<String, Object> payload = mapper.readValue(entry.getValue(), new TypeReference<Map<String, Object>>() {
                });
                payload.putIfAbsent("city", entry.getKey());
                latestByCity.put(entry.getKey(), payload);
            } catch (Exception ex) {
                log.debug("Skipping malformed city payload for {}", entry.getKey(), ex);
            }
        }

        return latestByCity;
    }

    private List<Map<String, Object>> readRanking() {
        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Tuple tuple : jedis.zrevrangeWithScores("aqi:ranking", 0, 15)) {
            Map<String, Object> row = new HashMap<>();
            row.put("city", tuple.getElement());
            row.put("aqi", (int) Math.round(tuple.getScore()));
            ranking.add(row);
        }
        return ranking;
    }

    private Map<String, Object> readHotspots() {
        Map<String, String> raw = jedis.hgetAll("aqi:hotspots");
        Map<String, Object> hotspots = new HashMap<>();

        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey(); // city:hotspot
            String[] parts = key.split(":", 2);
            String city = parts.length > 0 ? parts[0] : "";
            String hotspot = parts.length > 1 ? parts[1] : key;
            Integer aqi = toInt(entry.getValue());

            Map<String, Object> payload = new HashMap<>();
            payload.put("city", city);
            payload.put("hotspot", hotspot);
            payload.put("key", key);
            payload.put("aqi", aqi);

            hotspots.put(key, payload);
        }

        return hotspots;
    }

    private Map<String, Object> historyFrame(String city) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (Tuple tuple : jedis.zrangeWithScores("aqi:history:" + city, -24, -1)) {
            Map<String, Object> point = new HashMap<>();
            point.put("ts", Math.round(tuple.getScore()));
            point.put("aqi", toInt(tuple.getElement()));
            points.add(point);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("city", city);
        payload.put("points", points);
        return payload;
    }

    private Map<String, Object> hotspotHistoryFrame(String hotspotKey) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (Tuple tuple : jedis.zrangeWithScores("aqi:history:hotspot:" + hotspotKey, -24, -1)) {
            Map<String, Object> point = new HashMap<>();
            point.put("ts", Math.round(tuple.getScore()));
            point.put("aqi", toInt(tuple.getElement()));
            points.add(point);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("city", hotspotKey);
        payload.put("points", points);
        return payload;
    }

    private Long latestHotspotHistoryTimestamp(String hotspotKey) {
        List<Tuple> latest = jedis.zrevrangeWithScores("aqi:history:hotspot:" + hotspotKey, 0, 0);
        if (latest.isEmpty()) {
            return null;
        }

        return Math.round(latest.get(0).getScore());
    }

    private Map<String, Object> frame(String type, Object payload) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", type);
        frame.put("payload", payload);
        return frame;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Integer toIntObj(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            server.stop(1000);
        } catch (Exception ex) {
            log.warn("Error while stopping websocket server", ex);
        }
        jedis.close();
    }

    private class AqiSocketServer extends WebSocketServer {

        AqiSocketServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String path = handshake.getResourceDescriptor();
            if (!"/ws/aqi".equals(path)) {
                conn.close(1008, "Invalid websocket path");
                return;
            }
            log.info("WS connected: {} path={}", conn.getRemoteSocketAddress(), path);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            log.info("WS closed: {} code={} reason={}", conn == null ? "unknown" : conn.getRemoteSocketAddress(), code,
                    reason);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            // currently server is push-only
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            log.warn("WS error for {}", conn == null ? "server" : conn.getRemoteSocketAddress(), ex);
        }

        @Override
        public void onStart() {
            log.info("AQI websocket server started on port {}", getPort());
        }

        void broadcastFrame(Object frame) {
            try {
                String json = mapper.writeValueAsString(frame);
                for (WebSocket conn : getConnections()) {
                    if (conn != null && conn.isOpen()) {
                        conn.send(json);
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not broadcast websocket frame", ex);
            }
        }
    }
}
