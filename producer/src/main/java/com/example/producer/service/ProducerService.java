package com.example.producer.service;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.producer.Model.Reading;
import com.example.producer.repository.FileOffsetRepository;
import com.example.producer.utils.OffsetStore;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProducerService {

    private final ExecutorService executorService;
    private final List<CityProducerPort> producers;
    private final KafkaTemplate<String,Reading> kafkaTemplate;
    private List<Callable<List<Reading>>> tasks;
    private final FileOffsetRepository fileOffSetRepository;
    private final OffsetStore offsetStore;

    @PostConstruct
    private void init(){
        this.tasks=producers.stream()
        .map((city)->(Callable<List<Reading>>)()->city.fetch())
        .toList();

        offsetStore.putAll(fileOffSetRepository.loadAll());
    }

    @Scheduled(fixedDelay = 1000)
    public void produce() throws InterruptedException{

        long start=System.currentTimeMillis();

        List<Future<List<Reading>>> futures=executorService.invokeAll(tasks);
        List<Reading> readings = futures.stream()
            .map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    log.error("Exception from future readings", e);
                    return List.<Reading>of();
                }
            })
            .flatMap(List::stream)
            .toList();

        readings.forEach((reading)->{
            kafkaTemplate.send(reading.getCity(),reading.getHotspot(),reading);
        });
            
        CompletableFuture.runAsync(()->{
            offsetStore.persist();
        },executorService);
        log.info("Batch completed in {} ms with batch size {}",System.currentTimeMillis()-start,readings.size());
        
        
    }
        
}