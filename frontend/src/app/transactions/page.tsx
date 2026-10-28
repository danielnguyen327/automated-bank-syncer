import type { Metadata } from "next";
import { AppShell, LoadError, PageTitle } from "@/components/app-shell";
import { TransactionsView } from "@/components/transactions-view";
import { getTransactions, requireUser } from "@/lib/session";
import { getTheme } from "@/lib/theme";

export const metadata: Metadata = { title: "Transactions" };

/** Every transaction, newest first, with search and filters. */
export default async function TransactionsPage() {
  const [user, theme] = await Promise.all([requireUser(), getTheme()]);
  const firstPage = await getTransactions();

  return (
    <AppShell user={user} theme={theme} current="transactions">
      <PageTitle>Transactions</PageTitle>
      {firstPage ? <TransactionsView firstPage={firstPage} /> : <LoadError what="transactions" />}
    </AppShell>
  );
}
