package com.example.producer.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.producer.Model.Reading;

public interface CityProducerPort {

    public List<Reading> fetch();

}
