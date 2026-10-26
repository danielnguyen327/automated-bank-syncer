"use client";

import { useEffect, useRef, useState } from "react";
import type { NetWorth } from "@/lib/api";
import { chartTicks, formatDay } from "@/lib/dashboard";
import { formatCents } from "@/lib/money";

const HEIGHT = 180;
const TOP = 18;
const BOTTOM = 26;
const RIGHT = 8;

const compact = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  notation: "compact",
  maximumFractionDigits: 1,
});

/**
 * Net worth for each of the last 90 days. The line is dashed where the days were rebuilt from
 * transactions and solid where LedgerSync saved the real balance. Hover to read a day.
 */
export function NetWorthChart({ netWorth }: { netWorth: NetWorth }) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [hovered, setHovered] = useState<number | null>(null);

  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const observer = new ResizeObserver((entries) => setWidth(entries[0]?.contentRect.width ?? 0));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const points = netWorth.history;
  const values = points.map((p) => p.netCents);
  const valueAt = (i: number) => values[i] ?? 0;
  const ticks = chartTicks(Math.min(...values), Math.max(...values));
  const low = ticks[0] ?? 0;
  const high = ticks.at(-1) ?? low;
  const x = (i: number) => (i / (points.length - 1)) * (width - RIGHT);
  const y = (cents: number) =>
    TOP + (1 - (cents - low) / (high - low || 1)) * (HEIGHT - TOP - BOTTOM);
  const path = (from: number, to: number) =>
    points
      .slice(from, to + 1)
      .map((p, i) => `${i === 0 ? "M" : "L"}${x(from + i).toFixed(1)},${y(p.netCents).toFixed(1)}`)
      .join("");

  const last = points.length - 1;
  const firstSaved = Math.max(
    0,
    points.findIndex((p) => p.date >= netWorth.estimatedBefore),
  );
  const shown = hovered ?? last;
  const monthStarts = points
    .map((p, i) => ({ p, i }))
    .filter(({ p, i }) => p.date.endsWith("-01") && x(i) > 24 && x(i) < width - 24);

  function hover(clientX: number) {
    const left = box.current?.getBoundingClientRect().left ?? 0;
    const i = Math.round(((clientX - left) / (width - RIGHT)) * last);
    setHovered(Math.min(last, Math.max(0, i)));
  }

  return (
    <div
      ref={box}
      className="relative mt-2"
      style={{ height: HEIGHT }}
      role="img"
      aria-label={`Net worth over the last 90 days, from ${formatCents(valueAt(0))} to ${formatCents(valueAt(last))}`}
    >
      {width > 0 && (
        <svg
          width={width}
          height={HEIGHT}
          className="overflow-visible"
          onPointerMove={(e) => hover(e.clientX)}
          onPointerLeave={() => setHovered(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={0} x2={width - RIGHT} y1={y(tick)} y2={y(tick)} className="stroke-line" />
              <text x={0} y={y(tick) - 5} className="fill-ink-3 font-mono text-[11px]">
                {compact.format(tick / 100)}
              </text>
            </g>
          ))}
          {monthStarts.map(({ p, i }) => (
            <text
              key={p.date}
              x={x(i)}
              y={HEIGHT - 6}
              textAnchor="middle"
              className="fill-ink-3 font-mono text-[11px]"
            >
              {formatDay(p.date)}
            </text>
          ))}
          <path
            d={`${path(0, last)}L${x(last)},${y(low)}L${x(0)},${y(low)}Z`}
            className="fill-accent/10"
          />
          {firstSaved > 0 && (
            <path
              d={path(0, firstSaved)}
              className="fill-none stroke-accent"
              strokeWidth={2}
              strokeDasharray="4 4"
            />
          )}
          <path
            d={path(firstSaved, last)}
            className="fill-none stroke-accent"
            strokeWidth={2}
            strokeLinejoin="round"
          />
          {hovered !== null && (
            <line
              x1={x(hovered)}
              x2={x(hovered)}
              y1={TOP}
              y2={HEIGHT - BOTTOM}
              className="stroke-ink-3"
              strokeDasharray="3 3"
            />
          )}
          <circle
            cx={x(shown)}
            cy={y(valueAt(shown))}
            r={4}
            className="fill-accent stroke-surface"
            strokeWidth={2}
          />
          <rect width={width} height={HEIGHT} className="fill-transparent" />
        </svg>
      )}
      {hovered !== null && (
        <div
          className="pointer-events-none absolute -translate-x-1/2 -translate-y-full rounded-lg bg-ink px-2.5 py-1.5 text-xs whitespace-nowrap text-surface"
          style={{
            left: Math.min(Math.max(x(hovered), 64), width - 64),
            top: Math.max(y(valueAt(hovered)) - 10, 40),
          }}
        >
          {formatDay(points[hovered]?.date ?? "")}
          {hovered < firstSaved && " (estimated)"}
          <br />
          <span className="font-mono">{formatCents(valueAt(hovered))}</span>
        </div>
      )}
    </div>
  );
}
