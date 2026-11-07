"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError } from "../lib/api";
import {
	type ChatPage,
	type Conversation,
	chatError,
	fetchConversations,
} from "../lib/chat";
import { formatOsloDate } from "../lib/create-request";
import { StatusBadge } from "./status-badge";

export function ConversationList({
	userId,
	requestId,
}: {
	userId: number;
	requestId?: number;
}) {
	const [page, setPage] = useState(0);
	const [data, setData] = useState<ChatPage<Conversation> | null>(null);
	const [error, setError] = useState("");
	const [revision, setRevision] = useState(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: revision deliberately restarts polling after a manual refresh or send error.
	useEffect(() => {
		const controller = new AbortController();
		let timer: ReturnType<typeof setTimeout>;
		setData(null);
		async function refresh() {
			if (controller.signal.aborted) return;
			try {
				const result = await fetchConversations(
					userId,
					page,
					requestId,
					AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]),
				);
				if (controller.signal.aborted) return;
				setData(result);
				setError("");
			} catch (failure) {
				if (controller.signal.aborted) return;
				setError(chatError(failure));
				if (failure instanceof ApiError && failure.status === 401) {
					setData(null);
					return;
				}
			}
			timer = setTimeout(tick, 5000);
		}
		function tick() {
			if (document.visibilityState === "visible") void refresh();
			else timer = setTimeout(tick, 5000);
		}
		void refresh();
		return () => {
			controller.abort();
			clearTimeout(timer);
		};
	}, [userId, requestId, page, revision]);
	return (
		<>
			<div className="jobs-toolbar">
				<p>
					{requestId
						? `Conversations for request #${requestId}`
						: "Your private conversations · Newest conversations first"}
				</p>
				<button
					className="button secondary"
					type="button"
					onClick={() => setRevision((value) => value + 1)}
				>
					Refresh conversations
				</button>
			</div>
			{requestId && (
				<p>
					<Link href="/messages">Show all conversations</Link>
				</p>
			)}
			{error && (
				<p role="alert" className="error-notice">
					{error} <Link href="/login">Log in</Link>
				</p>
			)}
			{!data && !error && <p role="status">Loading conversations…</p>}
			{data?.items.length === 0 && (
				<div className="jobs-feedback">
					<h2>No conversations yet</h2>
					<p>
						Helpers can use “Message customer” on a job to ask a question.
						Customers will see those conversations here.
					</p>
					<Link className="text-link" href="/jobs">
						Browse available jobs →
					</Link>
				</div>
			)}
			<ul className="conversation-list">
				{data?.items.map((row) => (
					<li key={row.id}>
						<Link className="conversation-card" href={`/messages/${row.id}`}>
							<div>
								<p className="eyebrow">
									Request #{row.requestId} ·{" "}
									{row.customerId === userId ? "Helper" : "Customer"}
								</p>
								<h2>
									{row.customerId === userId
										? row.helperName
										: row.customerName}
								</h2>
								<p>{row.requestTitle}</p>
							</div>
							<div className="conversation-meta">
								<StatusBadge status={row.status} />
								<p>{row.canSend ? "Open conversation" : "Read-only history"}</p>
								<time dateTime={row.updatedAt}>
									{formatOsloDate(row.updatedAt)}
								</time>
							</div>
						</Link>
					</li>
				))}
			</ul>
			{data && (page > 0 || data.hasMore) && (
				<div className="request-actions">
					<button
						className="button secondary"
						type="button"
						disabled={page === 0}
						onClick={() => setPage((value) => value - 1)}
					>
						Previous
					</button>
					<span>Page {page + 1}</span>
					<button
						className="button secondary"
						type="button"
						disabled={!data.hasMore}
						onClick={() => setPage((value) => value + 1)}
					>
						Next
					</button>
				</div>
			)}
		</>
	);
}
