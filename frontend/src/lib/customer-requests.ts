import type { DeliveryRequest } from "./create-request";
import { isOwnedRequests } from "./request-details.ts";

/** Reject mixed-owner or malformed responses for the signed-in customer. */
export function isCustomerRequests(
	value: unknown,
	userId: number,
): value is DeliveryRequest[] {
	return isOwnedRequests(value, "customerId", userId);
}
