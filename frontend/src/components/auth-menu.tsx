"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError, apiRequest } from "../lib/api";
import type { SessionUser } from "../lib/server-api";

export function AuthMenu({ user }: { user: SessionUser | null }) {
	const [pending, setPending] = useState(false);
	const [error, setError] = useState("");
	async function logout() {
		if (pending) return;
		setPending(true);
		setError("");
		try {
			await apiRequest("/auth/logout", { method: "POST" });
			// A full navigation clears client-side caches belonging to the previous account.
			window.location.assign("/login");
		} catch (error) {
			setError(
				error instanceof ApiError
					? error.message
					: "Unable to log out. Please try again.",
			);
			setPending(false);
		}
	}
	return (
		<div className="auth-menu">
			{user ? (
				<>
					<span>Signed in as {user.name}</span>
					<button
						type="button"
						className="button secondary"
						disabled={pending}
						onClick={logout}
					>
						{pending ? "Logging out…" : "Log out"}
					</button>
				</>
			) : (
				<>
					<Link href="/login">Log in</Link>
					<Link href="/register">Create account</Link>
				</>
			)}
			{error && <p role="alert">{error}</p>}
		</div>
	);
}
