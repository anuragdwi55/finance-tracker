package com.example.fintrack.repository;

import com.example.fintrack.model.Goal;
import com.example.fintrack.model.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {
    List<GoalContribution> findByGoal(Goal goal);
    long deleteByGoal(Goal goal);
}
