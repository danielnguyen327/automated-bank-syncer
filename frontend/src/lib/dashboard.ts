import type { NetWorth, SpendingSummary } from "./api";

const utcDay = (isoDate: string) => new Date(`${isoDate}T00:00:00Z`);

/** "Oct 24" for an ISO date like "2026-10-24". Read as UTC, so every server and browser agrees. */
export function formatDay(isoDate: string): string {
  return utcDay(isoDate).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/** "October 1–24" for a period inside one month. */
export function formatPeriod(from: string, to: string): string {
  const month = utcDay(from).toLocaleDateString("en-US", { month: "long", timeZone: "UTC" });
  const first = utcDay(from).getUTCDate();
  const last = utcDay(to).getUTCDate();
  return first === last ? `${month} ${first}` : `${month} ${first}–${last}`;
}

/** How much net worth changed since 30 days ago. */
export function changeOver30Days(netWorth: NetWorth): number {
  const history = netWorth.history;
  const then = history[Math.max(0, history.length - 31)];
  return then ? netWorth.netCents - then.netCents : 0;
}

export type SpendingBar = {
  category: string;
  cents: number;
  previousCents: number;
  /** Percent of all spending in categories that had spending. */
  share: number;
  /** Bar length, 0 to 1, against the biggest category. */
  width: number;
};

/**
 * Categories with spending, biggest first. A refund can outweigh a category's spending (a refunded
 * flight makes Travel negative); those categories have no bar and are added up as refunds instead.
 */
export function spendingBars(summary: SpendingSummary): {
  bars: SpendingBar[];
  refundsCents: number;
} {
  const spent = summary.categories.filter((c) => c.cents > 0).sort((a, b) => b.cents - a.cents);
  const spentTotal = spent.reduce((sum, c) => sum + c.cents, 0);
  const biggest = spent[0]?.cents ?? 0;
  const refundsCents = summary.categories
    .filter((c) => c.cents < 0)
    .reduce((sum, c) => sum - c.cents, 0);
  return {
    bars: spent.map((c) => ({
      ...c,
      share: Math.round((c.cents / spentTotal) * 100),
      width: c.cents / biggest,
    })),
    refundsCents,
  };
}

/** Evenly spaced round values covering min to max, for a chart's grid lines and labels. */
export function chartTicks(min: number, max: number): number[] {
  const span = max - min || Math.abs(max) || 100;
  const rough = span / 2;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step = magnitude * ([1, 2, 5, 10].find((n) => n * magnitude >= rough) ?? 10);
  const ticks = [];
  for (let tick = Math.floor(min / step) * step; tick < max + step; tick += step) {
    ticks.push(tick);
    if (tick >= max) break;
  }
  return ticks;
}
