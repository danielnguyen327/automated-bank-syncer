import { z } from "zod";

export const userSchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string(),
});

export type User = z.infer<typeof userSchema>;

const accountSchema = z.object({
  id: z.string(),
  name: z.string(),
  mask: z.string().nullable(),
  type: z.string(),
  subtype: z.string().nullable(),
  currentBalanceCents: z.number().nullable(),
  availableBalanceCents: z.number().nullable(),
  isoCurrencyCode: z.string().nullable(),
});

const syncRunSchema = z.object({
  startedAt: z.string(),
  triggeredBy: z.string(),
  status: z.string(),
  added: z.number(),
  updated: z.number(),
  removed: z.number(),
  error: z.string().nullable(),
});

export const bankSchema = z.object({
  id: z.string(),
  institutionName: z.string(),
  status: z.string(),
  lastSyncedAt: z.string().nullable(),
  transactionCount: z.number(),
  accounts: z.array(accountSchema),
  recentSyncs: z.array(syncRunSchema),
});

const syncResultSchema = z.object({
  ready: z.boolean(),
  added: z.number(),
  updated: z.number(),
  removed: z.number(),
});

const bankSyncSchema = z.object({
  bankId: z.string(),
  bankName: z.string(),
  result: syncResultSchema.nullable(),
  error: z.string().nullable(),
});

const transactionSchema = z.object({
  id: z.string(),
  date: z.string(),
  name: z.string(),
  merchantName: z.string().nullable(),
  amountCents: z.number(),
  isoCurrencyCode: z.string().nullable(),
  pending: z.boolean(),
  category: z.string(),
  accountId: z.string(),
  accountName: z.string(),
  accountMask: z.string().nullable(),
  accountType: z.string(),
});

export const transactionPageSchema = z.object({
  transactions: z.array(transactionSchema),
  hasMore: z.boolean(),
});

export const spendingSchema = z.object({
  from: z.string(),
  to: z.string(),
  totalCents: z.number(),
  previousFrom: z.string(),
  previousTo: z.string(),
  previousTotalCents: z.number(),
  categories: z.array(
    z.object({ category: z.string(), cents: z.number(), previousCents: z.number() }),
  ),
});

export const netWorthSchema = z.object({
  assetsCents: z.number(),
  owedCents: z.number(),
  netCents: z.number(),
  estimatedBefore: z.string(),
  history: z.array(z.object({ date: z.string(), netCents: z.number() })),
});

export type Account = z.infer<typeof accountSchema>;
export type Transaction = z.infer<typeof transactionSchema>;
export type TransactionPage = z.infer<typeof transactionPageSchema>;
export type SpendingSummary = z.infer<typeof spendingSchema>;
export type NetWorth = z.infer<typeof netWorthSchema>;
export type Bank = z.infer<typeof bankSchema>;
export type SyncRun = z.infer<typeof syncRunSchema>;
export type SyncResult = z.infer<typeof syncResultSchema>;
export type BankSync = z.infer<typeof bankSyncSchema>;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

/** The sentence to show when a request fails. */
export function messageFor(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "Can’t reach LedgerSync right now. Try again in a moment.";
}

/** Reads one cookie from a `document.cookie`-style string. */
export function readCookie(cookies: string, name: string): string | undefined {
  for (const part of cookies.split(";")) {
    const [key, ...value] = part.trim().split("=");
    if (key === name) return decodeURIComponent(value.join("="));
  }
  return undefined;
}

const problemSchema = z.object({ detail: z.string().min(1) });

/** The backend explains errors in the "detail" field of a problem-details body. */
export async function errorMessage(response: Response): Promise<string> {
  const problem = problemSchema.safeParse(await response.json().catch(() => null));
  if (problem.success) return problem.data.detail;
  if (response.status === 403) return "Your session expired. Reload the page and try again.";
  return "Something went wrong. Try again in a moment.";
}

/**
 * The backend wants its XSRF-TOKEN cookie copied into an X-XSRF-TOKEN header on every POST and
 * DELETE. The cookie is missing on a first visit and right after signing in, so ask for a new one then.
 */
async function csrfToken(): Promise<string> {
  const existing = readCookie(document.cookie, "XSRF-TOKEN");
  if (existing) return existing;
  await fetch("/api/auth/csrf");
  return readCookie(document.cookie, "XSRF-TOKEN") ?? "";
}

/** Sends a request that changes something, and throws an ApiError if the backend says no. */
async function send(method: "POST" | "DELETE", path: string, body?: object): Promise<Response> {
  const headers: Record<string, string> = { "X-XSRF-TOKEN": await csrfToken() };
  if (body) headers["Content-Type"] = "application/json";
  const response = await fetch(path, {
    method,
    headers,
    body: body && JSON.stringify(body),
  });
  if (!response.ok) throw new ApiError(response.status, await errorMessage(response));
  return response;
}

export async function signIn(email: string, password: string): Promise<void> {
  await send("POST", "/api/auth/login", { email, password });
}

export async function createAccount(name: string, email: string, password: string): Promise<void> {
  await send("POST", "/api/auth/register", { name, email, password });
}

export async function signOut(): Promise<void> {
  await send("POST", "/api/auth/logout");
}

