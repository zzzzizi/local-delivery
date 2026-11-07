import { ApiError, apiRequest } from "./api.ts";
import type { DeliveryRequest } from "./create-request.ts";
import { requestStatuses } from "./create-request.ts";
import {
	isDeliveryRequest,
	isOffsetDate,
	isPositiveId,
} from "./request-details.ts";

export const applicationLabels = {
	PENDING: "Awaiting customer approval",
	APPROVED: "Approved by customer",
	DECLINED: "Declined by customer",
	WITHDRAWN: "Withdrawn",
	NOT_SELECTED: "Another helper was chosen",
	CANCELLED: "Job cancelled",
} as const;
export type JobApplication = {
	id: number;
	requestId: number;
	requestTitle: string;
	requestStatus: DeliveryRequest["status"];
	customerId: number;
	helperId: number;
	helperName: string;
	status: keyof typeof applicationLabels;
	createdAt: string;
	updatedAt: string;
};
export type ApplicationPage = { items: JobApplication[]; hasMore: boolean };

export function isApplication(value: unknown): value is JobApplication {
	if (!value || typeof value !== "object") return false;
	const row = value as Record<string, unknown>;
	return (
		[row.id, row.requestId, row.customerId, row.helperId].every(isPositiveId) &&
		typeof row.requestTitle === "string" &&
		typeof row.helperName === "string" &&
		typeof row.status === "string" &&
		Object.hasOwn(applicationLabels, row.status) &&
		(requestStatuses as readonly unknown[]).includes(row.requestStatus) &&
		isOffsetDate(row.createdAt) &&
		isOffsetDate(row.updatedAt)
	);
}
function invalid(): never {
	throw new ApiError(
		502,
		"Unable to read helper requests. Refresh and try again.",
	);
}
export async function fetchApplications(
	userId: number,
	page: number,
	requestId?: number,
	signal?: AbortSignal,
): Promise<ApplicationPage> {
	const path = requestId
		? `/requests/${requestId}/applications`
		: "/me/applications";
	const data = await apiRequest<unknown>(`${path}?page=${page}`, { signal });
	if (!data || typeof data !== "object") return invalid();
	const result = data as Record<string, unknown>;
	if (
		!Array.isArray(result.items) ||
		result.items.length > 25 ||
		typeof result.hasMore !== "boolean"
	)
		return invalid();
	const ids = new Set<number>();
	for (const row of result.items) {
		if (
			!isApplication(row) ||
			ids.has(row.id) ||
			(requestId
				? row.requestId !== requestId ||
					![row.helperId, row.customerId].includes(userId)
				: row.helperId !== userId)
		)
			return invalid();
		ids.add(row.id);
	}
	return result as ApplicationPage;
}
export async function requestJob(
	requestId: number,
	userId: number,
): Promise<JobApplication> {
	const row = await apiRequest<unknown>(`/requests/${requestId}/applications`, {
		method: "POST",
		body: {},
	});
	if (
		!isApplication(row) ||
		row.requestId !== requestId ||
		row.helperId !== userId ||
		!["PENDING", "APPROVED"].includes(row.status)
	)
		return invalid();
	return row;
}
export async function decideApplication(
	application: JobApplication,
	action: "approve" | "decline" | "withdraw",
): Promise<DeliveryRequest | JobApplication> {
	const row = await apiRequest<unknown>(
		`/requests/${application.requestId}/applications/${application.id}/${action}`,
		{ method: "POST", body: {} },
	);
	if (action === "approve") {
		if (
			!isDeliveryRequest(row) ||
			row.id !== application.requestId ||
			row.customerId !== application.customerId ||
			row.helperId !== application.helperId ||
			row.status === "OPEN"
		)
			return invalid();
	} else if (
		!isApplication(row) ||
		row.id !== application.id ||
		row.requestId !== application.requestId ||
		row.helperId !== application.helperId ||
		row.status !== (action === "decline" ? "DECLINED" : "WITHDRAWN")
	)
		return invalid();
	return row;
}
