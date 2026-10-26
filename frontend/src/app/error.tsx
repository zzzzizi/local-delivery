"use client";

export default function ErrorPage({
	retry,
}: {
	error: unknown;
	retry: () => void;
}) {
	return (
		<section className="error-notice" role="alert">
			<h1>Unable to load this page</h1>
			<p>The service may be temporarily unavailable. Please try again.</p>
			<button type="button" className="button" onClick={retry}>
				Try again
			</button>
		</section>
	);
}
