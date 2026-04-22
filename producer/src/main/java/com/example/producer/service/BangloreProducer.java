package com.example.producer.service;

import com.example.producer.Model.Location;
import com.example.producer.Model.Reading;
import com.example.producer.utils.OffsetStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BangloreProducer implements CityProducerPort{

        
    private final OffsetStore offsetStore;
    private static final String CITY = "Bengaluru";
    private static final String FOLDER_PATH = "producer/src/main/resources/static/aqi_readings/Bengaluru";
    private static final int BATCH_SIZE=4;
    private static final DateTimeFormatter FORMATTER =DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private List<File> hotspotFiles;

    private Map<String, Long> filePointers = new HashMap<>();
    private Map<String, Integer> columnIndex = new HashMap<>();
    private Map<String, Location> hotspotMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("static/hotspots.json");


            // File jsonFile = new File("src/main/resources/static/hotspots.json");


            Map<String, Map<String, Location>> allCities =
                    mapper.readValue(is,
                            new TypeReference<Map<String, Map<String, Location>>>() {});

            hotspotMap = allCities.get(CITY);

            
            File folder = new File(FOLDER_PATH);
            hotspotFiles = Arrays.asList(Objects.requireNonNull(folder.listFiles()));

            File firstFile = hotspotFiles.get(0);

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(firstFile), StandardCharsets.UTF_8))) {

                String header = br.readLine();

                if (header != null && header.startsWith("\uFEFF")) {
                    header = header.substring(1);
                }

                String[] cols = header.split(",");

                for (int i = 0; i < cols.length; i++) {
                    columnIndex.put(cols[i].trim(), i);
                }
            }

            
            for (File file : hotspotFiles) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    String hotspot = file.getName().replace(".csv", "");
                    Long pointer=offsetStore.get(hotspot);
                    if(pointer==0){
                        raf.readLine();
                        pointer=raf.getFilePointer();
                    }
                    filePointers.put(file.getName(), pointer);
                }
            }

            log.info("AhmedabadProducer initialized with {} hotspot files", hotspotFiles.size());

        } catch (Exception e) {
            log.error("Initialization failed", e);
        }
    }

    @Override
    public List<Reading> fetch() {

        List<Reading> readings = new ArrayList<>();

        for (File file : hotspotFiles) {

            String fileName = file.getName();
            String hotspot = fileName.replace(".csv", "");

            long pointer = filePointers.getOrDefault(fileName, 0L);

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {

                raf.seek(pointer);

                String line;
                int count=0;
                while ((line = raf.readLine()) != null && count<BATCH_SIZE) {

                    String[] values = line.split(",", -1); 

                    Reading r = map(values);

                    r.setCity(CITY);
                    r.setHotspot(hotspot);

                    Location loc = hotspotMap.get(hotspot);
                    if (loc != null) {
                        r.setLatitude(loc.getLat());
                        r.setLongitude(loc.getLon());
                    }

                    readings.add(r);
                    count++;
                }
                
                if(raf.getFilePointer()>=raf.length()) {
                    raf.seek(0);
                    raf.readLine(); 
                }
                filePointers.put(fileName, raf.getFilePointer());
                offsetStore.put(hotspot, raf.getFilePointer());
 

            } catch (Exception e) {
                log.error("Error processing file {}", fileName, e);
            }
        }

        return readings;
    }

    private Reading map(String[] values) {

        Reading r = new Reading();

        
        r.setTimestamp(java.time.LocalDateTime.now().format(FORMATTER));

        // 🔹 AQI pollutants
        r.setPm25(parse(values, "PM2.5 (µg/m³)"));
        r.setPm10(parse(values, "PM10 (µg/m³)"));
        r.setNo(parse(values, "NO (µg/m³)"));
        r.setNo2(parse(values, "NO2 (µg/m³)"));
        r.setNox(parse(values, "NOx (ppb)"));
        r.setNh3(parse(values, "NH3 (µg/m³)"));
        r.setSo2(parse(values, "SO2 (µg/m³)"));
        r.setCo(parse(values, "CO (mg/m³)"));
        r.setOzone(parse(values, "Ozone (µg/m³)"));

        // 🔹 Organic
        r.setBenzene(parse(values, "Benzene (µg/m³)"));
        r.setToluene(parse(values, "Toluene (µg/m³)"));
        r.setXylene(parse(values, "Xylene (µg/m³)"));
        r.setOXylene(parse(values, "O Xylene (µg/m³)"));
        r.setEthBenzene(parse(values, "Eth-Benzene (µg/m³)"));
        r.setMpXylene(parse(values, "MP-Xylene (µg/m³)"));

        // 🔹 Weather
        r.setTemperature(parse(values, "AT (°C)"));
        r.setHumidity(parse(values, "RH (%)"));
        r.setWindSpeed(parse(values, "WS (m/s)"));
        r.setWindDirection(parse(values, "WD (deg)"));
        r.setRainfall(parse(values, "RF (mm)"));
        r.setTotalRainfall(parse(values, "TOT-RF (mm)"));
        r.setSolarRadiation(parse(values, "SR (W/mt2)"));
        r.setPressure(parse(values, "BP (mmHg)"));
        r.setVerticalWindSpeed(parse(values, "VWS (m/s)"));

        return r;
    }

    
    private Double parse(String[] values, String col) {
        try {
            Integer i = columnIndex.get(col);
            if (i == null || i >= values.length) return null;

            String val = values[i];

            if(val==null){
                return null;
            }

            val=val.trim();
            
            if (val.isBlank() || val.equalsIgnoreCase("NA") || val.equalsIgnoreCase("null")) {
                return null;
            }

            return Double.parseDouble(val);
        } catch (Exception e) {
            return null;
        }
    }

}