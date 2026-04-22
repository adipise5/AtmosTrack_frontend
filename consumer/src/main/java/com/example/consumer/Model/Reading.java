package com.example.consumer.Model;

import lombok.Data;

@Data
public class Reading {

    // 🔹 Identity
    private String city;
    private String hotspot;
    private String timestamp;

    // 🔹 AQI
    private Integer aqi;

    // 🔹 Core pollutants (AQI relevant)
    private Double pm25;
    private Double pm10;
    private Double no;
    private Double no2;
    private Double nox;
    private Double nh3;
    private Double so2;
    private Double co;
    private Double ozone;

    // 🔹 Organic pollutants
    private Double benzene;
    private Double toluene;
    private Double xylene;
    private Double oXylene;
    private Double ethBenzene;
    private Double mpXylene;

    // 🔹 Weather
    private Double temperature;        // AT
    private Double humidity;           // RH
    private Double windSpeed;          // WS
    private Double windDirection;      // WD
    private Double rainfall;           // RF
    private Double totalRainfall;      // TOT-RF
    private Double solarRadiation;     // SR
    private Double pressure;           // BP
    private Double verticalWindSpeed;  // VWS

    // 🔹 Geo
    private Double latitude;
    private Double longitude;

}