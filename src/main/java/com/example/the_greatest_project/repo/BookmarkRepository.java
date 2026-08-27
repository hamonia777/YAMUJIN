package com.example.the_greatest_project.repo;

import com.example.the_greatest_project.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndUrl(Long userId, String url);
    long countByUserId(Long userId);
}
