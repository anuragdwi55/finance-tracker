package com.example.fintrack.repository;

import com.example.fintrack.model.Category;
import com.example.fintrack.model.Transaction;
import com.example.fintrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);
    List<Transaction> findByUser(User user);

    List<Transaction> findByUserAndCategoryAndDateBetween(
            User user, Category category, LocalDate start, LocalDate end);
}
