package io.github.danielnguyen327.ledgersync.bank;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    List<BankTransaction> findByUserIdAndPlaidTransactionIdIn(UUID userId, Collection<String> plaidTransactionIds);

    /** Deletes the given transactions, but only this user's. Returns how many were deleted. */
    @Modifying
    @Query("delete from BankTransaction t where t.userId = :userId and t.plaidTransactionId in :plaidTransactionIds")
    int deleteByPlaidTransactionIds(UUID userId, Collection<String> plaidTransactionIds);

    /** How many transactions each of the user's banks has. */
    @Query("""
        select a.itemId as bankId, count(t) as total
        from BankTransaction t join FinancialAccount a on a.id = t.accountId
        where t.userId = :userId
        group by a.itemId
        """)
    List<BankCount> countByBank(UUID userId);

    interface BankCount {

        UUID getBankId();

        Long getTotal();
    }
}
