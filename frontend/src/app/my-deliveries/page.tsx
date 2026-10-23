import { LoadingState } from "../../components/loading-state";
import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { DeliveryBoard } from "../../components/delivery-board";
import { ApiError } from "../../lib/api";
import { fetchDeliveries } from "../../lib/helper-deliveries";

export const metadata: Metadata = { title: "My Deliveries" };

async function Deliveries() {
	await connection();
	try {
		return <DeliveryBoard initialDeliveries={await fetchDeliveries()} />;
	} catch (error) {
		return (
			<div className="error-notice jobs-error" role="alert">
				<h2>We couldn’t load your deliveries</h2>
				<p>
					{error instanceof ApiError
						? error.message
						: "Unable to reach the delivery service. Please try again."}
				</p>
				<div className="request-actions">
					<a className="button secondary" href="/my-deliveries">
						Try again
					</a>
					<Link className="text-link" href="/jobs" prefetch={false}>
						Find available jobs
					</Link>
				</div>
			</div>
		);
	}
}

export default function MyDeliveriesPage() {
	return (
		<>
			<h1>My Deliveries</h1>
			<p className="intro">
				Keep the errands you’re helping with in one place.
			</p>
			<Suspense fallback={<LoadingState message="Loading your deliveries…" />}>
				<Deliveries />
			</Suspense>
		</>
	);
}
