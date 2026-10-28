import type { Metadata } from "next";
import { AppShell, PageTitle } from "@/components/app-shell";
import { DeleteAccount } from "@/components/delete-account";
import { requireUser } from "@/lib/session";
import { getTheme } from "@/lib/theme";

export const metadata: Metadata = { title: "Settings" };

/** Your account details, and deleting the account. */
export default async function SettingsPage() {
  const [user, theme] = await Promise.all([requireUser(), getTheme()]);

  return (
    <AppShell user={user} theme={theme} current="settings">
      <PageTitle>Settings</PageTitle>
      <section
        aria-labelledby="account-title"
        className="flex max-w-2xl flex-col gap-4 rounded-2xl border border-line bg-surface p-5"
      >
        <h2 id="account-title" className="text-[15px] font-semibold">
          Your account
        </h2>
        <dl className="grid grid-cols-[6rem_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
          <dt className="text-ink-3">Name</dt>
          <dd className="truncate">{user.name}</dd>
          <dt className="text-ink-3">Email</dt>
          <dd className="truncate">{user.email}</dd>
          <dt className="text-ink-3">Sign-in</dt>
          <dd>Email and password</dd>
        </dl>
      </section>
      <div className="max-w-2xl">
        <DeleteAccount />
      </div>
    </AppShell>
  );
}
