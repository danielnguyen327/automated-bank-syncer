"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { usePlaidLink } from "react-plaid-link";
import { createReconnectLinkToken, messageFor, syncBank } from "@/lib/api";

/**
 * Screen A4: reopens Plaid's window so the user can sign in to their bank again ("update mode"). The
 * connection keeps its access token, so afterwards a sync is all that's left to do.
 */
export function ReconnectButton({ bankId }: { bankId: string }) {
  const router = useRouter();
  const [linkToken, setLinkToken] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, startRefresh] = useTransition();

  const { open, ready } = usePlaidLink({
    token: linkToken,
    onSuccess: async () => {
      setLinkToken(null);
      try {
        await syncBank(bankId);
        startRefresh(() => router.refresh());
      } catch (e) {
        setError(messageFor(e));
      } finally {
        setWorking(false);
      }
    },
    onExit: () => {
      setLinkToken(null);
      setWorking(false);
    },
  });

  // Plaid's window can only open once Plaid's script has loaded the token.
  useEffect(() => {
    if (linkToken && ready) open();
  }, [linkToken, ready, open]);

  async function handleClick() {
    setWorking(true);
    setError(null);
    try {
      setLinkToken(await createReconnectLinkToken(bankId));
    } catch (e) {
      setError(messageFor(e));
      setWorking(false);
    }
  }

  const busy = working || refreshing;

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        onClick={handleClick}
        disabled={busy}
        className="h-9 rounded-lg bg-accent px-3 text-sm font-semibold whitespace-nowrap text-on-accent disabled:opacity-70"
      >
        {busy ? "Reconnecting…" : "Reconnect"}
      </button>
      {error && (
        <p role="alert" className="max-w-[28ch] text-right text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
