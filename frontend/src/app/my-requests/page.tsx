import { LoadingState } from "../../components/loading-state";
import type { Metadata } from "next";
import { Suspense } from "react";
import { CustomerRequests } from "../../components/customer-requests";

export const metadata: Metadata = { title: "My Requests" };

export default function MyRequestsPage() {
	return (
		<>
			<h1>My Requests</h1>
			<p className="intro">Follow the errands you’ve asked for help with.</p>
			<Suspense fallback={<LoadingState message="Loading your requests…" />}>
				<CustomerRequests />
			</Suspense>
		</>
	);
}
