package com.example.flinkjob.Utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.example.flinkjob.Model.AqiAccumulator;
import com.example.flinkjob.Model.AqiResult;
import com.example.flinkjob.Model.Reading;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AqiCalculator extends ProcessWindowFunction<AqiAccumulator,AqiResult,String,TimeWindow>{

    private String type;

    public AqiCalculator(String type){
        this.type=type;
    }

    public Integer computeAqi(AqiResult result) {

        int maxAqi = 0;

        maxAqi = Math.max(maxAqi, subIndexPM25(result.getPm25()));
        maxAqi = Math.max(maxAqi, subIndexPM10(result.getPm10()));
        maxAqi = Math.max(maxAqi, subIndexNO2(result.getNo2()));
        maxAqi = Math.max(maxAqi, subIndexSO2(result.getSo2()));
        maxAqi = Math.max(maxAqi, subIndexCO(result.getCo()));
        maxAqi = Math.max(maxAqi, subIndexO3(result.getOzone()));

        return (maxAqi == 0 ? null : maxAqi);
    }

    // 🔹 Generic formula
    private int calculate(double c, double bpLo, double bpHi, int iLo, int iHi) {
        return (int) Math.round(((iHi - iLo) / (bpHi - bpLo)) * (c - bpLo) + iLo);
    }

    // 🔹 PM2.5
    private int subIndexPM25(Double c) {
        if (c == null) return 0;

        if (c <= 30) return calculate(c, 0, 30, 0, 50);
        if (c <= 60) return calculate(c, 31, 60, 51, 100);
        if (c <= 90) return calculate(c, 61, 90, 101, 200);
        if (c <= 120) return calculate(c, 91, 120, 201, 300);
        if (c <= 250) return calculate(c, 121, 250, 301, 400);
        return calculate(c, 251, 500, 401, 500);
    }

    // 🔹 PM10
    private int subIndexPM10(Double c) {
        if (c == null) return 0;

        if (c <= 50) return calculate(c, 0, 50, 0, 50);
        if (c <= 100) return calculate(c, 51, 100, 51, 100);
        if (c <= 250) return calculate(c, 101, 250, 101, 200);
        if (c <= 350) return calculate(c, 251, 350, 201, 300);
        if (c <= 430) return calculate(c, 351, 430, 301, 400);
        return calculate(c, 431, 600, 401, 500);
    }

    // 🔹 NO2
    private int subIndexNO2(Double c) {
        if (c == null) return 0;

        if (c <= 40) return calculate(c, 0, 40, 0, 50);
        if (c <= 80) return calculate(c, 41, 80, 51, 100);
        if (c <= 180) return calculate(c, 81, 180, 101, 200);
        if (c <= 280) return calculate(c, 181, 280, 201, 300);
        if (c <= 400) return calculate(c, 281, 400, 301, 400);
        return calculate(c, 401, 1000, 401, 500);
    }

    // 🔹 SO2
    private int subIndexSO2(Double c) {
        if (c == null) return 0;

        if (c <= 40) return calculate(c, 0, 40, 0, 50);
        if (c <= 80) return calculate(c, 41, 80, 51, 100);
        if (c <= 380) return calculate(c, 81, 380, 101, 200);
        if (c <= 800) return calculate(c, 381, 800, 201, 300);
        if (c <= 1600) return calculate(c, 801, 1600, 301, 400);
        return calculate(c, 1601, 2000, 401, 500);
    }

    // 🔹 CO (mg/m³)
    private int subIndexCO(Double c) {
        if (c == null) return 0;

        if (c <= 1) return calculate(c, 0, 1, 0, 50);
        if (c <= 2) return calculate(c, 1.1, 2, 51, 100);
        if (c <= 10) return calculate(c, 2.1, 10, 101, 200);
        if (c <= 17) return calculate(c, 10.1, 17, 201, 300);
        if (c <= 34) return calculate(c, 17.1, 34, 301, 400);
        return calculate(c, 34.1, 50, 401, 500);
    }

    // 🔹 Ozone (O3)
    private int subIndexO3(Double c) {
        if (c == null) return 0;

        if (c <= 50) return calculate(c, 0, 50, 0, 50);
        if (c <= 100) return calculate(c, 51, 100, 51, 100);
        if (c <= 168) return calculate(c, 101, 168, 101, 200);
        if (c <= 208) return calculate(c, 169, 208, 201, 300);
        if (c <= 748) return calculate(c, 209, 748, 301, 400);
        return calculate(c, 749, 1000, 401, 500);
    }

    @Override
    public void process(String key,
            ProcessWindowFunction<AqiAccumulator, AqiResult, String, TimeWindow>.Context context,
            Iterable<AqiAccumulator> elements, Collector<AqiResult> out) throws Exception {

        AqiAccumulator acc = elements.iterator().next();

        if (acc.count == 0) return;

        AqiResult result = new AqiResult();
        
        if("Hotspot".equals(type)){
            String[] ch_pair=key.split(":");
            result.setCity(ch_pair[0]);
            result.setHotspot(ch_pair[1]);
        }
        else{
            result.setHotspot("ALL");
            result.setCity(key);
        }
        
        result.setCount(acc.count);


        result.setPm25(acc.pm25 / acc.count);
        result.setPm10(acc.pm10 / acc.count);
        result.setNo(acc.no / acc.count);
        result.setNo2(acc.no2 / acc.count);
        result.setNox(acc.nox / acc.count);
        result.setNh3(acc.nh3 / acc.count);
        result.setSo2(acc.so2 / acc.count);
        result.setCo(acc.co / acc.count);
        result.setOzone(acc.ozone / acc.count);

        result.setBenzene(acc.benzene / acc.count);
        result.setToluene(acc.toluene / acc.count);
        result.setXylene(acc.xylene / acc.count);
        result.setOXylene(acc.oXylene / acc.count);
        result.setEthBenzene(acc.ethBenzene / acc.count);
        result.setMpXylene(acc.mpXylene / acc.count);

        result.setTemperature(acc.temperature / acc.count);
        result.setHumidity(acc.humidity / acc.count);
        result.setWindSpeed(acc.windSpeed / acc.count);
        result.setWindDirection(acc.windDirection / acc.count); 

        result.setRainfall(acc.rainfall);
        result.setTotalRainfall(acc.totalRainfall);

        result.setSolarRadiation(acc.solarRadiation / acc.count);
        result.setPressure(acc.pressure / acc.count);
        result.setVerticalWindSpeed(acc.verticalWindSpeed / acc.count);

        
        Integer aqi = computeAqi(result);
        result.setWindowEndTS(context.window().getEnd());
        DateTimeFormatter formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String readable = Instant.ofEpochMilli(result.getWindowEndTS()).atZone(ZoneId.systemDefault()).format(formatter);

        result.setTimestamp(readable);

        result.setAqi(aqi);


        log.info("city={}, records={}, windowEnd={} , at {}", 
         acc.city,
         acc.count, 
         readable,
        java.time.LocalDateTime.now());

        out.collect(result);
    }
}