import Link from "next/link";

export default function RequestNotFound() {
	return (
		<>
			<h1>Request not found</h1>
			<p className="intro">
				This delivery request doesn’t exist. Choose an available job to
				continue.
			</p>
			<Link className="button secondary" href="/jobs" prefetch={false}>
				Back to available jobs
			</Link>
		</>
	);
}
