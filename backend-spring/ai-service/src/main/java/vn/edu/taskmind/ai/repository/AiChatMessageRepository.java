package vn.edu.taskmind.ai.repository;

import vn.edu.taskmind.ai.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
    List<AiChatMessage> findByChatIdOrderByCreatedAtAscMessageIdAsc(Integer chatId);
}
