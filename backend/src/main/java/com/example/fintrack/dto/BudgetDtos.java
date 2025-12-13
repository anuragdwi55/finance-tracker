package com.example.fintrack.dto;

import java.math.BigDecimal;
import java.util.List;

public class BudgetDtos {
    public record BudgetView(Long id, String category, int year, int month, BigDecimal limit) {}
    public record BudgetUpsert(String category, BigDecimal limit) {}
    public record BudgetUpsertList(List<BudgetUpsert> items) {}
}
