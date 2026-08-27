package com.example.the_greatest_project.repo;

import com.example.the_greatest_project.domain.ChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {
    List<ChatTurn> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);
}
