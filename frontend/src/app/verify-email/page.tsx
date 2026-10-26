import type { Metadata } from "next";
import { AccountActionForm } from "../../components/account-action-form";
import { accountActions } from "../../lib/account-actions";

const action = "verify-email";
export const metadata: Metadata = {
	title: accountActions[action].title,
	robots: { index: false, follow: false },
	referrer: "no-referrer",
};
export default function AccountPage() {
	return (
		<>
			<h1>{accountActions[action].title}</h1>
			<p className="intro">{accountActions[action].intro}</p>
			<AccountActionForm action={action} />
		</>
	);
}