/** Plaid's categories as LedgerSync names them, for filtering transactions. */
export const CATEGORY_NAMES = [
  "Bank fees",
  "Entertainment",
  "Food & drink",
  "Government & nonprofits",
  "Home improvement",
  "Income",
  "Loan payments",
  "Medical",
  "Personal care",
  "Rent & utilities",
  "Services",
  "Shopping",
  "Transfers in",
  "Transfers out",
  "Transportation",
  "Travel",
  "Other",
];

export type TransactionFilters = {
  accountType: "" | "depository" | "credit";
  category: string;
  search: string;
};

/** The query string for GET /api/transactions. Filters that aren't set are left out. */
export function transactionsQuery(filters: TransactionFilters, page: number, size = 50): string {
  const params = new URLSearchParams();
  if (filters.accountType) params.set("accountType", filters.accountType);
  if (filters.category) params.set("category", filters.category);
  if (filters.search.trim()) params.set("search", filters.search.trim());
  params.set("page", String(page));
  params.set("size", String(size));
  return params.toString();
}

/** One page of the user's transactions, newest first. */
export async function listTransactions(
  filters: TransactionFilters,
  page: number,
): Promise<TransactionPage> {
  const response = await fetch(`/api/transactions?${transactionsQuery(filters, page)}`);
  if (!response.ok) throw new ApiError(response.status, await errorMessage(response));
  return transactionPageSchema.parse(await response.json());
}

const linkTokenSchema = z.object({ linkToken: z.string() });

/** A one-time token that opens Plaid's bank sign-in window. */
export async function createLinkToken(): Promise<string> {
  const response = await send("POST", "/api/banks/link-token");
  return linkTokenSchema.parse(await response.json()).linkToken;
}

/** A one-time token that reopens Plaid's window for a bank that needs you to sign in again. */
export async function createReconnectLinkToken(bankId: string): Promise<string> {
  const response = await send("POST", `/api/banks/${bankId}/link-token`);
  return linkTokenSchema.parse(await response.json()).linkToken;
}

/** Sandbox only: makes one of your banks need a new sign-in, so you can try reconnecting. */
export async function expireSandboxSignIn(bankId: string): Promise<void> {
  await send("POST", `/api/sandbox/banks/${bankId}/expire-sign-in`);
}

/** Hands the backend the public token Plaid gave the browser. Only the backend sees the real access token. */
export async function connectBank(publicToken: string): Promise<Bank> {
  const response = await send("POST", "/api/banks", { publicToken });
  return bankSchema.parse(await response.json());
}

/** Downloads the bank's newest transactions from Plaid. */
export async function syncBank(bankId: string): Promise<SyncResult> {
  const response = await send("POST", `/api/banks/${bankId}/sync`);
  return syncResultSchema.parse(await response.json());
}

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Plaid needs a few seconds to gather a new bank's transactions, so this tries several times. */
export async function syncNewBank(bankId: string, pause = wait): Promise<SyncResult> {
  for (let attempt = 1; ; attempt++) {
    const result = await syncBank(bankId);
    if (result.ready || attempt === 6) return result;
    await pause(2000);
  }
}

/** Syncs every bank the user has. Each bank reports its own result or error. */
export async function syncAllBanks(): Promise<BankSync[]> {
  const response = await send("POST", "/api/banks/sync");
  return z.array(bankSyncSchema).parse(await response.json());
}

type Changes = { added: number; updated: number; removed: number };

/** "3 new, 1 removed", or "" when nothing changed. */
function describeChanges(changes: Changes): string {
  return [
    changes.added > 0 && `${changes.added} new`,
    changes.updated > 0 && `${changes.updated} updated`,
    changes.removed > 0 && `${changes.removed} removed`,
  ]
    .filter(Boolean)
    .join(", ");
}

/** One sentence about what a sync changed. */
export function describeSync(result: SyncResult): string {
  if (!result.ready)
    return "Plaid is still gathering this bank’s transactions. Try again in a minute.";
  const changes = describeChanges(result);
  return changes ? `Synced: ${changes}.` : "No new transactions.";
}

/** What "Sync all" did: the combined changes, then each bank that failed and why. */
export function describeSyncAll(results: BankSync[]): string {
  const done = results.flatMap((r) => (r.result ? [r.result] : []));
  const errors = results.flatMap((r) => (r.error ? [r.error] : []));
  if (done.length === 0) return errors.join(" ") || "There are no banks to sync.";
  const total = done.reduce(
    (sum, r) => ({
      added: sum.added + r.added,
      updated: sum.updated + r.updated,
      removed: sum.removed + r.removed,
    }),
    { added: 0, updated: 0, removed: 0 },
  );
  const banks =
    done.length === results.length
      ? `Synced ${results.length} ${results.length === 1 ? "bank" : "banks"}`
      : `Synced ${done.length} of ${results.length} banks`;
  const changes = describeChanges(total);
  return [changes ? `${banks}: ${changes}.` : `${banks}. No new transactions.`, ...errors].join(
    " ",
  );
}

/** The result part of a line in a bank's recent syncs. */
export function describeRun(run: SyncRun): string {
  if (run.status === "running") return "Syncing…";
  if (run.error) return run.error;
  return describeChanges(run) || "No new transactions";
}

export async function disconnectBank(bankId: string): Promise<void> {
  await send("DELETE", `/api/banks/${bankId}`);
}

export async function deleteAccount(): Promise<void> {
  await send("DELETE", "/api/me");
}
