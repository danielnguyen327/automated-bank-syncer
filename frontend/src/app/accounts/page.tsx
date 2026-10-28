import type { Metadata } from "next";
import { AppShell, LoadError, PageTitle } from "@/components/app-shell";
import { ConnectBankButton } from "@/components/connect-bank-button";
import { FirstVisit } from "@/components/first-visit";
import { YourBanks } from "@/components/your-banks";
import { getBanks, requireUser } from "@/lib/session";
import { getTheme } from "@/lib/theme";

export const metadata: Metadata = { title: "Accounts" };

/** The connected banks and their accounts, with syncing, connecting and disconnecting. */
export default async function AccountsPage() {
  const [user, theme] = await Promise.all([requireUser(), getTheme()]);
  const banks = await getBanks();

  return (
    <AppShell user={user} theme={theme} current="accounts">
      <PageTitle
        action={
          banks?.length ? <ConnectBankButton label="Connect another bank" look="outline" /> : null
        }
      >
        Accounts
      </PageTitle>
      {banks === null ? (
        <LoadError what="banks" />
      ) : banks.length === 0 ? (
        <FirstVisit />
      ) : (
        <YourBanks banks={banks} />
      )}
    </AppShell>
  );
}
