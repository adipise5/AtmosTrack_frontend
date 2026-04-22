package com.example.flinkjob.Utils;

import org.apache.flink.configuration.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import com.example.flinkjob.Model.AqiResult;

import redis.clients.jedis.Jedis;

public class RedisSink extends RichSinkFunction<AqiResult>{

    private transient Jedis jedis;
    private transient ObjectMapper mapper;
    private String type;

    public RedisSink(String type){
        this.type=type;
    }

    @Override
    public void open(Configuration parameters) {
        jedis = new Jedis("localhost", 6379);
        mapper = new ObjectMapper();
    }

    @Override
    public void invoke(AqiResult value, Context context) throws com.fasterxml.jackson.core.JsonProcessingException {

        if("Hotspot".equals(type)){
            String key= value.getCity()+":"+value.getHotspot();
            String aqi=String.valueOf(value.getAqi()==null?0:value.getAqi());

            jedis.hset("aqi:hotspots",key,aqi);
            jedis.zadd("aqi:history:hotspot:" + key, value.getWindowEndTS(), aqi);
            
        }

        else{

            String city = value.getCity();
            Integer aqi = value.getAqi();

            if(aqi==null){
                aqi=0;
            }
            
            String json=mapper.writeValueAsString(value);

            jedis.hset("aqi:latest", city, json);

            jedis.zadd("aqi:history:" + city, value.getWindowEndTS(), String.valueOf(aqi));

            jedis.zadd("aqi:ranking", aqi , city);

        }
    }

    @Override
    public void close() {
        if (jedis != null) {
            jedis.close();
        }
    }
}

