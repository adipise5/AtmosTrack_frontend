package com.example.consumer.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.example.consumer.Model.Reading;
import com.example.consumer.Utils.AqiCalculator;
import com.example.consumer.Utils.DbManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConsumerProcessor {

    private final DbManager dbManager;
    private final AqiCalculator aqiCalculator;

    
    public void process(List<Reading> readings){

        if (readings == null || readings.isEmpty()) return;

        Map<String,List<Reading>> cityMapBatch=new HashMap<>();
        Long start=System.currentTimeMillis();
        for(Reading reading : readings){
            aqiCalculator.compute(reading);
            cityMapBatch.computeIfAbsent(reading.getCity(),k->new ArrayList<>()).add(reading);
        }

        dbManager.saveBatch(cityMapBatch);

        log.info("Processed {} readings in {}ms", readings.size(), System.currentTimeMillis() - start);

    }
        
}

