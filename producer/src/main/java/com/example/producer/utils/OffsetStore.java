package com.example.producer.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.producer.repository.FileOffsetRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OffsetStore {

    private final FileOffsetRepository fileOffsetRepository;
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();

    public long get(String key) {
        return offsets.getOrDefault(key, 0L);
    }

    public void put(String key, long value) {
        offsets.put(key, value);
    }

    public void putAll(Map<String, Long> map) {
        offsets.putAll(map);
    }

    public void persist() {
        offsets.forEach((hotspot,pointer)->fileOffsetRepository.upsert(hotspot, pointer));
    }

}
