import type { DeliveryRequest } from "./create-request";
import { isDeliveryRequest } from "./request-details.ts";

/** The MVP page belongs to customer 1; reject mixed-owner or malformed responses. */
export function isCustomerRequests(value: unknown): value is DeliveryRequest[] {
	if (!Array.isArray(value)) return false;
	const ids = new Set<number>();
	return value.every((request) => {
		if (
			!isDeliveryRequest(request) ||
			request.customerId !== 1 ||
			ids.has(request.id)
		)
			return false;
		ids.add(request.id);
		return true;
	});
}
