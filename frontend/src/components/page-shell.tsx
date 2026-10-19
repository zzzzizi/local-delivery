import Link from "next/link";

type PageShellProps = { title: string; description: string; notice: string };

export function PageShell({ title, description, notice }: PageShellProps) {
	return (
		<>
			<p className="eyebrow">Your neighbourhood, a little closer.</p>
			<h1>{title}</h1>
			<p className="intro">{description}</p>
			<section className="coming-soon" aria-label="Availability">
				<h2>Coming soon</h2>
				<p>{notice}</p>
			</section>
			<Link className="text-link" href="/">
				Back to home <span aria-hidden="true">↗</span>
			</Link>
		</>
	);
}
