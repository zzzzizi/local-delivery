import type { Metadata } from "next";
import { PageShell } from "../../../components/page-shell";

export const metadata: Metadata = { title: "Create Request" };

export default function CreateRequestPage() {
	return (
		<PageShell
			title="Create Request"
			description="Tell a neighbour what you need and where it needs to go."
			notice="The request form is coming soon. You’ll be able to ask for a delivery, a shopping errand, or a pickup."
		/>
	);
}
