"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { ApiError, apiRequest } from "../lib/api";

export function AuthForm({ register = false }: { register?: boolean }) {
	const [pending, setPending] = useState(false);
	const [error, setError] = useState("");
	const busy = useRef(false);
	const feedback = useRef<HTMLParagraphElement>(null);
	async function submit(event: React.FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (busy.current) return;
		busy.current = true;
		setPending(true);
		setError("");
		const form = new FormData(event.currentTarget);
		const email = String(form.get("email") ?? "").trim();
		const password = String(form.get("password") ?? "");
		try {
			if (register) {
				await apiRequest("/auth/register", {
					method: "POST",
					body: {
						name: String(form.get("name") ?? "").trim(),
						email,
						password,
					},
				});
				window.location.assign("/login?registered=1");
			} else {
				await apiRequest("/auth/login", {
					method: "POST",
					body: { email, password },
				});
				window.location.assign("/jobs");
			}
		} catch (failure) {
			setError(
				failure instanceof ApiError
					? failure.status === 409
						? "An account with this email already exists. Try logging in."
						: Object.values(failure.fields).join(" ") || failure.message
					: "Unable to reach the service. Please try again.",
			);
			setPending(false);
			busy.current = false;
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
				role="alert"
				className={error ? "error-notice" : ""}
			>
				{error}
			</p>
			{register && (
				<div className="form-field">
					<label htmlFor="name">Name</label>
					<input
						id="name"
						name="name"
						autoComplete="name"
						maxLength={100}
						required
						disabled={pending}
					/>
				</div>
			)}
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
			<div className="form-field">
				<label htmlFor="password">Password</label>
				<input
					id="password"
					name="password"
					type="password"
					autoComplete={register ? "new-password" : "current-password"}
					minLength={register ? 12 : undefined}
					maxLength={128}
					required
					disabled={pending}
				/>
				{register && (
					<p className="field-hint">
						Use 12–128 characters. You can use a passphrase.
					</p>
				)}
			</div>
			<button className="button" type="submit" disabled={pending}>
				{pending ? "Please wait…" : register ? "Create account" : "Log in"}
			</button>
			<p>
				{register ? "Already registered? " : "New here? "}
				<Link href={register ? "/login" : "/register"}>
					{register ? "Log in" : "Create an account"}
				</Link>
			</p>
		</form>
	);
}
