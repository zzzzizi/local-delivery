"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError, apiRequest } from "../lib/api";
import {
	type AccountAction,
	accountActions,
	accountActionBody,
	accountTokenFromHash,
} from "../lib/account-actions";

export function AccountActionForm({ action }: { action: AccountAction }) {
	const [token, setToken] = useState("");
	const [pending, setPending] = useState(false);
	const [error, setError] = useState("");
	const [message, setMessage] = useState("");
	const busy = useRef(false);
	const tokenRead = useRef(false);
	const feedback = useRef<HTMLParagraphElement>(null);
	const needsToken = action === "verify-email" || action === "reset-password";
	useEffect(() => {
		if (!needsToken || tokenRead.current) return;
		tokenRead.current = true;
		setToken(accountTokenFromHash(window.location.hash));
		// Fragments never reach the server; remove the secret from the visible URL/history too.
		window.history.replaceState(
			null,
			"",
			window.location.pathname + window.location.search,
		);
	}, [needsToken]);
	async function submit(event: React.FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (busy.current) return;
		busy.current = true;
		setPending(true);
		setError("");
		setMessage("");
		try {
			const data = new FormData(event.currentTarget);
			const body = accountActionBody(action, {
				token,
				email: String(data.get("email") ?? ""),
				password: String(data.get("password") ?? ""),
				confirmation: String(data.get("confirmation") ?? ""),
			});
			const result = await apiRequest<{ message: string }>(`/auth/${action}`, {
				method: "POST",
				body,
			});
			if (!result?.message)
				throw new Error(
					"The service returned an unexpected response. Please try again.",
				);
			setMessage(result.message);
			if (needsToken) setToken("");
		} catch (failure) {
			setError(
				failure instanceof ApiError
					? Object.values(failure.fields).join(" ") || failure.message
					: failure instanceof Error
						? failure.message
						: "Unable to reach the service. Please try again.",
			);
		} finally {
			busy.current = false;
			setPending(false);
			setTimeout(() => feedback.current?.focus(), 0);
		}
	}
	return (
		<form
			className="request-form auth-form"
			onSubmit={submit}
			aria-busy={pending}
		>
			<p
				ref={feedback}
				tabIndex={-1}
				role={error ? "alert" : "status"}
				className={error ? "error-notice" : message ? "success-notice" : ""}
			>
				{error || message}
			</p>
			{!message && (
				<>
					{!needsToken && (
						<div className="form-field">
							<label htmlFor="email">Email</label>
							<input
								id="email"
								name="email"
								type="email"
								autoComplete="email"
								maxLength={320}
								required
								disabled={pending}
							/>
						</div>
					)}
					{action === "reset-password" && (
						<>
							<div className="form-field">
								<label htmlFor="password">New password</label>
								<input
									id="password"
									name="password"
									type="password"
									autoComplete="new-password"
									minLength={12}
									maxLength={128}
									required
									disabled={pending}
								/>
							</div>
							<div className="form-field">
								<label htmlFor="confirmation">Confirm new password</label>
								<input
									id="confirmation"
									name="confirmation"
									type="password"
									autoComplete="new-password"
									minLength={12}
									maxLength={128}
									required
									disabled={pending}
								/>
							</div>
						</>
					)}
					<button type="submit" className="button" disabled={pending}>
						{pending ? "Please wait…" : accountActions[action].button}
					</button>
				</>
			)}
			{needsToken && (
				<Link
					href={
						action === "verify-email"
							? "/resend-verification"
							: "/forgot-password"
					}
				>
					Request a new link
				</Link>
			)}
			<Link href="/login">Back to log in</Link>
		</form>
	);
}
