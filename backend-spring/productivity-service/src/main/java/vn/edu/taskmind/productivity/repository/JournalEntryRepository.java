package vn.edu.taskmind.productivity.repository;

import vn.edu.taskmind.productivity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByUserId(Integer userId);
}
