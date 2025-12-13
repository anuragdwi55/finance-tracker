package com.example.fintrack.repository;

import com.example.fintrack.model.Alert;
import com.example.fintrack.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop50ByUserOrderByCreatedAtDesc(User u);
    Optional<Alert> findByIdAndUser(Long id, User u);

    // robust count (no naming ambiguity)
    @Query("select count(a) from Alert a where a.user = :user and a.readFlag = false")
    long countUnread(@Param("user") User user);

    // clean up alerts on TX delete
    @Transactional
    long deleteByTxIdAndUser(Long txId, User user);
}
