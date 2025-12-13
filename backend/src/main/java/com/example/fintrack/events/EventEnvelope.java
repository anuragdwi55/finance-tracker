package com.example.fintrack.events;

import java.time.Instant;
import java.util.Map;

public record EventEnvelope(
        String type,
        Long userId,
        String email,
        Map<String, Object> data,
        Instant ts
) {}
