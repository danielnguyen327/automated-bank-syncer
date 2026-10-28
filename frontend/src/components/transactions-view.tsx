"use client";

import { useRef, useState } from "react";
import {
  CATEGORY_NAMES,
  listTransactions,
  messageFor,
  type Transaction,
  type TransactionFilters,
  type TransactionPage,
} from "@/lib/api";
import { formatDay } from "@/lib/dashboard";
import { formatCents } from "@/lib/money";
import { SearchIcon } from "./icons";

const NO_FILTERS: TransactionFilters = { accountType: "", category: "", search: "" };
const control =
  "h-10 rounded-lg border border-field-line bg-field px-3 text-sm text-ink focus:border-accent focus:outline-none";

/** The transactions list: search, filters, and "Load more". The first page comes from the server. */
export function TransactionsView({ firstPage }: { firstPage: TransactionPage }) {
  const [filters, setFilters] = useState(NO_FILTERS);
  const [rows, setRows] = useState(firstPage.transactions);
  const [hasMore, setHasMore] = useState(firstPage.hasMore);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const latestRequest = useRef(0);
  const searchTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  async function load(next: TransactionFilters, pageNumber: number) {
    const request = ++latestRequest.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listTransactions(next, pageNumber);
      if (request !== latestRequest.current) return; // a newer search started meanwhile; keep its results
      setRows((shown) =>
        pageNumber === 0 ? result.transactions : [...shown, ...result.transactions],
      );
      setHasMore(result.hasMore);
      setPage(pageNumber);
    } catch (e) {
      if (request === latestRequest.current) setError(messageFor(e));
    } finally {
      if (request === latestRequest.current) setLoading(false);
    }
  }

  /** Changes a filter and reloads from the first page. Typing waits for a pause before searching. */
  function change(next: TransactionFilters, wait: number) {
    setFilters(next);
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => load(next, 0), wait);
  }

  const filtered = Boolean(filters.accountType || filters.category || filters.search.trim());

  return (
    <section aria-label="Transactions" className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        <label className="relative min-w-0 flex-1 basis-56">
          <span className="sr-only">Search by name</span>
          <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" />
          <input
            type="search"
            value={filters.search}
            onChange={(e) => change({ ...filters, search: e.target.value }, 300)}
            placeholder="Search by name"
            className={`${control} w-full pl-9`}
          />
        </label>
        <label>
          <span className="sr-only">Accounts</span>
          <select
            value={filters.accountType}
            onChange={(e) =>
              change(
                { ...filters, accountType: e.target.value as TransactionFilters["accountType"] },
                0,
              )
            }
            className={control}
          >
            <option value="">All accounts</option>
            <option value="depository">Bank accounts</option>
            <option value="credit">Credit cards</option>
          </select>
        </label>
        <label>
          <span className="sr-only">Category</span>
          <select
            value={filters.category}
            onChange={(e) => change({ ...filters, category: e.target.value }, 0)}
            className={control}
          >
            <option value="">All categories</option>
            {CATEGORY_NAMES.map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        </label>
      </div>

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}

      <div
        aria-busy={loading}
        className="rounded-2xl border border-line bg-surface px-5 py-2 aria-busy:opacity-60"
      >
        {rows.length === 0 ? (
          <p className="py-10 text-center text-sm text-ink-2">
            {filtered
              ? "No transactions match. Try a different name or filter."
              : "No transactions yet. They arrive with the first sync."}
          </p>
        ) : (
          byDay(rows).map(([day, transactions]) => (
            <div key={day} className="border-b border-line py-2 last:border-b-0">
              <h2 className="py-1.5 text-xs font-semibold tracking-wider text-ink-3 uppercase">
                {formatDay(day)}
              </h2>
              <ul>
                {transactions.map((t) => (
                  <Row key={t.id} transaction={t} />
                ))}
              </ul>
            </div>
          ))
        )}
      </div>

      {hasMore && (
        <button
          type="button"
          onClick={() => load(filters, page + 1)}
          disabled={loading}
          className="h-10 self-center rounded-lg border border-field-line bg-surface px-4 text-sm font-medium hover:bg-muted disabled:opacity-70"
        >
          {loading ? "Loading…" : "Load more"}
        </button>
      )}
    </section>
  );
}

function Row({ transaction: t }: { transaction: Transaction }) {
  return (
    <li className="flex items-center gap-3 py-2">
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
          {t.accountMask && ` ••${t.accountMask}`}
        </span>
      </div>
      {t.amountCents < 0 ? (
        <span className="font-mono text-sm whitespace-nowrap text-good tabular-nums">
          +{formatCents(-t.amountCents)}
        </span>
      ) : (
        <span className="font-mono text-sm whitespace-nowrap tabular-nums">
          {formatCents(t.amountCents)}
        </span>
      )}
    </li>
  );
}

/** Groups transactions (already newest first) under their day. */
function byDay(transactions: Transaction[]): [string, Transaction[]][] {
  const days = new Map<string, Transaction[]>();
  for (const t of transactions) days.set(t.date, [...(days.get(t.date) ?? []), t]);
  return [...days];
}
