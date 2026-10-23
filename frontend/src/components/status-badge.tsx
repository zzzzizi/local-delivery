import type { DeliveryRequest } from "../lib/create-request";

const labels: Record<DeliveryRequest["status"], string> = {
	OPEN: "Open",
	ACCEPTED: "Accepted",
	PICKED_UP: "Picked up",
	DELIVERING: "Delivering",
	DELIVERED: "Delivered",
	CANCELLED: "Cancelled",
};

export function StatusBadge({ status }: { status: DeliveryRequest["status"] }) {
	return (
		<span className="status-badge" data-status={status}>
			<span className="sr-only">Status: </span>
			{labels[status]}
		</span>
	);
}
