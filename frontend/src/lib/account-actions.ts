export type AccountAction =
	| "forgot-password"
	| "resend-verification"
	| "verify-email"
	| "reset-password";

export const accountActions = {
	"forgot-password": {
		title: "Reset your password",
		intro:
			"Enter the email you used to register. We’ll send a reset link if the account is verified.",
		button: "Send reset link",
	},
	"resend-verification": {
		title: "Verify your email",
		intro: "Enter your email to request a new verification link.",
		button: "Send verification email",
	},
	"verify-email": {
		title: "Confirm your email",
		intro: "Confirm below to finish verifying your email address.",
		button: "Verify email",
	},
	"reset-password": {
		title: "Choose a new password",
		intro: "Use 12–128 characters. Your other sessions will be signed out.",
		button: "Update password",
	},
} as const;

export function accountTokenFromHash(hash: string): string {
	const params = new URLSearchParams(hash.replace(/^#/, ""));
	const tokens = params.getAll("token");
	return tokens.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(tokens[0])
		? tokens[0]
		: "";
}

export function accountActionBody(
	action: AccountAction,
	values: {
		email?: string;
		token?: string;
		password?: string;
		confirmation?: string;
	},
) {
	if (action === "forgot-password" || action === "resend-verification") {
		const email = values.email?.trim();
		if (!email) throw new Error("Enter your email address.");
		return { email };
	}
	if (!values.token || !/^[A-Za-z0-9_-]{43}$/.test(values.token))
		throw new Error(
			"This link is missing or invalid. Open the link in your latest email, or request a new one.",
		);
	if (action === "verify-email") return { token: values.token };
	const password = values.password ?? "";
	if (password.length < 12 || password.length > 128)
		throw new Error("Use a password between 12 and 128 characters.");
	if (password !== values.confirmation)
		throw new Error("The passwords do not match.");
	return { token: values.token, password };
}
