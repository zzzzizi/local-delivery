import type { Metadata } from "next";
import { AuthForm } from "../../components/auth-form";

export const metadata: Metadata = { title: "Log in" };
export default async function LoginPage({
	searchParams,
}: {
	searchParams: Promise<{ registered?: string }>;
}) {
	const { registered } = await searchParams;
	return (
		<>
			<h1>Log in</h1>
			<p className="intro">Welcome back to your neighbourhood.</p>
			{registered === "1" && (
				<p className="success-notice" role="status">
					Account created. Check your email and verify your address before
					logging in.
				</p>
			)}
			<AuthForm />
		</>
	);
}
