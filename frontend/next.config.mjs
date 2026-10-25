import { API_PROXY_PATH, getApiBaseUrl } from "./src/lib/api-config.mjs";

/** @type {import('next').NextConfig} */
const nextConfig = {
	...(process.env.DOCKER_BUILD === "true" ? { output: "standalone" } : {}),
	rewrites() {
		return [
			{
				source: `${API_PROXY_PATH}/:path*`,
				destination: `${getApiBaseUrl()}/:path*`,
			},
		];
	},
};

export default nextConfig;
