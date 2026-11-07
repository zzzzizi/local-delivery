import type { Metadata } from "next";
import { connection } from "next/server";
import { Suspense } from "react";
import { ConversationList } from "../../components/conversation-list";
import { requireUser } from "../../lib/server-api";

export const metadata: Metadata = { title: "Messages" };
async function Inbox({
	searchParams,
}: {
	searchParams: Promise<{ requestId?: string }>;
}) {
	await connection();
	const user = await requireUser();
	const query = await searchParams;
	const requestId =
		query.requestId &&
		/^[1-9]\d*$/.test(query.requestId) &&
		Number.isSafeInteger(Number(query.requestId))
			? Number(query.requestId)
			: undefined;
	return <ConversationList userId={user.id} requestId={requestId} />;
}
export default function MessagesPage({
	searchParams,
}: {
	searchParams: Promise<{ requestId?: string }>;
}) {
	return (
		<>
			<section className="page-intro">
				<p className="eyebrow">Stay in touch</p>
				<h1>Messages</h1>
				<p>
					Ask a question before requesting a job and keep delivery details
					together.
				</p>
			</section>
			<Suspense fallback={<p>Loading messages…</p>}>
				<Inbox searchParams={searchParams} />
			</Suspense>
		</>
	);
}
