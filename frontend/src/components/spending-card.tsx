"use client";

import { useState } from "react";
import type { SpendingSummary } from "@/lib/api";
import { formatPeriod, spendingBars } from "@/lib/dashboard";
import { formatCents } from "@/lib/money";

type Accounts = "all" | "depository" | "credit";

const choices: { value: Accounts; label: string }[] = [
  { value: "all", label: "All accounts" },
  { value: "depository", label: "Bank accounts" },
  { value: "credit", label: "Credit cards" },
];

/** This month's spending by category, for all accounts, bank accounts only, or credit cards only. */
export function SpendingCard({ spending }: { spending: Record<Accounts, SpendingSummary> }) {
  const [accounts, setAccounts] = useState<Accounts>("all");
  const [hovered, setHovered] = useState<string | null>(null);
  const summary = spending[accounts];
  const { bars, refundsCents } = spendingBars(summary);

  return (
    <section
      aria-labelledby="spending-title"
      className="flex min-w-0 flex-col gap-4 rounded-2xl border border-line bg-surface p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="spending-title" className="text-[15px] font-semibold">
            Spending by category
          </h2>
          <p className="text-xs text-ink-3">{formatPeriod(summary.from, summary.to)}</p>
        </div>
        <div
          role="group"
          aria-label="Which accounts"
          className="flex gap-0.5 rounded-lg border border-field-line bg-muted p-0.5"
        >
          {choices.map((choice) => (
            <button
              key={choice.value}
              type="button"
              aria-pressed={accounts === choice.value}
              onClick={() => setAccounts(choice.value)}
              className="h-8 rounded-md px-2.5 text-[13px] font-medium whitespace-nowrap text-ink-2 aria-pressed:bg-surface aria-pressed:text-ink aria-pressed:shadow-sm"
            >
              {choice.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-ink-2">
        <span>
          Total <span className="font-mono text-ink">{formatCents(summary.totalCents)}</span>
        </span>
        <span>
          Same days last month{" "}
          <span className="font-mono text-ink">{formatCents(summary.previousTotalCents)}</span>
        </span>
      </div>

      {bars.length === 0 ? (
        <p className="py-6 text-center text-sm text-ink-2">
          No spending on these accounts yet this month.
        </p>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {bars.map((bar) => (
            <li
              key={bar.category}
              tabIndex={0}
              onPointerEnter={() => setHovered(bar.category)}
              onPointerLeave={() => setHovered(null)}
              onFocus={() => setHovered(bar.category)}
              onBlur={() => setHovered(null)}
              className="relative grid grid-cols-[minmax(0,8.5rem)_minmax(0,1fr)_5.5rem] items-center gap-3 text-sm outline-offset-4"
            >
              <span className="truncate">{bar.category}</span>
              <span className="h-3">
                <span
                  className="block h-3 rounded-r bg-accent"
                  style={{ width: `${Math.max(bar.width * 100, 1)}%` }}
                />
              </span>
              <span className="text-right font-mono tabular-nums">{formatCents(bar.cents)}</span>
              {hovered === bar.category && (
                <span className="pointer-events-none absolute bottom-full left-[8.5rem] z-10 mb-1.5 rounded-lg bg-ink px-2.5 py-1.5 text-xs whitespace-nowrap text-surface">
                  {bar.share}% of spending · same days last month{" "}
                  <span className="font-mono">{formatCents(bar.previousCents)}</span>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      {refundsCents > 0 && (
        <p className="text-xs text-ink-3">
          The total subtracts {formatCents(refundsCents)} in refunds.
        </p>
      )}
    </section>
  );
}
