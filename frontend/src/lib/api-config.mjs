export const API_PROXY_PATH = "/backend-api";

export function getApiBaseUrl() {
	const value = process.env.NEXT_PUBLIC_API_URL?.trim();
	if (!value) {
		throw new Error(
			"Set NEXT_PUBLIC_API_URL in frontend/.env.local before starting or building the app.",
		);
	}
	let url;
	try {
		url = new URL(value);
	} catch {
		throw new Error(
			"NEXT_PUBLIC_API_URL must be an absolute HTTP or HTTPS URL.",
		);
	}
	if (
		!["http:", "https:"].includes(url.protocol) ||
		url.username ||
		url.password ||
		url.search ||
		url.hash
	) {
		throw new Error(
			"NEXT_PUBLIC_API_URL must be an HTTP or HTTPS base URL without credentials, a query, or a fragment.",
		);
	}
	return url.toString().replace(/\/+$/, "");
}
