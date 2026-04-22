package com.example.consumer.Utils;

import org.springframework.stereotype.Component;

import com.example.consumer.Model.Reading;

@Component
public class AqiCalculator {

    public void compute(Reading r) {

        int maxAqi = 0;

        maxAqi = Math.max(maxAqi, subIndexPM25(r.getPm25()));
        maxAqi = Math.max(maxAqi, subIndexPM10(r.getPm10()));
        maxAqi = Math.max(maxAqi, subIndexNO2(r.getNo2()));
        maxAqi = Math.max(maxAqi, subIndexSO2(r.getSo2()));
        maxAqi = Math.max(maxAqi, subIndexCO(r.getCo()));
        maxAqi = Math.max(maxAqi, subIndexO3(r.getOzone()));

        r.setAqi(maxAqi == 0 ? null : maxAqi);
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
}