"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const links = [
	{ href: "/", label: "Home" },
	{ href: "/requests/new", label: "Create Request" },
	{ href: "/jobs", label: "Available Jobs" },
	{ href: "/my-requests", label: "My Requests" },
	{ href: "/my-deliveries", label: "My Deliveries" },
];

export function Navigation() {
	const pathname = usePathname();
	return (
		<nav aria-label="Main navigation">
			<ul className="nav-links">
				{links.map(({ href, label }) => (
					<li key={href}>
						<Link
							href={href}
							aria-current={pathname === href ? "page" : undefined}
						>
							{label}
						</Link>
					</li>
				))}
			</ul>
		</nav>
	);
}
