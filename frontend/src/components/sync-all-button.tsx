"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { describeSyncAll, messageFor, syncAllBanks } from "@/lib/api";
import { SyncIcon } from "./icons";

/** Syncs every connected bank at once, then says what changed and which banks need attention. */
export function SyncAllButton() {
  const router = useRouter();
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, startRefresh] = useTransition();
  const busy = working || refreshing;

  async function syncAll() {
    setWorking(true);
    setMessage(null);
    setError(null);
    try {
      setMessage(describeSyncAll(await syncAllBanks()));
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
        onClick={syncAll}
        disabled={busy}
        className="flex h-9 items-center gap-2 rounded-lg border border-field-line bg-surface px-3 text-sm font-medium hover:bg-muted disabled:opacity-70"
      >
        <SyncIcon className={`size-4 ${busy ? "animate-spin motion-reduce:animate-none" : ""}`} />
        {busy ? "Syncing…" : "Sync all"}
      </button>
      {message && (
        <p role="status" className="max-w-[40ch] text-right text-xs text-ink-3">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="max-w-[40ch] text-right text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
