/**
 * Whether the backend talks to Plaid's Sandbox (made-up banks) rather than real banks. Read on the
 * server from the same PLAID_ENV setting the backend uses; Sandbox unless it's set otherwise.
 */
export function usesSandbox(): boolean {
  return (process.env.PLAID_ENV ?? "sandbox") === "sandbox";
}
