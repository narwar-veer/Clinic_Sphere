package com.clinic.repository;

import com.clinic.entity.OutboxEvent;
import com.clinic.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventKey(String eventKey);

    @Query(value = """
            select * from outbox_events
            where status in ('PENDING', 'RETRY')
              and next_attempt_at <= :now
              and (locked_at is null or locked_at < :lockExpiry)
            order by id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") LocalDateTime now,
                                    @Param("lockExpiry") LocalDateTime lockExpiry,
                                    @Param("batchSize") int batchSize);

    @Modifying
    @Query("update OutboxEvent e set e.status = :status, e.processedAt = :processedAt, e.lockedAt = null where e.id = :id")
    int markStatus(@Param("id") Long id, @Param("status") OutboxStatus status, @Param("processedAt") LocalDateTime processedAt);
}