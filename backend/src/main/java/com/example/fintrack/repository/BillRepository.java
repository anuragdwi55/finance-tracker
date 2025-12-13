package com.example.fintrack.repository;

import com.example.fintrack.model.Bill;
import com.example.fintrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByUserOrderByCreatedAtDesc(User u);
    List<Bill> findByUserAndActiveTrue(User u);
    Optional<Bill> findByIdAndUser(Long id, User u);
    List<Bill> findByActiveTrue(); // for scheduler (all users)
}
