import { ApiError, apiRequest } from "./api.ts";
import {
	type DeliveryRequest,
	categoryLabels,
	requestStatuses,
} from "./create-request.ts";

export function isRequestId(id: string): boolean {
	return (
		/^[1-9]\d{0,18}$/.test(id) && BigInt(id) <= BigInt("9223372036854775807")
	);
}

export const isPositiveId = (id: unknown): id is number =>
	typeof id === "number" && Number.isSafeInteger(id) && id > 0;

export const isMoney = (amount: unknown): amount is string =>
	typeof amount === "string" && /^\d{1,8}\.\d{2}$/.test(amount);

export const isOffsetDate = (time: unknown): time is string =>
	typeof time === "string" &&
	/T.+(?:Z|[+-]\d{2}:\d{2})$/.test(time) &&
	Number.isFinite(Date.parse(time));

export const isCategory = (category: unknown): boolean =>
	typeof category === "string" && Object.hasOwn(categoryLabels, category);

const statuses: readonly unknown[] = requestStatuses;

export function isDeliveryRequest(value: unknown): value is DeliveryRequest {
	if (!value || typeof value !== "object") return false;
	const request = value as Record<string, unknown>;
	return (
		isPositiveId(request.id) &&
		isPositiveId(request.customerId) &&
		(request.helperId === null || isPositiveId(request.helperId)) &&
		isCategory(request.category) &&
		statuses.includes(request.status) &&
		["title", "description", "pickupAddress", "deliveryAddress"].every(
			(key) => typeof request[key] === "string",
		) &&
		(request.shoppingBudget === null || isMoney(request.shoppingBudget)) &&
		isMoney(request.helperReward) &&
		[request.deadline, request.createdAt, request.updatedAt].every(isOffsetDate)
	);
}

/** Reject malformed, duplicate, or mixed-owner list responses for the signed-in user. */
export function isOwnedRequests(
	value: unknown,
	owner: "customerId" | "helperId",
	userId: number,
): value is DeliveryRequest[] {
	if (!Array.isArray(value)) return false;
	const ids = new Set<number>();
	return value.every((request) => {
		if (
			!isDeliveryRequest(request) ||
			request[owner] !== userId ||
			ids.has(request.id)
		)
			return false;
		ids.add(request.id);
		return true;
	});
}

export async function fetchRequest(
	id: string,
	headers?: HeadersInit,
): Promise<DeliveryRequest> {
	if (!isRequestId(id)) throw new ApiError(404, "Delivery request not found.");
	const data = await apiRequest<unknown>(`/requests/${id}`, { headers });
	if (!isDeliveryRequest(data) || String(data.id) !== id) {
		throw new ApiError(
			502,
			"The server returned invalid request details. Please try again.",
		);
	}
	return data;
}
