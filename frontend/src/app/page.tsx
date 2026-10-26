import { SignInScreen } from "@/components/sign-in-screen";
import { SignedInHome } from "@/components/signed-in-home";
import { getBanks, getDashboard, getSession } from "@/lib/session";
import { getTheme } from "@/lib/theme";

/** One page: the sign-in screen when signed out, the app when signed in. */
export default async function Home() {
  const [session, theme] = await Promise.all([getSession(), getTheme()]);
  if (session.status !== "signed-in") {
    return <SignInScreen theme={theme} unavailable={session.status === "unavailable"} />;
  }
  const banks = await getBanks();
  const dashboard = banks?.length ? await getDashboard() : null;
  return <SignedInHome user={session.user} theme={theme} banks={banks} dashboard={dashboard} />;
}
