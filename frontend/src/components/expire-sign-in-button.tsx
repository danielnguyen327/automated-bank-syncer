"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { expireSandboxSignIn, messageFor } from "@/lib/api";

/** Sandbox only: makes the bank need a new sign-in, the way real banks sometimes do, to try Reconnect. */
export function ExpireSignInButton({ bankId }: { bankId: string }) {
  const router = useRouter();
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, startRefresh] = useTransition();

  async function expire() {
    setWorking(true);
    setError(null);
    try {
      await expireSandboxSignIn(bankId);
      startRefresh(() => router.refresh());
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setWorking(false);
    }
  }

  return (
    <p className="text-xs text-ink-3">
      Sandbox: want to try reconnecting?{" "}
      <button
        type="button"
        onClick={expire}
        disabled={working || refreshing}
        className="font-medium text-accent hover:underline disabled:opacity-70"
      >
        {working || refreshing ? "Working…" : "Make this bank need a new sign-in"}
      </button>
      {error && (
        <span role="alert" className="block text-danger">
          {error}
        </span>
      )}
    </p>
  );
}
