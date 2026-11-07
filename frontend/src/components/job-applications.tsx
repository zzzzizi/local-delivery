"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { chatError } from "../lib/chat";
import { type DeliveryRequest, formatOsloDate } from "../lib/create-request";
import {
	type ApplicationPage,
	type JobApplication,
	applicationLabels,
	decideApplication,
	fetchApplications,
	requestJob,
} from "../lib/job-applications";
import { fetchRequest } from "../lib/request-details";

export function JobApplications({
	userId,
	request,
	onRequestChange,
}: {
	userId: number;
	request?: DeliveryRequest;
	onRequestChange?: (request: DeliveryRequest) => void;
}) {
	const requestId = request?.id;
	const customer = request?.customerId === userId;
	const [page, setPage] = useState(0);
	const [data, setData] = useState<ApplicationPage | null>(null);
	const [pending, setPending] = useState(false);
	const [blocked, setBlocked] = useState(false);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const busy = useRef(false);
	useEffect(() => {
		const controller = new AbortController();
		setData(null);
		setBlocked(true);
		setError("");
		fetchApplications(
			userId,
			page,
			requestId,
			AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]),
		)
			.then((result) => {
				if (!controller.signal.aborted) {
					setData(result);
					setBlocked(false);
				}
			})
			.catch((failure) => {
				if (!controller.signal.aborted) setError(chatError(failure));
			});
		return () => controller.abort();
	}, [userId, requestId, page]);
	async function reload() {
		const result = await fetchApplications(userId, page, requestId);
		setData(result);
		if (requestId && onRequestChange)
			onRequestChange(await fetchRequest(String(requestId)));
		setBlocked(false);
	}
	async function act(
		action: "apply" | "approve" | "decline" | "withdraw" | "refresh",
		application?: JobApplication,
	) {
		if (busy.current || (blocked && action !== "refresh")) return;
		busy.current = true;
		setPending(true);
		setError("");
		setNotice("");
		let saved = false;
		try {
			if (action === "apply" && requestId) {
				const result = await requestJob(requestId, userId);
				setData({ items: [result], hasMore: false });
				saved = true;
				setNotice(
					result.status === "PENDING"
						? "Request sent. Wait for the customer to approve you before starting the job."
						: "The customer has approved your request.",
				);
			} else if (application && action !== "refresh" && action !== "apply") {
				const result = await decideApplication(application, action);
				if (action === "approve" && onRequestChange)
					onRequestChange(result as DeliveryRequest);
				saved = true;
				setNotice(
					action === "approve"
						? result.status === "ACCEPTED"
							? `${application.helperName} is approved. The job is now assigned to them.`
							: `This approval was already saved. The current job status is ${result.status}.`
						: action === "decline"
							? "Helper request declined. Your job is still open."
							: "Your helper request has been withdrawn.",
				);
			}
			await reload();
			if (action === "refresh") setNotice("Helper requests refreshed.");
		} catch (failure) {
			setBlocked(true);
			setError(
				`${saved ? "Your change was saved, but refreshing failed. " : ""}${chatError(failure)} Refresh helper requests to check the latest result before another action.`,
			);
		} finally {
			busy.current = false;
			setPending(false);
		}
	}
	const own = data?.items.find((row) => row.helperId === userId);
	const canApply =
		request?.status === "OPEN" &&
		request.helperId === null &&
		!customer &&
		(!own || own.status === "WITHDRAWN");
	return (
		<section
			className="application-panel"
			aria-label={customer ? "Review helper requests" : "Your helper requests"}
			aria-busy={pending}
		>
			<div className="jobs-toolbar">
				<div>
					<h2>
						{customer
							? "Review helper requests"
							: request
								? "Request this job"
								: "Your helper requests"}
					</h2>
					<p>
						{customer
							? "Choose a helper to confirm the job. Until you approve someone, the job stays open."
							: "The customer must approve you before you can start pickup or delivery."}
					</p>
				</div>
				<button
					className="button secondary"
					type="button"
					disabled={pending}
					onClick={() => act("refresh")}
				>
					Refresh helper requests
				</button>
			</div>
			{error && (
				<p role="alert" className="error-notice">
					{error}
				</p>
			)}
			{notice && (
				<p role="status" className="success-notice">
					{notice}
				</p>
			)}
			{!data && !error && <p role="status">Loading helper requests…</p>}
			{canApply && (
				<button
					className="button"
					type="button"
					disabled={pending || blocked || !data}
					onClick={() => act("apply")}
				>
					Request to help
				</button>
			)}
			{data?.items.length === 0 && (
				<p>
					{customer
						? "No helpers have requested this job yet. Interested helpers can still message you."
						: request
							? "You haven’t requested this job yet."
							: "You haven’t requested any jobs yet. Browse Available Jobs to get started."}
				</p>
			)}
			<ul className="application-list">
				{data?.items.map((row) => (
					<li key={row.id} className="application-card">
						<div>
							<h3>{customer ? row.helperName : row.requestTitle}</h3>
							<p className="application-status" data-status={row.status}>
								{applicationLabels[row.status]}
							</p>
							<p className="chat-hint">
								Request #{row.requestId} · Updated{" "}
								{formatOsloDate(row.updatedAt)}
							</p>
						</div>
						<div className="request-actions">
							{row.status === "PENDING" &&
								row.requestStatus === "OPEN" &&
								(!request || request.status === "OPEN") &&
								(customer ? (
									<>
										<button
											className="button"
											type="button"
											disabled={pending || blocked}
											onClick={() => act("approve", row)}
											aria-label={`Approve ${row.helperName}`}
										>
											Approve helper
										</button>
										<button
											className="button secondary"
											type="button"
											disabled={pending || blocked}
											onClick={() => act("decline", row)}
											aria-label={`Decline ${row.helperName}`}
										>
											Decline
										</button>
									</>
								) : (
									<button
										className="button secondary"
										type="button"
										disabled={pending || blocked}
										onClick={() => act("withdraw", row)}
									>
										Withdraw request
									</button>
								))}
							{!request &&
								(row.requestStatus === "OPEN" || row.status === "APPROVED") && (
									<Link
										className="text-link"
										href={`/requests/${row.requestId}`}
									>
										View job
									</Link>
								)}
							{customer && (
								<Link
									className="text-link"
									href={`/messages?requestId=${row.requestId}`}
								>
									View conversations
								</Link>
							)}
							{!customer && row.status === "APPROVED" && request && (
								<Link className="text-link" href="/my-deliveries">
									Go to My Deliveries
								</Link>
							)}
						</div>
					</li>
				))}
			</ul>
			{data && (page > 0 || data.hasMore) && (
				<div className="request-actions">
					<button
						type="button"
						className="button secondary"
						disabled={pending || page === 0}
						onClick={() => setPage((value) => value - 1)}
					>
						Previous helpers
					</button>
					<span>Page {page + 1}</span>
					<button
						type="button"
						className="button secondary"
						disabled={pending || !data.hasMore}
						onClick={() => setPage((value) => value + 1)}
					>
						Next helpers
					</button>
				</div>
			)}
		</section>
	);
}
