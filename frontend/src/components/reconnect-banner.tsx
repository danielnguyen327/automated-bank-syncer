import type { Bank } from "@/lib/api";
import { ReconnectButton } from "./reconnect-button";

/** Screen A4's banner: one line per bank that needs a new sign-in, each with its own Reconnect button. */
export function ReconnectBanner({ banks }: { banks: Bank[] }) {
  const waiting = banks.filter((bank) => bank.status === "login_required");
  return waiting.map((bank) => (
    <div
      key={bank.id}
      role="status"
      className="flex flex-wrap items-center gap-3 rounded-xl border border-warn-line bg-warn px-4 py-3 text-warn-ink"
    >
      <div className="flex min-w-0 flex-1 basis-64 flex-col gap-0.5">
        <p className="text-sm font-semibold">{bank.institutionName} needs you to sign in again.</p>
        <p className="text-sm">
          Your bank ended the connection, which banks do from time to time. Until you reconnect, its
          balances and transactions won’t update.
        </p>
      </div>
      <ReconnectButton bankId={bank.id} />
    </div>
  ));
}
