package com.example.consumer.Utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.consumer.Model.Reading;
import com.example.consumer.repository.CityRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class DbManager {

    private final Map<String, JdbcTemplate> cityJdbcTemplates;
    private final CityRepository cityRepository;


    public void saveBatch(Map<String,List<Reading>> cityMapBatch) {

        cityMapBatch.forEach((city,readings)->{
            JdbcTemplate connection=cityJdbcTemplates.get(city);
            cityRepository.save(connection,readings);
        });
        
    }

}
