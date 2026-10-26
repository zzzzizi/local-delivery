"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";

export function RefreshButton({
	label,
	retry = false,
}: {
	label: string;
	retry?: boolean;
}) {
	const router = useRouter();
	const [pending, startTransition] = useTransition();

	return (
		<button
			type="button"
			className="button secondary"
			disabled={pending}
			aria-live="polite"
			onClick={() => startTransition(() => router.refresh())}
		>
			{pending ? "Refreshing…" : retry ? "Try again" : label}
		</button>
	);
}
