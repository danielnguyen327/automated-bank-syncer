import { describeRun, type Account, type Bank, type SyncRun } from "@/lib/api";
import { formatCents } from "@/lib/money";
import { usesSandbox } from "@/lib/plaid";
import { timeAgo } from "@/lib/time";
import { DisconnectBankButton } from "./disconnect-bank-button";
import { ExpireSignInButton } from "./expire-sign-in-button";
import { BankIcon, CardIcon } from "./icons";
import { ReconnectButton } from "./reconnect-button";
import { SyncAllButton } from "./sync-all-button";
import { SyncBankButton } from "./sync-bank-button";

/** Every connected bank and its accounts, with the balances from the last sync. */
export function YourBanks({ banks }: { banks: Bank[] }) {
  const now = new Date();
  const sandbox = usesSandbox();

  return (
    <section aria-labelledby="banks-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 id="banks-title" className="text-xs font-semibold tracking-wider text-ink-3 uppercase">
          Connected banks
        </h2>
        {banks.length > 1 && <SyncAllButton />}
      </div>
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
                  {needsSignIn(bank) ? (
                    <span className="text-xs font-medium text-warn-ink">
                      Needs you to sign in again
                    </span>
                  ) : (
                    <span className="text-xs text-ink-3">{syncStatus(bank, now)}</span>
                  )}
                </div>
              </div>
              <div className="flex items-start gap-1">
                {needsSignIn(bank) ? (
                  <ReconnectButton bankId={bank.id} />
                ) : (
                  <SyncBankButton bankId={bank.id} />
                )}
                <DisconnectBankButton bankId={bank.id} bankName={bank.institutionName} />
              </div>
            </div>
            {needsSignIn(bank) && (
              <p className="border-b border-warn-line bg-warn px-5 py-3 text-sm text-warn-ink">
                {bank.institutionName} ended the connection, which banks do from time to time. Use
                Reconnect to sign in again. Until then, its balances and transactions won’t update.
              </p>
            )}
            <ul className="divide-y divide-line">
              {bank.accounts.map((account) => (
                <AccountRow key={account.id} account={account} />
              ))}
            </ul>
            {sandbox && !needsSignIn(bank) && (
              <div className="border-t border-line px-5 py-2.5">
                <ExpireSignInButton bankId={bank.id} />
              </div>
            )}
            {bank.recentSyncs.length > 0 && <RecentSyncs runs={bank.recentSyncs} now={now} />}
          </li>
        ))}
      </ul>
    </section>
  );
}

function needsSignIn(bank: Bank): boolean {
  return bank.status === "login_required";
}

/** The last few syncs: when, what started it, and what changed or went wrong. */
function RecentSyncs({ runs, now }: { runs: SyncRun[]; now: Date }) {
  return (
    <div className="flex flex-col gap-1.5 rounded-b-2xl border-t border-line bg-page px-5 py-3">
      <h3 className="text-[11px] font-semibold tracking-wider text-ink-3 uppercase">
        Recent syncs
      </h3>
      <ul className="flex flex-col gap-1 text-xs">
        {runs.map((run) => (
          <li key={run.startedAt} className="flex flex-wrap gap-x-2">
            <span className="text-ink-2">{timeAgo(run.startedAt, now)}</span>
            <span className="text-ink-3">
              · {run.triggeredBy === "schedule" ? "Automatic" : "Sync now"} ·
            </span>
            <span className={run.status === "failed" ? "text-danger" : "text-ink-2"}>
              {describeRun(run)}
            </span>
          </li>
        ))}
      </ul>
    </div>
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
