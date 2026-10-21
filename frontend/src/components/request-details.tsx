"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api";
import {
	categoryLabels,
	type DeliveryRequest,
	formatOsloDate,
} from "../lib/create-request";
import { acceptJob, fetchRequest } from "../lib/request-details";

export function RequestDetails({
	initialRequest,
}: {
	initialRequest: DeliveryRequest;
}) {
	const [request, setRequest] = useState(initialRequest);
	const [pending, setPending] = useState<"accept" | "refresh" | null>(null);
	const [blocked, setBlocked] = useState(false);
	const [notice, setNotice] = useState<{
		kind: "success" | "error";
		message: string;
	} | null>(null);
	const busy = useRef(false);
	const feedback = useRef<HTMLDivElement>(null);
	useEffect(() => {
		if (notice) feedback.current?.focus();
	}, [notice]);

	async function act(action: "accept" | "refresh") {
		if (
			busy.current ||
			(action === "accept" && (request.status !== "OPEN" || blocked))
		)
			return;
		busy.current = true;
		setPending(action);
		setNotice(null);
		try {
			if (action === "accept") {
				const result = await acceptJob(String(request.id));
				if (result.request) setRequest(result.request);
				setBlocked(result.requiresRefresh);
				setNotice({ kind: result.kind, message: result.message });
			} else {
				setRequest(await fetchRequest(String(request.id)));
				setBlocked(false);
				setNotice({ kind: "success", message: "Request details refreshed." });
			}
		} catch (error) {
			if (
				action === "refresh" ||
				(error instanceof ApiError && error.status === 404)
			)
				setBlocked(true);
			setNotice({
				kind: "error",
				message:
					error instanceof ApiError
						? error.message
						: "Unable to reach the delivery service. Please refresh the request.",
			});
		} finally {
			busy.current = false;
			setPending(null);
		}
	}

	return (
		<div className="request-detail" aria-busy={pending !== null}>
			{notice && (
				<div
					ref={feedback}
					tabIndex={-1}
					role={notice.kind === "error" ? "alert" : "status"}
					className={
						notice.kind === "error" ? "error-notice" : "success-notice"
					}
				>
					<p>{notice.message}</p>
				</div>
			)}
			<article className="request-card" aria-label="Request details">
				<div className="request-card-heading">
					<p className="eyebrow">
						Request #{request.id} · {categoryLabels[request.category]}
					</p>
					<span className="status-badge">{request.status}</span>
				</div>
				<h2>{request.title}</h2>
				<p className="request-description">{request.description}</p>
				<dl className="request-details">
					<div>
						<dt>Pickup address</dt>
						<dd>{request.pickupAddress}</dd>
					</div>
					<div>
						<dt>Delivery address</dt>
						<dd>{request.deliveryAddress}</dd>
					</div>
					<div>
						<dt>Shopping budget</dt>
						<dd>
							{request.shoppingBudget === null
								? "Not specified"
								: `${request.shoppingBudget} NOK`}
						</dd>
					</div>
					<div>
						<dt>Helper reward</dt>
						<dd>{request.helperReward} NOK</dd>
					</div>
					<div>
						<dt>Deadline (Oslo time)</dt>
						<dd>
							<time dateTime={request.deadline}>
								{formatOsloDate(request.deadline)}
							</time>
						</dd>
					</div>
					<div>
						<dt>Customer</dt>
						<dd>Customer #{request.customerId}</dd>
					</div>
					<div>
						<dt>Helper</dt>
						<dd>
							{request.helperId === null
								? "No helper assigned"
								: `Helper #${request.helperId}${request.helperId === 2 ? " (you)" : ""}`}
						</dd>
					</div>
					<div>
						<dt>Posted (Oslo time)</dt>
						<dd>{formatOsloDate(request.createdAt)}</dd>
					</div>
					<div>
						<dt>Last updated (Oslo time)</dt>
						<dd>{formatOsloDate(request.updatedAt)}</dd>
					</div>
				</dl>
			</article>
			<div className="request-actions">
				{request.status === "OPEN" && (
					<button
						type="button"
						className="button"
						disabled={pending !== null || blocked}
						onClick={() => act("accept")}
					>
						{pending === "accept" ? "Accepting job…" : "Accept Job"}
					</button>
				)}
				<button
					type="button"
					className="button secondary"
					disabled={pending !== null}
					onClick={() => act("refresh")}
				>
					{pending === "refresh" ? "Refreshing…" : "Refresh request"}
				</button>
			</div>
			<p className="submission-status" role="status">
				{pending === "accept"
					? "Accepting the job and checking its latest status…"
					: pending === "refresh"
						? "Loading the latest request details…"
						: request.status !== "OPEN"
							? "This request is no longer open for acceptance."
							: ""}
			</p>
			<Link className="text-link" href="/jobs" prefetch={false}>
				Back to available jobs <span aria-hidden="true">↗</span>
			</Link>
		</div>
	);
}
