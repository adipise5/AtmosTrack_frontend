package com.example.flinkjob.Model;

public class AqiAccumulator {


    public String city;
    // 🔹 AQI
    public Integer aqi=0;
    public Integer count=0;

    // 🔹 Core pollutants (AQI relevant)
    public Double pm25=(double) 0;
    public Double pm10=(double) 0;
    public Double no=(double) 0;
    public Double no2=(double) 0;
    public Double nox=(double) 0;
    public Double nh3=(double) 0;
    public Double so2=(double) 0;
    public Double co=(double) 0;
    public Double ozone=(double) 0;

    // 🔹 Organic pollutants
    public Double benzene=(double) 0;
    public Double toluene=(double) 0;
    public Double xylene=(double) 0;
    public Double oXylene=(double) 0;
    public Double ethBenzene=(double) 0;
    public Double mpXylene=(double) 0;

    // 🔹 Weather
    public Double temperature=(double) 0;        // AT
    public Double humidity=(double) 0;           // RH
    public Double windSpeed=(double) 0;          // WS
    public Double windDirection=(double) 0;      // WD
    public Double rainfall=(double) 0;           // RF
    public Double totalRainfall=(double) 0;      // TOT-RF
    public Double solarRadiation=(double) 0;     // SR
    public Double pressure=(double) 0;           // BP
    public Double verticalWindSpeed=(double) 0;  // VWS

}
