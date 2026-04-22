package com.example.flinkjob.Utils;

import org.apache.flink.api.common.functions.AggregateFunction;

import com.example.flinkjob.Model.AqiAccumulator;
import com.example.flinkjob.Model.Reading;

public class Aggregator implements AggregateFunction<Reading,AqiAccumulator,AqiAccumulator> {

    @Override
    public AqiAccumulator add(Reading reading, AqiAccumulator acc) {

        if(acc.city==null){
            acc.city=reading.getCity();
        }

        acc.count++;

        acc.pm25 += safe(reading.getPm25());
        acc.pm10 += safe(reading.getPm10());
        acc.no   += safe(reading.getNo());
        acc.no2  += safe(reading.getNo2());
        acc.nox  += safe(reading.getNox());
        acc.nh3  += safe(reading.getNh3());
        acc.so2  += safe(reading.getSo2());
        acc.co   += safe(reading.getCo());
        acc.ozone+= safe(reading.getOzone());
        acc.benzene   += safe(reading.getBenzene());
        acc.toluene   += safe(reading.getToluene());
        acc.xylene    += safe(reading.getXylene());
        acc.oXylene   += safe(reading.getOXylene());
        acc.ethBenzene+= safe(reading.getEthBenzene());
        acc.mpXylene  += safe(reading.getMpXylene());
        acc.temperature        += safe(reading.getTemperature());
        acc.humidity           += safe(reading.getHumidity());
        acc.windSpeed          += safe(reading.getWindSpeed());
        acc.windDirection      += safe(reading.getWindDirection()); 
        acc.rainfall           += safe(reading.getRainfall());      
        acc.totalRainfall      += safe(reading.getTotalRainfall()); 
        acc.solarRadiation     += safe(reading.getSolarRadiation());
        acc.pressure           += safe(reading.getPressure());
        acc.verticalWindSpeed  += safe(reading.getVerticalWindSpeed());

        return acc;
    }

    @Override
    public AqiAccumulator createAccumulator() {
        return new AqiAccumulator();
    }

    @Override
    public AqiAccumulator getResult(AqiAccumulator acc) {
        return acc;
    }

    @Override
    public AqiAccumulator merge(AqiAccumulator a, AqiAccumulator b) {

        a.count += b.count;

        a.pm25 += b.pm25;
        a.pm10 += b.pm10;
        a.no   += b.no;
        a.no2  += b.no2;
        a.nox  += b.nox;
        a.nh3  += b.nh3;
        a.so2  += b.so2;
        a.co   += b.co;
        a.ozone+= b.ozone;

        a.benzene    += b.benzene;
        a.toluene    += b.toluene;
        a.xylene     += b.xylene;
        a.oXylene    += b.oXylene;
        a.ethBenzene += b.ethBenzene;
        a.mpXylene   += b.mpXylene;

        a.temperature       += b.temperature;
        a.humidity          += b.humidity;
        a.windSpeed         += b.windSpeed;
        a.windDirection     += b.windDirection; 
        a.rainfall          += b.rainfall;
        a.totalRainfall     += b.totalRainfall;
        a.solarRadiation    += b.solarRadiation;
        a.pressure          += b.pressure;
        a.verticalWindSpeed += b.verticalWindSpeed;

        return a;
    }

    private double safe(Double val) {
        return val == null ? 0.0 : val;
    }

}