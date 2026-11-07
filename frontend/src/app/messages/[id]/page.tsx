import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Suspense } from "react";
import { ChatRoom } from "../../../components/chat-room";
import { requireUser } from "../../../lib/server-api";

export const metadata: Metadata = { title: "Conversation" };
async function Room({ params }: { params: Promise<{ id: string }> }) {
	await connection();
	const user = await requireUser();
	const { id } = await params;
	if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) notFound();
	return <ChatRoom key={id} id={Number(id)} userId={user.id} />;
}
export default function ConversationPage({
	params,
}: {
	params: Promise<{ id: string }>;
}) {
	return (
		<>
			<section className="page-intro">
				<h1>Conversation</h1>
			</section>
			<Suspense fallback={<p>Loading conversation…</p>}>
				<Room params={params} />
			</Suspense>
		</>
	);
}
