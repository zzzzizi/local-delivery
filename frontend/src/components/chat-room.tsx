"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api";
import {
	type ChatMessage,
	type Conversation,
	chatError,
	fetchConversation,
	fetchMessages,
	mergeMessages,
	sendMessage,
} from "../lib/chat";
import { formatOsloDate } from "../lib/create-request";
import { StatusBadge } from "./status-badge";

export function ChatRoom({ id, userId }: { id: number; userId: number }) {
	const [conversation, setConversation] = useState<Conversation | null>(null);
	const [messages, setMessages] = useState<ChatMessage[]>([]);
	const [hasOlder, setHasOlder] = useState(false);
	const [loadingOlder, setLoadingOlder] = useState(false);
	const [draft, setDraft] = useState("");
	const [sending, setSending] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [sendError, setSendError] = useState("");
	const [sent, setSent] = useState(false);
	const [revision, setRevision] = useState(0);
	const retry = useRef<{ body: string; key: string } | null>(null);
	const busy = useRef(false);
	const olderBusy = useRef(false);
	const history = useRef<HTMLDivElement>(null);
	const follow = useRef(true);
	const olderAnchor = useRef<number | null>(null);
	const latest = useRef(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: revision deliberately restarts polling after a manual refresh or send error.
	useEffect(() => {
		const controller = new AbortController();
		let timer: ReturnType<typeof setTimeout>;
		let initialized = false;
		async function refresh() {
			if (controller.signal.aborted) return;
			let catchUp = false;
			try {
				const signal = AbortSignal.any([
					controller.signal,
					AbortSignal.timeout(10000),
				]);
				const current = await fetchConversation(id, userId, signal);
				const page = await fetchMessages(
					current,
					latest.current
						? { after: latest.current }
						: initialized
							? { after: 0 }
							: undefined,
					signal,
				);
				if (controller.signal.aborted) return;
				setConversation(current);
				setMessages((rows) => mergeMessages(rows, page.items));
				if (!initialized && !latest.current) setHasOlder(page.hasMore);
				else catchUp = page.hasMore;
				initialized = true;
				if (page.items.length)
					latest.current = page.items[page.items.length - 1].id;
				setLoadError("");
			} catch (failure) {
				if (controller.signal.aborted) return;
				setLoadError(chatError(failure));
				if (
					failure instanceof ApiError &&
					[401, 404].includes(failure.status)
				) {
					setConversation(null);
					setMessages([]);
					return;
				}
			}
			timer = setTimeout(tick, catchUp ? 0 : 5000);
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
	}, [id, userId, revision]);
	useEffect(() => {
		const pane = history.current;
		if (!pane || messages.length === 0) return;
		if (olderAnchor.current !== null) {
			pane.scrollTop += pane.scrollHeight - olderAnchor.current;
			olderAnchor.current = null;
		} else if (follow.current) pane.scrollTop = pane.scrollHeight;
	}, [messages]);
	async function older() {
		if (!conversation || !messages.length || olderBusy.current) return;
		olderBusy.current = true;
		setLoadingOlder(true);
		try {
			const page = await fetchMessages(conversation, {
				before: messages[0].id,
			});
			olderAnchor.current = history.current?.scrollHeight ?? null;
			setMessages((rows) => mergeMessages(rows, page.items));
			setHasOlder(page.hasMore);
			setLoadError("");
		} catch (failure) {
			setLoadError(chatError(failure));
		} finally {
			olderBusy.current = false;
			setLoadingOlder(false);
		}
	}
	async function send(event: FormEvent) {
		event.preventDefault();
		if (!conversation?.canSend || busy.current || !draft.trim()) return;
		busy.current = true;
		setSending(true);
		setSendError("");
		setSent(false);
		const body = draft.trim();
		if (!retry.current || retry.current.body !== body)
			retry.current = { body, key: crypto.randomUUID() };
		try {
			const message = await sendMessage(
				conversation,
				userId,
				body,
				retry.current.key,
			);
			follow.current = true;
			setMessages((rows) => mergeMessages(rows, [message]));
			setDraft("");
			retry.current = null;
			setSent(true);
		} catch (failure) {
			setSendError(chatError(failure));
			setRevision((value) => value + 1);
		} finally {
			busy.current = false;
			setSending(false);
		}
	}
	return (
		<>
			<Link className="text-link" href="/messages">
				← All conversations
			</Link>
			{loadError && (
				<p className="error-notice" role="alert">
					{loadError} <Link href="/login">Log in</Link>
				</p>
			)}
			<button
				type="button"
				className="button secondary chat-refresh"
				onClick={() => setRevision((value) => value + 1)}
			>
				Refresh messages
			</button>
			{!conversation && !loadError && (
				<p role="status">Loading conversation…</p>
			)}
			{conversation && (
				<section className="chat-room" aria-label="Private conversation">
					<header className="chat-heading">
						<div>
							<p className="eyebrow">Request #{conversation.requestId}</p>
							<h2>
								{conversation.customerId === userId
									? conversation.helperName
									: conversation.customerName}
							</h2>
							<p>{conversation.requestTitle}</p>
							{conversation.canViewRequest && (
								<Link href={`/requests/${conversation.requestId}`}>
									View job details →
								</Link>
							)}
						</div>
						<StatusBadge status={conversation.status} />
					</header>
					<p className="chat-hint">
						Only you and{" "}
						{conversation.customerId === userId
							? conversation.helperName
							: conversation.customerName}{" "}
						can read this conversation. Messages update every few seconds.
					</p>
					{hasOlder && (
						<button
							type="button"
							className="button secondary"
							disabled={loadingOlder}
							onClick={older}
						>
							{loadingOlder ? "Loading…" : "Load older messages"}
						</button>
					)}
					<div
						className="chat-history"
						ref={history}
						onScroll={() => {
							const pane = history.current;
							if (pane)
								follow.current =
									pane.scrollHeight - pane.scrollTop - pane.clientHeight < 80;
						}}
					>
						{messages.length === 0 && (
							<p className="chat-empty">
								Start with a question about the pickup, timing, or delivery.
							</p>
						)}
						<ol aria-label="Messages" className="message-list">
							{messages.map((message) => (
								<li
									key={message.id}
									className={
										message.senderId === userId ? "message own" : "message"
									}
								>
									<p className="message-author">
										{message.senderId === userId
											? "You"
											: message.senderId === conversation.customerId
												? conversation.customerName
												: conversation.helperName}
									</p>
									<p className="message-body">{message.body}</p>
									<time dateTime={message.createdAt}>
										{formatOsloDate(message.createdAt)}
									</time>
								</li>
							))}
						</ol>
					</div>
					<p className="chat-hint" role="status">
						{messages.length} messages loaded{sent ? " · Message sent." : ""}
					</p>
					{conversation.canSend ? (
						<form className="chat-composer" onSubmit={send}>
							<label htmlFor="message">Your message</label>
							<textarea
								id="message"
								value={draft}
								onChange={(event) => {
									setDraft(event.target.value);
									setSent(false);
								}}
								maxLength={2000}
								rows={3}
								required
								disabled={sending}
								aria-describedby="message-length"
							/>
							<div className="request-actions">
								<span id="message-length">{draft.length}/2000</span>
								<button
									type="submit"
									className="button"
									disabled={sending || !draft.trim()}
								>
									{sending ? "Sending…" : "Send message"}
								</button>
							</div>
						</form>
					) : (
						<p className="chat-closed">
							This conversation is read-only because the job has ended or
							another helper was selected. Your message history is still
							available.
						</p>
					)}
					{sendError && (
						<p className="error-notice" role="alert">
							{sendError} Your draft has been kept.
						</p>
					)}
				</section>
			)}
		</>
	);
}
