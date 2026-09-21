package com.ecommerce.user_service.messaging;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface AccountMessageRepository extends JpaRepository<AccountMessage,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from AccountMessage m where m.id=:id") Optional<AccountMessage> lock(String id);
    @Query("select m.id from AccountMessage m where m.status='PENDING' and m.nextAttemptAt<=:now order by m.nextAttemptAt")
    List<String> due(Instant now,Pageable page);
}
