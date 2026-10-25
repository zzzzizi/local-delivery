import type { Metadata } from "next";
import { AuthForm } from "../../components/auth-form";

export const metadata: Metadata = { title: "Create account" };
export default function RegisterPage() {
	return (
		<>
			<h1>Create account</h1>
			<p className="intro">
				Ask for a delivery or help someone nearby with the same account.
			</p>
			<AuthForm register />
		</>
	);
}
