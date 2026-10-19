import type { Metadata } from "next";
import { PageShell } from "../../components/page-shell";

export const metadata: Metadata = { title: "Available Jobs" };

export default function AvailableJobsPage() {
	return (
		<PageShell
			title="Available Jobs"
			description="Find a small errand nearby and lend a helping hand."
			notice="Browsing local requests is coming soon. Available errands will appear here when the job list is ready."
		/>
	);
}
