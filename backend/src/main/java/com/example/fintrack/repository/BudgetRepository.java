package com.example.fintrack.repository;

import com.example.fintrack.model.Budget;
import com.example.fintrack.model.Category;
import com.example.fintrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserAndYearAndMonth(User user, int year, int month);
    Optional<Budget> findByUserAndYearAndMonthAndCategory(User user, int year, int month, Category category);
}
