import type { DeliveryRequest } from "./create-request";
import { isDeliveryRequest } from "./request-details.ts";

/** Reject mixed-owner or malformed responses for the signed-in customer. */
export function isCustomerRequests(
	value: unknown,
	userId: number,
): value is DeliveryRequest[] {
	if (!Array.isArray(value)) return false;
	const ids = new Set<number>();
	return value.every((request) => {
		if (
			!isDeliveryRequest(request) ||
			request.customerId !== userId ||
			ids.has(request.id)
		)
			return false;
		ids.add(request.id);
		return true;
	});
}
