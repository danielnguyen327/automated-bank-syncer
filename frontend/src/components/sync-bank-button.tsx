"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { describeSync, messageFor, syncBank } from "@/lib/api";

/** Downloads one bank's newest transactions, then says what changed. */
export function SyncBankButton({ bankId }: { bankId: string }) {
  const router = useRouter();
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, startRefresh] = useTransition();
  const busy = working || refreshing;

  async function sync() {
    setWorking(true);
    setMessage(null);
    setError(null);
    try {
      setMessage(describeSync(await syncBank(bankId)));
      startRefresh(() => router.refresh());
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setWorking(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        onClick={sync}
        disabled={busy}
        className="h-9 rounded-lg px-3 text-sm font-medium text-ink-2 hover:bg-muted hover:text-ink disabled:opacity-70"
      >
        {busy ? "Syncing…" : "Sync now"}
      </button>
      {message && (
        <p role="status" className="max-w-[28ch] text-right text-xs text-ink-3">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="max-w-[28ch] text-right text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
