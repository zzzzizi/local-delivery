import { ApiError, apiRequest } from "./api.ts";
import type { DeliveryRequest } from "./create-request";

export function isRequestId(id: string): boolean {
	return (
		/^[1-9]\d{0,18}$/.test(id) && BigInt(id) <= BigInt("9223372036854775807")
	);
}

export function isDeliveryRequest(value: unknown): value is DeliveryRequest {
	if (!value || typeof value !== "object") return false;
	const request = value as Record<string, unknown>;
	const positiveId = (id: unknown) =>
		typeof id === "number" && Number.isSafeInteger(id) && id > 0;
	const money = (amount: unknown) =>
		typeof amount === "string" && /^\d{1,8}\.\d{2}$/.test(amount);
	const date = (time: unknown) =>
		typeof time === "string" &&
		/T.+(?:Z|[+-]\d{2}:\d{2})$/.test(time) &&
		Number.isFinite(Date.parse(time));
	return (
		positiveId(request.id) &&
		positiveId(request.customerId) &&
		(request.helperId === null || positiveId(request.helperId)) &&
		typeof request.category === "string" &&
		["PACKAGE", "BUY", "PICKUP"].includes(request.category) &&
		typeof request.status === "string" &&
		[
			"OPEN",
			"ACCEPTED",
			"PICKED_UP",
			"DELIVERING",
			"DELIVERED",
			"CANCELLED",
		].includes(request.status) &&
		["title", "description", "pickupAddress", "deliveryAddress"].every(
			(key) => typeof request[key] === "string",
		) &&
		(request.shoppingBudget === null || money(request.shoppingBudget)) &&
		money(request.helperReward) &&
		[request.deadline, request.createdAt, request.updatedAt].every(date)
	);
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

export type AcceptanceResult = {
	request?: DeliveryRequest;
	kind: "success" | "error";
	message: string;
	requiresRefresh: boolean;
};

/** Never retry a POST automatically. Reconcile conflicts or uncertain results with a GET. */
export async function acceptJob(
	id: string,
	userId: number,
): Promise<AcceptanceResult> {
	if (!isRequestId(id)) throw new ApiError(404, "Delivery request not found.");
	let accepted: DeliveryRequest;
	try {
		const data = await apiRequest<unknown>(`/requests/${id}/accept`, {
			method: "POST",
			body: {},
		});
		if (
			!isDeliveryRequest(data) ||
			String(data.id) !== id ||
			data.status !== "ACCEPTED" ||
			data.helperId !== userId
		) {
			throw new ApiError(
				502,
				"The server returned an unexpected acceptance response.",
			);
		}
		accepted = data;
	} catch (error) {
		if (
			error instanceof ApiError &&
			error.status >= 400 &&
			error.status < 500 &&
			error.status !== 409
		)
			throw error;
		let current: DeliveryRequest | undefined;
		try {
			current = await fetchRequest(id);
		} catch {
			/* Keep acceptance blocked until a manual refresh succeeds. */
		}
		if (error instanceof ApiError && error.status === 409) {
			return {
				request: current,
				kind: "error",
				requiresRefresh: !current || current.status === "OPEN",
				message: `${error.message} ${current ? "The latest request details are shown below." : "Refresh the request to check its latest status."}`,
			};
		}
		if (
			current?.helperId === userId &&
			current.status !== "OPEN" &&
			current.status !== "CANCELLED"
		) {
			return {
				request: current,
				kind: "success",
				requiresRefresh: false,
				message: "The latest details confirm that you are the assigned helper.",
			};
		}
		return {
			request: current,
			kind: "error",
			requiresRefresh: true,
			message:
				"We couldn’t confirm acceptance. Refresh the request to check its status before trying again.",
		};
	}
	try {
		const current = await fetchRequest(id);
		return {
			request: current,
			kind: "success",
			requiresRefresh: false,
			message: "Job accepted. The latest request details are shown below.",
		};
	} catch {
		return {
			request: accepted,
			kind: "success",
			requiresRefresh: false,
			message:
				"Job accepted. We couldn’t reload the latest details; the saved acceptance is shown below. Use Refresh request to check for updates.",
		};
	}
}
