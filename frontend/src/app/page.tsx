import Link from "next/link";

export default function HomePage() {
	return (
		<>
			<p className="eyebrow">A little help, close to home.</p>
			<h1>
				What do you need
				<br />
				help with?
			</h1>
			<p className="intro">
				A package across town. Groceries on your doorstep. An extra pair of
				hands, right here in Oslo.
			</p>
			<div className="home-actions">
				<Link className="button" href="/requests/new">
					Create a request <span aria-hidden="true">↗</span>
				</Link>
				<Link className="button secondary" href="/jobs">
					View available jobs <span aria-hidden="true">↗</span>
				</Link>
			</div>
			<p className="home-note">Local errands. Neighbourly help.</p>
		</>
	);
}
