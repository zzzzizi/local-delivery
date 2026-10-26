import { ApiError, apiRequest } from "./api.ts";
import type { DeliveryRequest } from "./create-request";
import { isDeliveryRequest, isOwnedRequests } from "./request-details.ts";

export function isHelperDeliveries(
	value: unknown,
	userId: number,
): value is DeliveryRequest[] {
	return isOwnedRequests(value, "helperId", userId);
}

export function deliveryAction(request: DeliveryRequest, userId: number) {
	if (request.helperId !== userId) return null;
	switch (request.status) {
		case "ACCEPTED":
			return { status: "PICKED_UP", label: "Mark Picked Up" } as const;
		case "PICKED_UP":
			return { status: "DELIVERING", label: "Start Delivery" } as const;
		case "DELIVERING":
			return { status: "DELIVERED", label: "Mark Delivered" } as const;
		default:
			return null;
	}
}

export async function fetchDeliveries(
	userId: number,
	headers?: HeadersInit,
): Promise<DeliveryRequest[]> {
	const data = await apiRequest<unknown>("/me/deliveries", { headers });
	if (!isHelperDeliveries(data, userId))
		throw new ApiError(
			502,
			"The server returned invalid delivery data. Please try again.",
		);
	return data;
}

type UpdateResult = {
	deliveries?: DeliveryRequest[];
	saved?: DeliveryRequest;
	kind: "success" | "error";
	message: string;
	requiresRefresh: boolean;
};

const progress = ["ACCEPTED", "PICKED_UP", "DELIVERING", "DELIVERED"];

/** Send one explicit transition. Never retry a PATCH after a conflict or uncertain response. */
export async function advanceDelivery(
	request: DeliveryRequest,
	userId: number,
): Promise<UpdateResult> {
	const action = isDeliveryRequest(request)
		? deliveryAction(request, userId)
		: null;
	if (!action)
		throw new ApiError(409, "This delivery has no available next step.");
	let saved: DeliveryRequest;
	try {
		const data = await apiRequest<unknown>(`/requests/${request.id}/status`, {
			method: "PATCH",
			body: { status: action.status },
		});
		if (
			!isDeliveryRequest(data) ||
			data.id !== request.id ||
			data.helperId !== userId ||
			data.status !== action.status
		) {
			throw new ApiError(
				502,
				"The server returned an unexpected status update response.",
			);
		}
		saved = data;
	} catch (error) {
		let deliveries: DeliveryRequest[] | undefined;
		try {
			deliveries = await fetchDeliveries(userId);
		} catch {
			/* A manual refresh must succeed before another update. */
		}
		const current = deliveries?.find((item) => item.id === request.id);
		const uncertain =
			!(error instanceof ApiError) || error.status < 400 || error.status >= 500;
		if (
			uncertain &&
			current &&
			progress.indexOf(current.status) >= progress.indexOf(action.status)
		) {
			return {
				deliveries,
				kind: "success",
				requiresRefresh: false,
				message: `Request #${request.id}: the latest saved status is ${current.status}.`,
			};
		}
		return {
			deliveries,
			kind: "error",
			requiresRefresh: !deliveries || current?.status === request.status,
			message: `Request #${request.id}: ${error instanceof ApiError ? error.message : "We couldn’t confirm the status update."} ${deliveries ? "The latest deliveries are shown below." : "Refresh deliveries to check the saved status before trying again."}`,
		};
	}
	try {
		const deliveries = await fetchDeliveries(userId);
		const current = deliveries.find((item) => item.id === request.id);
		if (
			current &&
			current.status !== "CANCELLED" &&
			progress.indexOf(current.status) < progress.indexOf(saved.status)
		) {
			throw new ApiError(
				502,
				"The refreshed status is older than the saved update.",
			);
		}
		return {
			deliveries,
			kind: "success",
			requiresRefresh: false,
			message: `Request #${request.id} updated to ${saved.status}. The latest deliveries are shown below.`,
		};
	} catch {
		return {
			saved,
			kind: "success",
			requiresRefresh: true,
			message: `Request #${request.id} updated to ${saved.status}. We couldn’t reload the latest deliveries; the saved update is shown below. Refresh deliveries before another update.`,
		};
	}
}
