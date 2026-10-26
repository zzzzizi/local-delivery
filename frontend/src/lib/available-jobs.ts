import type { DeliveryRequest } from "./create-request";
import {
	isCategory,
	isMoney,
	isOffsetDate,
	isPositiveId,
} from "./request-details.ts";

export type AvailableJob = Pick<
	DeliveryRequest,
	| "id"
	| "category"
	| "title"
	| "pickupAddress"
	| "deliveryAddress"
	| "helperReward"
	| "deadline"
> & { status: "OPEN" };

/** Reject invalid list responses instead of rendering broken cards or unsafe IDs. */
export function isAvailableJobs(value: unknown): value is AvailableJob[] {
	if (!Array.isArray(value)) return false;
	const ids = new Set<number>();
	return value.every((job) => {
		if (!job || typeof job !== "object") return false;
		if (!isPositiveId(job.id) || ids.has(job.id)) return false;
		ids.add(job.id);
		return (
			isCategory(job.category) &&
			job.status === "OPEN" &&
			["title", "pickupAddress", "deliveryAddress"].every(
				(key) => typeof job[key] === "string",
			) &&
			isMoney(job.helperReward) &&
			isOffsetDate(job.deadline)
		);
	});
}
