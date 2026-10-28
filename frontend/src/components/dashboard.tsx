import Link from "next/link";
import type { Transaction } from "@/lib/api";
import { changeOver30Days, formatDay } from "@/lib/dashboard";
import { formatCents } from "@/lib/money";
import type { DashboardData } from "@/lib/session";
import { NetWorthChart } from "./net-worth-chart";
import { SpendingCard } from "./spending-card";

const card = "flex min-w-0 flex-col gap-3 rounded-2xl border border-line bg-surface p-5";

/** Screen A3: net worth, this month's summary, spending by category, and the latest transactions. */
export function Dashboard({ data }: { data: DashboardData }) {
  return (
    <div className="flex flex-col gap-5">
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <NetWorthCard data={data} />
        <MonthlySummary />
      </div>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <SpendingCard spending={data.spending} />
        <RecentTransactions transactions={data.recent.transactions} />
      </div>
    </div>
  );
}

function NetWorthCard({ data }: { data: DashboardData }) {
  const { netWorth } = data;
  const change = changeOver30Days(netWorth);
  const firstDay = netWorth.history[0]?.date;
  const estimated = firstDay !== undefined && netWorth.estimatedBefore > firstDay;

  return (
    <section aria-labelledby="net-worth-title" className={card}>
      <div className="flex items-center justify-between gap-3">
        <h2 id="net-worth-title" className="text-[15px] font-semibold">
          Net worth
        </h2>
        <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-ink">
          Last 90 days
        </span>
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-[32px] leading-tight font-semibold tracking-tight tabular-nums">
          {formatCents(netWorth.netCents)}
        </span>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-ink-2">
          <span>
            {change >= 0 ? "Up" : "Down"}{" "}
            <span className="font-mono text-ink">{formatCents(Math.abs(change))}</span> in 30 days
          </span>
          <span>
            In your accounts{" "}
            <span className="font-mono text-ink">{formatCents(netWorth.assetsCents)}</span>
          </span>
          <span>
            Owed <span className="font-mono text-ink">{formatCents(netWorth.owedCents)}</span>
          </span>
        </div>
      </div>
      <NetWorthChart netWorth={netWorth} />
      {estimated && (
        <p className="text-xs text-ink-3">
          The dashed part, before {formatDay(netWorth.estimatedBefore)}, is estimated from your
          transactions. From then on, LedgerSync saves each day’s real balances.
        </p>
      )}
    </section>
  );
}

function MonthlySummary() {
  return (
    <section aria-labelledby="summary-title" className={`${card} lg:self-start`}>
      <div className="flex items-center justify-between gap-3">
        <h2 id="summary-title" className="text-[15px] font-semibold">
          What changed this month
        </h2>
        <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-ink">
          AI summary
        </span>
      </div>
      <p className="text-sm text-ink-2">
        A short summary of your month will appear here. Claude writes the sentences, and LedgerSync
        calculates every number in them from your transactions.
      </p>
    </section>
  );
}

function RecentTransactions({ transactions }: { transactions: Transaction[] }) {
  return (
    <section aria-labelledby="recent-title" className={card}>
      <div className="flex items-center justify-between gap-3">
        <h2 id="recent-title" className="text-[15px] font-semibold">
          Recent transactions
        </h2>
        <Link href="/transactions" className="text-sm font-medium text-accent hover:underline">
          See all
        </Link>
      </div>
      {transactions.length === 0 ? (
        <p className="py-6 text-center text-sm text-ink-2">
          No transactions yet. They arrive with the first sync.
        </p>
      ) : (
        <ul className="divide-y divide-line">
          {transactions.map((t) => (
            <li key={t.id} className="flex items-center gap-3 py-2.5">
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="flex items-center gap-2 text-sm font-medium">
                  <span className="truncate">{t.merchantName ?? t.name}</span>
                  {t.pending && (
                    <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-ink">
                      Pending
                    </span>
                  )}
                </span>
                <span className="truncate text-xs text-ink-3">
                  {t.category} · {t.accountName.replace("Plaid ", "")}
                  {t.accountMask && ` ••${t.accountMask}`} · {formatDay(t.date)}
                </span>
              </div>
              <Amount cents={t.amountCents} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Money out shows as a plain amount; money in shows as +amount in green. */
function Amount({ cents }: { cents: number }) {
  return cents < 0 ? (
    <span className="font-mono text-sm whitespace-nowrap text-good tabular-nums">
      +{formatCents(-cents)}
    </span>
  ) : (
    <span className="font-mono text-sm whitespace-nowrap tabular-nums">{formatCents(cents)}</span>
  );
}
