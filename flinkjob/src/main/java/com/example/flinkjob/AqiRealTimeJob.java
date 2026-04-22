package com.example.flinkjob;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;

import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;

import com.example.flinkjob.Model.Reading;
import com.example.flinkjob.Utils.Aggregator;
import com.example.flinkjob.Utils.AqiCalculator;
import com.example.flinkjob.Utils.RedisSink;
import com.example.flinkjob.Utils.RedisWebSocketBridge;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AqiRealTimeJob {

    public static void main(String[] args) throws Exception {

        final RedisWebSocketBridge wsBridge = new RedisWebSocketBridge("localhost", 6379, 8083, 2000);
        Runtime.getRuntime().addShutdownHook(new Thread(wsBridge::close));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("Delhi", "Mumbai", "Bengaluru", "Hyderabad", "Chennai", "Kolkata", "Pune", "Ahmedabad")
                .setGroupId("flink-aqi-consumer-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> stream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source"
        );

        ObjectMapper mapper = new ObjectMapper();

        DataStream<Reading> readings = stream.map(json ->
                mapper.readValue(json, Reading.class)
        );

        readings
            .keyBy((reading)->reading.getCity())
            .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
            .aggregate(new Aggregator(), new AqiCalculator("City"))
            .addSink(new RedisSink("City"));


        readings
            .keyBy((reading)->reading.getCity()+":"+reading.getHotspot())
            .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
            .aggregate(new Aggregator(), new AqiCalculator("Hotspot"))
            .addSink(new RedisSink("Hotspot"));

        try {
            env.execute("AQI Flink Job");
        } finally {
            wsBridge.close();
        }
    }
}