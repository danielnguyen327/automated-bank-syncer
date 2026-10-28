import { AppShell, LoadError, PageTitle } from "@/components/app-shell";
import { Dashboard } from "@/components/dashboard";
import { AfterFirstSync, FirstVisit } from "@/components/first-visit";
import { SignInScreen } from "@/components/sign-in-screen";
import { getBanks, getDashboard, getSession } from "@/lib/session";
import { getTheme } from "@/lib/theme";

/** The sign-in screen when signed out. Signed in, the dashboard, or screen A2 until a bank is connected. */
export default async function Home() {
  const [session, theme] = await Promise.all([getSession(), getTheme()]);
  if (session.status !== "signed-in") {
    return <SignInScreen theme={theme} unavailable={session.status === "unavailable"} />;
  }
  const banks = await getBanks();
  const dashboard = banks?.length ? await getDashboard() : null;
  const firstName = session.user.name.split(" ")[0] ?? session.user.name;

  return (
    <AppShell user={session.user} theme={theme} current="dashboard">
      <PageTitle>Welcome, {firstName}</PageTitle>
      {banks === null ? (
        <LoadError what="banks" />
      ) : banks.length === 0 ? (
        <>
          <FirstVisit />
          <AfterFirstSync />
        </>
      ) : dashboard ? (
        <Dashboard data={dashboard} />
      ) : (
        <LoadError what="dashboard" />
      )}
    </AppShell>
  );
}
