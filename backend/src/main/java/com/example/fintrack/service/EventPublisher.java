package com.example.fintrack.service;

import com.example.fintrack.events.EventEnvelope;
import com.example.fintrack.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public EventPublisher(KafkaTemplate<String, Object> kafka,
                          @Value("${app.kafka.events-topic:fintrack.events}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publish(String type, User user, Map<String, Object> data) {
        EventEnvelope e = new EventEnvelope(type,
                user != null ? user.getId() : null,
                user != null ? user.getEmail() : null,
                data, Instant.now());
        String key = (user != null ? String.valueOf(user.getId()) : "anon") + ":" + type;
        kafka.send(topic, key, e);
    }
}
