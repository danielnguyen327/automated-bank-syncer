package io.github.danielnguyen327.ledgersync.bank;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findByUserIdOrderByName(UUID userId);

    List<FinancialAccount> findByItemId(UUID itemId);
}
