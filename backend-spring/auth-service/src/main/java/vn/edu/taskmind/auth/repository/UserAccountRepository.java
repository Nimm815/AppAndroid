package vn.edu.taskmind.auth.repository;

import vn.edu.taskmind.auth.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {
    List<UserAccount> findByUserId(Integer userId);
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
