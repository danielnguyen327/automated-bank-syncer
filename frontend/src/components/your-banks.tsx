import type { Account, Bank } from "@/lib/api";
import { formatCents } from "@/lib/money";
import { timeAgo } from "@/lib/time";
import { DisconnectBankButton } from "./disconnect-bank-button";
import { BankIcon, CardIcon } from "./icons";
import { SyncBankButton } from "./sync-bank-button";

/** Every connected bank and its accounts, with the balance Plaid reported when the bank was connected. */
export function YourBanks({ banks }: { banks: Bank[] }) {
  const now = new Date();

  return (
    <section aria-labelledby="banks-title" className="flex flex-col gap-3">
      <h2 id="banks-title" className="text-xs font-semibold tracking-wider text-ink-3 uppercase">
        Connected banks
      </h2>
      <ul className="flex flex-col gap-4">
        {banks.map((bank) => (
          <li key={bank.id} className="rounded-2xl border border-line bg-surface">
            <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4">
              <div className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-full bg-muted text-accent">
                  <BankIcon className="size-[18px]" />
                </span>
                <div className="flex flex-col">
                  <span className="text-base font-semibold">{bank.institutionName}</span>
                  <span className="text-xs text-ink-3">{syncStatus(bank, now)}</span>
                </div>
              </div>
              <div className="flex items-start gap-1">
                <SyncBankButton bankId={bank.id} />
                <DisconnectBankButton bankId={bank.id} bankName={bank.institutionName} />
              </div>
            </div>
            <ul className="divide-y divide-line">
              {bank.accounts.map((account) => (
                <AccountRow key={account.id} account={account} />
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** "18 transactions · synced 5 minutes ago", or "Not synced yet". */
function syncStatus(bank: Bank, now: Date): string {
  if (!bank.lastSyncedAt) return "Not synced yet";
  const count = `${bank.transactionCount} ${bank.transactionCount === 1 ? "transaction" : "transactions"}`;
  return `${count} · synced ${timeAgo(bank.lastSyncedAt, now)}`;
}

function AccountRow({ account }: { account: Account }) {
  const isCard = account.type === "credit";
  const isOwed = isCard || account.type === "loan"; // for cards and loans, Plaid's balance is what you owe
  const details = [account.subtype, account.mask && `•••• ${account.mask}`].filter(Boolean);

  return (
    <li className="flex items-center justify-between gap-4 px-5 py-3">
      <div className="flex min-w-0 items-center gap-3">
        {isCard ? (
          <CardIcon className="size-[18px] shrink-0 text-ink-3" />
        ) : (
          <BankIcon className="size-[18px] shrink-0 text-ink-3" />
        )}
        <div className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-medium">{account.name}</span>
          <span className="text-xs text-ink-3">{details.join(" · ")}</span>
        </div>
      </div>
      {account.currentBalanceCents !== null && (
        <div className="flex flex-col items-end">
          <span className="font-mono text-sm tabular-nums">
            {formatCents(account.currentBalanceCents)}
          </span>
          <span className="text-xs text-ink-3">{isOwed ? "owed" : "balance"}</span>
        </div>
      )}
    </li>
  );
}
