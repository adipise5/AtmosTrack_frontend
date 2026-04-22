package com.example.producer.repository;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FileOffsetRepository {
    
    private final JdbcTemplate jdbcTemplate;

    public Map<String, Long> loadAll() {
        return jdbcTemplate.query("""
            SELECT hotspot, file_pointer FROM file_offsets
        """, rs -> {
            Map<String, Long> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("hotspot"), rs.getLong("file_pointer"));
            }
            return map;
        });
    }

    public void upsert(String hotspot, long pointer) {
        jdbcTemplate.update("""
            INSERT INTO file_offsets (hotspot, file_pointer)
            VALUES (?, ?)
            ON CONFLICT (hotspot)
            DO UPDATE SET file_pointer = EXCLUDED.file_pointer
        """, hotspot, pointer);
    }
}