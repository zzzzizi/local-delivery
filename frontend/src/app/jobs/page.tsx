import type { Metadata } from "next";
import { Suspense } from "react";
import { AvailableJobs, JobsLoading } from "../../components/available-jobs";

export const metadata: Metadata = { title: "Available Jobs" };

export default function AvailableJobsPage() {
	return (
		<>
			<h1>Available Jobs</h1>
			<p className="intro">
				Find a small errand nearby and lend a helping hand.
			</p>
			<Suspense fallback={<JobsLoading />}>
				<AvailableJobs />
			</Suspense>
		</>
	);
}
