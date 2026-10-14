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

export const bankSchema = z.object({
  id: z.string(),
  institutionName: z.string(),
  status: z.string(),
  lastSyncedAt: z.string().nullable(),
  transactionCount: z.number(),
  accounts: z.array(accountSchema),
});

const syncResultSchema = z.object({
  ready: z.boolean(),
  added: z.number(),
  updated: z.number(),
  removed: z.number(),
});

export type Account = z.infer<typeof accountSchema>;
export type Bank = z.infer<typeof bankSchema>;
export type SyncResult = z.infer<typeof syncResultSchema>;

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

const linkTokenSchema = z.object({ linkToken: z.string() });

/** A one-time token that opens Plaid's bank sign-in window. */
export async function createLinkToken(): Promise<string> {
  const response = await send("POST", "/api/banks/link-token");
  return linkTokenSchema.parse(await response.json()).linkToken;
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

/** One sentence about what a sync changed. */
export function describeSync(result: SyncResult): string {
  if (!result.ready)
    return "Plaid is still gathering this bank’s transactions. Try again in a minute.";
  const parts = [
    result.added > 0 && `${result.added} new`,
    result.updated > 0 && `${result.updated} updated`,
    result.removed > 0 && `${result.removed} removed`,
  ].filter(Boolean);
  return parts.length > 0 ? `Synced: ${parts.join(", ")}.` : "Already up to date.";
}

export async function disconnectBank(bankId: string): Promise<void> {
  await send("DELETE", `/api/banks/${bankId}`);
}

export async function deleteAccount(): Promise<void> {
  await send("DELETE", "/api/me");
}
