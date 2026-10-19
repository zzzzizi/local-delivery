import type { Metadata } from "next";
import type { ReactNode } from "react";
import Link from "next/link";
import { Navigation } from "../components/navigation";

import "./globals.css";

export const metadata: Metadata = {
	title: { default: "Local Delivery", template: "%s | Local Delivery" },
	description: "A local peer-to-peer delivery project for Oslo.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="en">
			<body>
				<a className="skip-link" href="#main-content">
					Skip to content
				</a>
				<header className="site-header">
					<div className="brand-row">
						<Link className="brand" href="/">
							Local Delivery<span aria-hidden="true">↗</span>
						</Link>
						<span className="location">Oslo, Norway</span>
					</div>
					<Navigation />
				</header>
				<main id="main-content" tabIndex={-1} className="page-content">
					{children}
				</main>
				<footer className="site-footer">
					Small deliveries. A more connected neighbourhood.
				</footer>
			</body>
		</html>
	);
}
