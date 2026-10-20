import type { DeliveryRequest } from "./create-request";

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
		if (!Number.isSafeInteger(job.id) || job.id < 1 || ids.has(job.id))
			return false;
		ids.add(job.id);
		return (
			["PACKAGE", "BUY", "PICKUP"].includes(job.category) &&
			job.status === "OPEN" &&
			["title", "pickupAddress", "deliveryAddress"].every(
				(key) => typeof job[key] === "string",
			) &&
			typeof job.helperReward === "string" &&
			/^\d{1,8}\.\d{2}$/.test(job.helperReward) &&
			typeof job.deadline === "string" &&
			/T.+(?:Z|[+-]\d{2}:\d{2})$/.test(job.deadline) &&
			Number.isFinite(Date.parse(job.deadline))
		);
	});
}
