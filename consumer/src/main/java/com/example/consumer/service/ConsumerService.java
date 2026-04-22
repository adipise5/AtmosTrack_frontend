package com.example.consumer.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.consumer.Model.Reading;
import com.example.consumer.Utils.BlockingBuffer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumerService {

     private final BlockingBuffer blockingBuffer;

    @KafkaListener(topics = {"Delhi","Kolkata","Ahmedabad","Hyderabad","Pune","Mumbai","Bengaluru","Chennai"}, concurrency="3")
    public void listen(Reading message ){
          blockingBuffer.add(message);
    }

}
