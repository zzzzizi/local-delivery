import type { Metadata } from "next";
import { PageShell } from "../../components/page-shell";

export const metadata: Metadata = { title: "My Requests" };

export default function MyRequestsPage() {
	return (
		<PageShell
			title="My Requests"
			description="A place to follow the errands you’ve asked for help with."
			notice="Request tracking is coming soon. You’ll be able to see your helper and each delivery’s progress here."
		/>
	);
}
