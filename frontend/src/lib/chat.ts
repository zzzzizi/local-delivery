import { ApiError, apiRequest } from "./api.ts";
import { requestStatuses } from "./create-request.ts";
import { isOffsetDate, isPositiveId } from "./request-details.ts";

export type Conversation = {
	id: number;
	requestId: number;
	requestTitle: string;
	status: (typeof requestStatuses)[number];
	customerId: number;
	customerName: string;
	helperId: number;
	helperName: string;
	canSend: boolean;
	canViewRequest: boolean;
	updatedAt: string;
};
export type ChatMessage = {
	id: number;
	conversationId: number;
	senderId: number;
	clientMessageId: string;
	body: string;
	createdAt: string;
};
export type ChatPage<T> = { items: T[]; hasMore: boolean };

export function isConversation(value: unknown): value is Conversation {
	if (!value || typeof value !== "object") return false;
	const row = value as Record<string, unknown>;
	return (
		[row.id, row.requestId, row.customerId, row.helperId].every(isPositiveId) &&
		[row.requestTitle, row.customerName, row.helperName].every(
			(item) => typeof item === "string",
		) &&
		(requestStatuses as readonly unknown[]).includes(row.status) &&
		typeof row.canSend === "boolean" &&
		typeof row.canViewRequest === "boolean" &&
		isOffsetDate(row.updatedAt)
	);
}

function isMessage(value: unknown): value is ChatMessage {
	if (!value || typeof value !== "object") return false;
	const row = value as Record<string, unknown>;
	return (
		[row.id, row.conversationId, row.senderId].every(isPositiveId) &&
		typeof row.clientMessageId === "string" &&
		typeof row.body === "string" &&
		row.body.length > 0 &&
		row.body.length <= 2000 &&
		isOffsetDate(row.createdAt)
	);
}

function invalid(): never {
	throw new ApiError(
		502,
		"Unable to read the chat response. Please try again.",
	);
}
function page<T>(
	value: unknown,
	check: (item: unknown) => item is T,
	max: number,
): ChatPage<T> {
	if (!value || typeof value !== "object") return invalid();
	const result = value as Record<string, unknown>;
	if (
		!Array.isArray(result.items) ||
		result.items.length > max ||
		!result.items.every(check) ||
		typeof result.hasMore !== "boolean"
	)
		return invalid();
	return { items: result.items, hasMore: result.hasMore };
}

export async function startConversation(requestId: number, userId: number) {
	const row = await apiRequest<unknown>(`/requests/${requestId}/conversation`, {
		method: "POST",
	});
	if (
		!isConversation(row) ||
		row.requestId !== requestId ||
		row.helperId !== userId
	)
		return invalid();
	return row;
}
export async function fetchConversation(
	id: number,
	userId: number,
	signal?: AbortSignal,
) {
	const row = await apiRequest<unknown>(`/conversations/${id}`, { signal });
	if (
		!isConversation(row) ||
		row.id !== id ||
		![row.customerId, row.helperId].includes(userId)
	)
		return invalid();
	return row;
}
export async function fetchConversations(
	userId: number,
	pageNumber: number,
	requestId?: number,
	signal?: AbortSignal,
) {
	const query = new URLSearchParams({ page: String(pageNumber) });
	if (requestId) query.set("requestId", String(requestId));
	const result = page(
		await apiRequest<unknown>(`/conversations?${query}`, { signal }),
		isConversation,
		25,
	);
	if (
		result.items.some(
			(row) =>
				![row.customerId, row.helperId].includes(userId) ||
				(requestId && row.requestId !== requestId),
		)
	)
		return invalid();
	return result;
}
export async function fetchMessages(
	conversation: Conversation,
	cursor?: { before: number } | { after: number },
	signal?: AbortSignal,
) {
	const query = cursor
		? `?${new URLSearchParams(Object.fromEntries(Object.entries(cursor).map(([key, value]) => [key, String(value)])))}`
		: "";
	const result = page(
		await apiRequest<unknown>(
			`/conversations/${conversation.id}/messages${query}`,
			{ signal },
		),
		isMessage,
		50,
	);
	if (
		result.items.some(
			(row, index) =>
				row.conversationId !== conversation.id ||
				![conversation.customerId, conversation.helperId].includes(
					row.senderId,
				) ||
				(index > 0 && result.items[index - 1].id >= row.id) ||
				(cursor && "before" in cursor && row.id >= cursor.before) ||
				(cursor && "after" in cursor && row.id <= cursor.after),
		)
	)
		return invalid();
	return result;
}
export async function sendMessage(
	conversation: Conversation,
	userId: number,
	body: string,
	clientMessageId: string,
) {
	const row = await apiRequest<unknown>(
		`/conversations/${conversation.id}/messages`,
		{ method: "POST", body: { body, clientMessageId } },
	);
	if (
		!isMessage(row) ||
		row.conversationId !== conversation.id ||
		row.senderId !== userId ||
		row.clientMessageId !== clientMessageId ||
		row.body !== body.trim()
	)
		return invalid();
	return row;
}
export function mergeMessages(
	current: ChatMessage[],
	incoming: ChatMessage[],
): ChatMessage[] {
	return [
		...new Map([...current, ...incoming].map((row) => [row.id, row])).values(),
	].sort((a, b) => a.id - b.id);
}
export function chatError(error: unknown): string {
	return error instanceof ApiError
		? error.status === 401
			? "Your session expired. Log in to continue."
			: error.message
		: "Unable to reach Messages. Check your connection and try again.";
}
