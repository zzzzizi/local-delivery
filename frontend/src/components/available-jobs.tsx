import { LoadingState } from "./loading-state";
import { StatusBadge } from "./status-badge";
import Link from "next/link";
import { connection } from "next/server";
import { ApiError, apiRequest } from "../lib/api";
import { type AvailableJob, isAvailableJobs } from "../lib/available-jobs";
import { categoryLabels, formatOsloDate } from "../lib/create-request";
import { RefreshJobsButton } from "./refresh-jobs-button";

export function JobsLoading() {
	return <LoadingState message="Loading available jobs…" />;
}

export async function AvailableJobs() {
	// Fetch per visit, outside prerendering, even when the API is unavailable at build time.
	await connection();
	let jobs: AvailableJob[];
	try {
		const response = await apiRequest<unknown>("/requests");
		if (!isAvailableJobs(response)) {
			throw new ApiError(
				502,
				"The server returned an invalid jobs response. Please try again.",
			);
		}
		jobs = response;
	} catch (error) {
		return (
			<div className="error-notice jobs-error" role="alert">
				<h2>We couldn’t load available jobs</h2>
				<p>
					{error instanceof ApiError
						? error.message
						: "Unable to reach the delivery service. Please try again."}
				</p>
				<RefreshJobsButton retry />
			</div>
		);
	}

	return (
		<section aria-label="Available requests">
			<div className="jobs-toolbar">
				<p className="jobs-count" role="status">
					{jobs.length} open {jobs.length === 1 ? "request" : "requests"}
					<span>Newest first</span>
				</p>
				<RefreshJobsButton />
			</div>
			{jobs.length === 0 ? (
				<div className="jobs-feedback">
					<h2>No jobs available yet</h2>
					<p>New requests will appear here when neighbours need a hand.</p>
					<Link className="text-link" href="/requests/new">
						Create a request <span aria-hidden="true">↗</span>
					</Link>
				</div>
			) : (
				<ul className="jobs-grid">
					{jobs.map((job) => (
						<li key={job.id}>
							<Link
								href={`/requests/${job.id}`}
								prefetch={false}
								className="job-card"
								aria-label={`View request: ${job.title}`}
							>
								<div className="request-card-heading">
									<p className="eyebrow">{categoryLabels[job.category]}</p>
									<StatusBadge status={job.status} />
								</div>
								<h2>{job.title}</h2>
								<dl className="job-details">
									<div className="job-address">
										<dt>Pickup</dt>
										<dd>{job.pickupAddress}</dd>
									</div>
									<div className="job-address">
										<dt>Delivery</dt>
										<dd>{job.deliveryAddress}</dd>
									</div>
									<div>
										<dt>Helper reward</dt>
										<dd className="job-reward">{job.helperReward} NOK</dd>
									</div>
									<div>
										<dt>Deadline (Oslo time)</dt>
										<dd>
											<time dateTime={job.deadline}>
												{formatOsloDate(job.deadline)}
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
