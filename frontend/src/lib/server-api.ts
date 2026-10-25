import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import { ApiError, apiRequest } from "./api";

export type SessionUser = { id: number; name: string; email: string };

/** Forward only this app's session cookie from the incoming request, never cache per-user data. */
export async function sessionHeaders(): Promise<Record<string, string>> {
	const session = (await cookies()).get("DELIVERY_SESSION");
	return session ? { Cookie: `DELIVERY_SESSION=${session.value}` } : {};
}

export const currentUser = cache(async (): Promise<SessionUser | null> => {
	try {
		return (
			(await apiRequest<SessionUser>("/auth/me", {
				headers: await sessionHeaders(),
			})) ?? null
		);
	} catch (error) {
		if (error instanceof ApiError && error.status === 401) return null;
		throw error;
	}
});

export async function requireUser() {
	const user = await currentUser();
	if (!user) redirect("/login");
	return user;
}
