package io.github.danielnguyen327.ledgersync.bank;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PlaidItemRepository extends JpaRepository<PlaidItem, UUID> {

    List<PlaidItem> findByUserIdOrderByCreatedAt(UUID userId);

    List<PlaidItem> findByStatus(String status);

    /** Finds a bank only if it belongs to this user, so nobody can reach another user's bank. */
    Optional<PlaidItem> findByIdAndUserId(UUID id, UUID userId);

    /** Loads a bank and locks its row until the transaction ends, so two syncs of one bank take turns saving. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlaidItem> findLockedById(UUID id);
}
