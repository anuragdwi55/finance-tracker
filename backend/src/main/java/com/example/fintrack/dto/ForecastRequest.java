package com.example.fintrack.dto;

import java.util.List;

public record ForecastRequest(List<SavingsMonth> months) { }