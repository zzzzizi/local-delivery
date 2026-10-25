"use client";

import { StatusBadge } from "./status-badge";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api";
import {
	categoryLabels,
	type DeliveryRequest,
	formatOsloDate,
} from "../lib/create-request";
import {
	advanceDelivery,
	deliveryAction,
	fetchDeliveries,
} from "../lib/helper-deliveries";

export function DeliveryBoard({
	initialDeliveries,
	userId,
}: {
	initialDeliveries: DeliveryRequest[];
	userId: number;
}) {
	const [deliveries, setDeliveries] = useState(initialDeliveries);
	const [pending, setPending] = useState<number | "refresh" | null>(null);
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

	async function act(target: number | "refresh") {
		if (busy.current || (target !== "refresh" && blocked)) return;
		const request = deliveries.find((item) => item.id === target);
		if (target !== "refresh" && (!request || !deliveryAction(request, userId)))
			return;
		busy.current = true;
		setPending(target);
		setNotice(null);
		try {
			if (target === "refresh") {
				setDeliveries(await fetchDeliveries(userId));
				setBlocked(false);
				setNotice({ kind: "success", message: "Delivery details refreshed." });
			} else if (request) {
				const result = await advanceDelivery(request, userId);
				if (result.deliveries) setDeliveries(result.deliveries);
				else if (result.saved) {
					const saved = result.saved;
					setDeliveries((items) =>
						items.map((item) => (item.id === saved.id ? saved : item)),
					);
				}
				setBlocked(result.requiresRefresh);
				setNotice({ kind: result.kind, message: result.message });
			}
		} catch (error) {
			setBlocked(true);
			setNotice({
				kind: "error",
				message: `${error instanceof ApiError ? error.message : "Unable to reach the delivery service."} Refresh deliveries before updating a status.`,
			});
		} finally {
			busy.current = false;
			setPending(null);
		}
	}

	return (
		<section aria-label="Your deliveries" aria-busy={pending !== null}>
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
			<div className="jobs-toolbar">
				<p className="jobs-count">
					{deliveries.length}{" "}
					{deliveries.length === 1 ? "delivery" : "deliveries"}
					<span>All assigned jobs · Newest first</span>
				</p>
				<button
					type="button"
					className="button secondary"
					disabled={pending !== null}
					onClick={() => act("refresh")}
				>
					{pending === "refresh" ? "Refreshing…" : "Refresh deliveries"}
				</button>
			</div>
			<p className="submission-status" role="status">
				{pending === "refresh"
					? "Loading the latest deliveries…"
					: pending !== null
						? `Updating request #${pending} and checking its latest status…`
						: blocked
							? "Refresh deliveries to enable status updates."
							: ""}
			</p>
			{deliveries.length === 0 ? (
				<div className="jobs-feedback">
					<h2>No deliveries yet</h2>
					<p>Accept an available job to start helping a neighbour.</p>
					<Link className="text-link" href="/jobs" prefetch={false}>
						Find available jobs <span aria-hidden="true">↗</span>
					</Link>
				</div>
			) : (
				<ul className="jobs-grid">
					{deliveries.map((request) => {
						const action = deliveryAction(request, userId);
						return (
							<li key={request.id}>
								<article
									className="request-card delivery-card"
									data-request-id={request.id}
									aria-labelledby={`delivery-${request.id}-title`}
								>
									<div className="request-card-heading">
										<p className="eyebrow">
											{categoryLabels[request.category]}
										</p>
										<StatusBadge status={request.status} />
									</div>
									<h2 id={`delivery-${request.id}-title`}>{request.title}</h2>
									<p className="request-description">{request.description}</p>
									<dl className="job-details">
										<div className="job-address">
											<dt>Pickup</dt>
											<dd>{request.pickupAddress}</dd>
										</div>
										<div className="job-address">
											<dt>Delivery</dt>
											<dd>{request.deliveryAddress}</dd>
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
									</dl>
									{!action && (
										<p>
											{request.status === "DELIVERED"
												? "Delivery completed."
												: request.status === "CANCELLED"
													? "This delivery was cancelled."
													: "No status action is available for this request."}
										</p>
									)}
									<div className="request-actions">
										{action && (
											<button
												type="button"
												className="button"
												disabled={pending !== null || blocked}
												onClick={() => act(request.id)}
											>
												{pending === request.id ? "Updating…" : action.label}
											</button>
										)}
										<Link
											className="text-link"
											href={`/requests/${request.id}`}
											prefetch={false}
										>
											View request <span aria-hidden="true">↗</span>
										</Link>
									</div>
								</article>
							</li>
						);
					})}
				</ul>
			)}
		</section>
	);
}
