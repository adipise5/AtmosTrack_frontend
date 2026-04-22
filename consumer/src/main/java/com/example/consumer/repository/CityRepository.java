package com.example.consumer.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.consumer.Model.Reading;

@Repository
public class CityRepository {


    private final String sql = """
        INSERT INTO readings (
            city, hotspot, timestamp, aqi,
            pm25, pm10, no, no2, nox, nh3, so2, co, ozone,
            benzene, toluene, xylene, oXylene, ethBenzene, mpXylene,
            temperature, humidity, windSpeed, windDirection,
            rainfall, totalRainfall, solarRadiation, pressure, verticalWindSpeed,
            latitude, longitude
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    public void save(JdbcTemplate connection, List<Reading> readings) {

        connection.batchUpdate(
            sql,
            readings,
            readings.size(),
            (ps, r) -> {

                ps.setString(1, r.getCity());
                ps.setString(2, r.getHotspot());
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(r.getTimestamp()));
                ps.setObject(4, r.getAqi());

                ps.setObject(5, r.getPm25());
                ps.setObject(6, r.getPm10());
                ps.setObject(7, r.getNo());
                ps.setObject(8, r.getNo2());
                ps.setObject(9, r.getNox());
                ps.setObject(10, r.getNh3());
                ps.setObject(11, r.getSo2());
                ps.setObject(12, r.getCo());
                ps.setObject(13, r.getOzone());

                ps.setObject(14, r.getBenzene());
                ps.setObject(15, r.getToluene());
                ps.setObject(16, r.getXylene());
                ps.setObject(17, r.getOXylene());
                ps.setObject(18, r.getEthBenzene());
                ps.setObject(19, r.getMpXylene());

                ps.setObject(20, r.getTemperature());
                ps.setObject(21, r.getHumidity());
                ps.setObject(22, r.getWindSpeed());
                ps.setObject(23, r.getWindDirection());

                ps.setObject(24, r.getRainfall());
                ps.setObject(25, r.getTotalRainfall());
                ps.setObject(26, r.getSolarRadiation());
                ps.setObject(27, r.getPressure());
                ps.setObject(28, r.getVerticalWindSpeed());

                ps.setObject(29, r.getLatitude());
                ps.setObject(30, r.getLongitude());
            }
        );
    }
        
}

