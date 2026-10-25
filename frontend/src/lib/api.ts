import { API_PROXY_PATH, getApiBaseUrl } from "./api-config.mjs";

export class ApiError extends Error {
	readonly status: number;
	readonly fields: Record<string, string>;

	constructor(
		status: number,
		message: string,
		fields: Record<string, string> = {},
	) {
		super(message);
		this.name = "ApiError";
		this.status = status;
		this.fields = fields;
	}
}

type ApiOptions = Omit<RequestInit, "body"> & { body?: unknown };

/** JSON requests from either Server or Client Components. Empty responses return undefined. */
export async function apiRequest<T>(
	path: string,
	options: ApiOptions = {},
): Promise<T | undefined> {
	if (!path.startsWith("/") || path.startsWith("//")) {
		throw new Error(
			"API paths must start with a single slash, for example /requests.",
		);
	}
	const baseUrl =
		typeof window === "undefined" ? getApiBaseUrl() : API_PROXY_PATH;
	const { body, ...init } = options;
	const headers = new Headers(init.headers);
	headers.set("Accept", "application/json");
	if (body !== undefined) headers.set("Content-Type", "application/json");
	// Session cookies are HttpOnly. CSRF tokens are short-lived and fetched anew after login/logout.
	if (
		typeof window !== "undefined" &&
		!["GET", "HEAD", "OPTIONS"].includes((init.method ?? "GET").toUpperCase())
	) {
		const csrf = await apiRequest<{ token: string; headerName: string }>(
			"/auth/csrf",
		);
		if (!csrf?.token || csrf.headerName !== "X-CSRF-TOKEN")
			throw new ApiError(
				403,
				"Unable to verify your session. Refresh and try again.",
			);
		headers.set(csrf.headerName, csrf.token);
	}

	const response = await fetch(`${baseUrl}${path}`, {
		...init,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body),
		cache: init.cache ?? "no-store",
		signal: init.signal ?? AbortSignal.timeout(10_000),
	});
	const text = await response.text();
	let data: unknown;
	try {
		data = text ? JSON.parse(text) : undefined;
	} catch {
		if (response.ok)
			throw new ApiError(
				response.status,
				"The server returned an invalid JSON response.",
			);
	}

	if (!response.ok) {
		const error =
			data && typeof data === "object" ? (data as Record<string, unknown>) : {};
		const fields: Record<string, string> = {};
		if (error.fields && typeof error.fields === "object") {
			for (const [key, value] of Object.entries(error.fields)) {
				if (typeof value === "string") fields[key] = value;
			}
		}
		throw new ApiError(
			response.status,
			typeof error.message === "string"
				? error.message
				: `Request failed (${response.status}). Please try again.`,
			fields,
		);
	}
	return data as T | undefined;
}
