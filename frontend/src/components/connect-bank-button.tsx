"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { usePlaidLink } from "react-plaid-link";
import { connectBank, createLinkToken, messageFor, syncNewBank } from "@/lib/api";
import { PlusIcon } from "./icons";

const styles = {
  primary: "bg-accent text-on-accent",
  outline: "border border-field-line bg-surface text-ink hover:bg-muted",
};

/** Opens Plaid's bank sign-in window, then saves the new bank on the backend. */
export function ConnectBankButton({
  label,
  look = "primary",
}: {
  label: string;
  look?: keyof typeof styles;
}) {
  const router = useRouter();
  const [linkToken, setLinkToken] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, startRefresh] = useTransition();

  const { open, ready } = usePlaidLink({
    token: linkToken,
    onSuccess: async (publicToken) => {
      setLinkToken(null);
      if (!publicToken) {
        setError("The bank connection didn’t finish. Try again.");
        setWorking(false);
        return;
      }
      try {
        const bank = await connectBank(publicToken);
        setSyncing(true);
        // The bank is saved either way. If this first sync fails, the bank's "Sync now" button shows why.
        await syncNewBank(bank.id).catch(() => undefined);
        startRefresh(() => router.refresh());
      } catch (e) {
        setError(messageFor(e));
      } finally {
        setWorking(false);
        setSyncing(false);
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
      setLinkToken(await createLinkToken());
    } catch (e) {
      setError(messageFor(e));
      setWorking(false);
    }
  }

  const busy = working || refreshing;

  return (
    <div className="flex flex-col items-start gap-2">
      <button
        type="button"
        onClick={handleClick}
        disabled={busy}
        className={`flex h-11 items-center gap-2 rounded-lg px-5 text-[15px] font-semibold disabled:opacity-70 ${styles[look]}`}
      >
        <PlusIcon className="size-4" />
        {busy ? (syncing ? "Getting transactions…" : "Connecting…") : label}
      </button>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
