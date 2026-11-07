"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { chatError, startConversation } from "../lib/chat";
import type { DeliveryRequest } from "../lib/create-request";

export function RequestChatLink({
	request,
	userId,
}: {
	request: DeliveryRequest;
	userId: number;
}) {
	const router = useRouter();
	const busy = useRef(false);
	const [pending, setPending] = useState(false);
	const [error, setError] = useState("");
	async function open() {
		if (busy.current) return;
		busy.current = true;
		setPending(true);
		setError("");
		try {
			const conversation = await startConversation(request.id, userId);
			router.push(`/messages/${conversation.id}`);
		} catch (failure) {
			setError(chatError(failure));
		} finally {
			busy.current = false;
			setPending(false);
		}
	}
	return (
		<section className="chat-entry" aria-label="Request conversations">
			{request.customerId === userId ? (
				<>
					<h2>Talk with interested helpers</h2>
					<p>
						Each helper has a private conversation with you about this request.
					</p>
					<Link
						className="button secondary"
						href={`/messages?requestId=${request.id}`}
					>
						View conversations
					</Link>
				</>
			) : (
				<>
					<h2>Have a question about this job?</h2>
					<p>
						Ask the customer before requesting the job, or coordinate your delivery here.
					</p>
					<button
						type="button"
						className="button secondary"
						onClick={open}
						disabled={pending}
					>
						{pending ? "Opening chat…" : "Message customer"}
					</button>
				</>
			)}
			{error && (
				<p className="error-notice" role="alert">
					{error}
				</p>
			)}
		</section>
	);
}
