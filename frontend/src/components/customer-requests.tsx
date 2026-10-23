import { StatusBadge } from "./status-badge";
import Link from "next/link";
import { connection } from "next/server";
import { ApiError, apiRequest } from "../lib/api";
import {
	categoryLabels,
	type DeliveryRequest,
	formatOsloDate,
} from "../lib/create-request";
import { isCustomerRequests } from "../lib/customer-requests";
import { RefreshRequestsButton } from "./refresh-requests-button";

export async function CustomerRequests() {
	await connection();
	let requests: DeliveryRequest[];
	try {
		const response = await apiRequest<unknown>("/users/1/requests");
		if (!isCustomerRequests(response)) {
			throw new ApiError(
				502,
				"The server returned invalid request data. Please try again.",
			);
		}
		requests = response;
	} catch (error) {
		return (
			<div className="error-notice jobs-error" role="alert">
				<h2>We couldn’t load your requests</h2>
				<p>
					{error instanceof ApiError
						? error.message
						: "Unable to reach the delivery service. Please try again."}
				</p>
				<RefreshRequestsButton retry />
			</div>
		);
	}
	return (
		<section aria-label="Your requests">
			<div className="jobs-toolbar">
				<p className="jobs-count" role="status">
					{requests.length} {requests.length === 1 ? "request" : "requests"}
					<span>All statuses · Newest first</span>
				</p>
				<RefreshRequestsButton />
			</div>
			{requests.length === 0 ? (
				<div className="jobs-feedback">
					<h2>No requests yet</h2>
					<p>Your errands will appear here after you create a request.</p>
					<Link className="text-link" href="/requests/new">
						Create a request <span aria-hidden="true">↗</span>
					</Link>
				</div>
			) : (
				<ul className="jobs-grid">
					{requests.map((request) => (
						<li key={request.id}>
							<Link
								href={`/requests/${request.id}`}
								prefetch={false}
								className="job-card"
								aria-label={`View request: ${request.title}`}
							>
								<div className="request-card-heading">
									<p className="eyebrow">{categoryLabels[request.category]}</p>
									<StatusBadge status={request.status} />
								</div>
								<h2>{request.title}</h2>
								<dl className="job-details">
									<div>
										<dt>Helper</dt>
										<dd>
											{request.helperId === null
												? "No helper assigned"
												: `Helper #${request.helperId}`}
										</dd>
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
								<span className="job-card-action">
									View request <span aria-hidden="true">↗</span>
								</span>
							</Link>
						</li>
					))}
				</ul>
			)}
		</section>
	);
}
