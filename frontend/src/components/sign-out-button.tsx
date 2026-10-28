"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { signOut } from "@/lib/api";

export function SignOutButton() {
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);
  const [failed, setFailed] = useState(false);
  const [refreshing, startRefresh] = useTransition();

  async function handleClick() {
    setSigningOut(true);
    setFailed(false);
    try {
      await signOut();
      startRefresh(() => router.refresh());
    } catch {
      setFailed(true);
    } finally {
      setSigningOut(false);
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={signingOut || refreshing}
      className="h-11 rounded-lg border border-field-line bg-surface px-4 text-sm font-medium whitespace-nowrap hover:bg-muted disabled:opacity-70"
    >
      {failed ? "Retry sign out" : "Sign out"}
    </button>
  );
}
