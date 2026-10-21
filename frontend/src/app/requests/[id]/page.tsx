import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Suspense } from "react";
import { RequestDetails } from "../../../components/request-details";
import { ApiError } from "../../../lib/api";
import { fetchRequest, isRequestId } from "../../../lib/request-details";

export const metadata: Metadata = { title: "Request Details" };
type Props = { params: Promise<{ id: string }> };

async function Details({ params }: Props) {
	const { id } = await params;
	if (!isRequestId(id)) notFound();
	await connection();
	try {
		const request = await fetchRequest(id);
		return <RequestDetails key={request.id} initialRequest={request} />;
	} catch (error) {
		if (error instanceof ApiError && error.status === 404) notFound();
		return (
			<div className="error-notice request-detail" role="alert">
				<h2>We couldn’t load this request</h2>
				<p>
					{error instanceof ApiError
						? error.message
						: "Unable to reach the delivery service. Please try again."}
				</p>
				<div className="request-actions">
					<a className="button secondary" href={`/requests/${id}`}>
						Try again
					</a>
					<Link className="text-link" href="/jobs" prefetch={false}>
						Back to available jobs
					</Link>
				</div>
			</div>
		);
	}
}

export default function RequestDetailsPage(props: Props) {
	return (
		<>
			<h1>Request Details</h1>
			<p className="intro">
				See what’s needed and help a neighbour with their delivery.
			</p>
			<Suspense
				fallback={
					<p className="jobs-feedback" role="status">
						Loading request details…
					</p>
				}
			>
				<Details {...props} />
			</Suspense>
		</>
	);
}
