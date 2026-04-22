package com.example.consumer.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.example.consumer.Model.Reading;
import com.example.consumer.service.ConsumerProcessor;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockingBuffer {

    private final ExecutorService executorService;
    private final ConsumerProcessor processor;

    private final BlockingQueue<Reading> queue = new LinkedBlockingQueue<>(10000);

    private static final int BATCH_SIZE = 1000;
    private static final int TIME_OUT_MS = 5000;

    @PostConstruct
    private void init() {
        int workerThreads=4;
        for (int i=0;i<workerThreads;i++) {
            executorService.submit(()->workerLoop());
        }
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {   
                Reading firstReading=queue.poll(1000, TimeUnit.MILLISECONDS);
                if(firstReading==null){
                    continue;
                }
                List<Reading> batch=new ArrayList<>();
                batch.add(firstReading);
                Long deadline=System.currentTimeMillis()+TIME_OUT_MS;
                while(batch.size()<BATCH_SIZE && System.currentTimeMillis()<deadline){
                    queue.drainTo(batch, BATCH_SIZE - batch.size());
                    Long timeRemaining=deadline-System.currentTimeMillis();
                    if(timeRemaining<=0 || batch.size()>=BATCH_SIZE){
                        break;                        
                    }
                    Reading nextReading=queue.poll(timeRemaining,TimeUnit.MILLISECONDS);
                    if(nextReading!=null){
                        batch.add(nextReading);
                    }

                }

                processor.process(batch);

            }
            catch (Exception e) {
                log.error("Error processing batch, dropping data", e);
            }
        }
    }

    public void add(Reading reading){
        try {
            queue.put(reading);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();            
            log.warn("Thread was interrupted while trying to add a reading to the buffer.");
        }
    }

}