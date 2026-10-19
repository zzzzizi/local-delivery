import type { Metadata } from "next";
import { PageShell } from "../../components/page-shell";

export const metadata: Metadata = { title: "My Deliveries" };

export default function MyDeliveriesPage() {
	return (
		<PageShell
			title="My Deliveries"
			description="Keep the errands you’re helping with in one place."
			notice="Delivery tracking is coming soon. Your accepted jobs and their next steps will appear here."
		/>
	);
}
