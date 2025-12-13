package com.example.fintrack.repository;

import com.example.fintrack.model.Goal;
import com.example.fintrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserOrderByCreatedAtDesc(User u);
    Optional<Goal> findByIdAndUser(Long id, User u);
}
