import { describe, expect, it } from "vitest";
import { changeOver30Days, chartTicks, formatDay, formatPeriod, spendingBars } from "./dashboard";

describe("formatDay and formatPeriod", () => {
  it("reads dates as UTC days", () => {
    expect(formatDay("2026-10-24")).toBe("Oct 24");
    expect(formatPeriod("2026-10-01", "2026-10-24")).toBe("October 1–24");
    expect(formatPeriod("2026-10-01", "2026-10-01")).toBe("October 1");
  });
});

describe("changeOver30Days", () => {
  it("compares today with 30 days earlier", () => {
    const history = Array.from({ length: 90 }, (_, i) => ({ date: `day-${i}`, netCents: i * 100 }));
    expect(
      changeOver30Days({
        assetsCents: 0,
        owedCents: 0,
        netCents: 8900,
        estimatedBefore: "",
        history,
      }),
    ).toBe(3000);
  });
});

describe("spendingBars", () => {
  const summary = {
    from: "2026-10-01",
    to: "2026-10-24",
    totalCents: 7500,
    previousFrom: "2026-09-01",
    previousTo: "2026-09-24",
    previousTotalCents: 0,
    categories: [
      { category: "Food & drink", cents: 6000, previousCents: 5000 },
      { category: "Shopping", cents: 2000, previousCents: 0 },
      { category: "Travel", cents: -500, previousCents: 0 },
    ],
  };

  it("sizes bars against the biggest category and leaves refunds out", () => {
    const { bars, refundsCents } = spendingBars(summary);
    expect(bars.map((b) => [b.category, b.width, b.share])).toEqual([
      ["Food & drink", 1, 75],
      ["Shopping", 1 / 3, 25],
    ]);
    expect(refundsCents).toBe(500);
  });
  it("has no bars when nothing was spent", () => {
    expect(spendingBars({ ...summary, totalCents: 0, categories: [] })).toEqual({
      bars: [],
      refundsCents: 0,
    });
  });
});

describe("chartTicks", () => {
  it("picks round steps that cover the values", () => {
    expect(chartTicks(1381001, 1543423)).toEqual([1300000, 1400000, 1500000, 1600000]);
    expect(chartTicks(-7716415, -4522843)).toEqual([-8000000, -6000000, -4000000]);
    expect(chartTicks(500, 500)).toEqual([500]);
  });
});
