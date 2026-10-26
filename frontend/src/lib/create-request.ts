export const categoryLabels = {
	PACKAGE: "Package delivery",
	BUY: "Shopping",
	PICKUP: "Pickup",
} as const;

export const requestStatuses = [
	"OPEN",
	"ACCEPTED",
	"PICKED_UP",
	"DELIVERING",
	"DELIVERED",
	"CANCELLED",
] as const;

export type RequestCategory = keyof typeof categoryLabels;
export type RequestValues = {
	category: string;
	title: string;
	description: string;
	pickupAddress: string;
	deliveryAddress: string;
	shoppingBudget: string;
	helperReward: string;
	deadline: string;
};
export type RequestErrors = Partial<Record<keyof RequestValues, string>>;

export const emptyRequest: RequestValues = {
	category: "",
	title: "",
	description: "",
	pickupAddress: "",
	deliveryAddress: "",
	shoppingBudget: "",
	helperReward: "",
	deadline: "",
};

export type CreateRequestPayload = Omit<
	RequestValues,
	"category" | "shoppingBudget" | "helperReward"
> & {
	category: RequestCategory;
	shoppingBudget: number | null;
	helperReward: number;
};

export type DeliveryRequest = Omit<
	CreateRequestPayload,
	"customerId" | "shoppingBudget" | "helperReward"
> & {
	id: number;
	customerId: number;
	helperId: number | null;
	shoppingBudget: string | null;
	helperReward: string;
	status: (typeof requestStatuses)[number];
	createdAt: string;
	updatedAt: string;
};

export function prepareRequest(
	values: RequestValues,
):
	| { payload: CreateRequestPayload; errors?: never }
	| { errors: RequestErrors; payload?: never } {
	const errors: RequestErrors = {};
	if (!Object.hasOwn(categoryLabels, values.category)) {
		errors.category = "Choose a category.";
	}
	for (const [key, label, max] of [
		["title", "title", 200],
		["description", "description", null],
		["pickupAddress", "pickup address", 500],
		["deliveryAddress", "delivery address", 500],
	] as const) {
		const value = values[key].trim();
		if (!value) errors[key] = `Enter a ${label}.`;
		else if (max && value.length > max) {
			errors[key] = `Use ${max} characters or fewer.`;
		}
	}
	for (const key of ["shoppingBudget", "helperReward"] as const) {
		const value = values[key].trim();
		if (!value && key === "shoppingBudget") continue;
		if (!value) errors[key] = "Enter a helper reward, even if it is 0.";
		else if (
			!/^\d+(?:\.\d{1,2})?$/.test(value) ||
			Number(value) > 99_999_999.99
		) {
			errors[key] =
				"Enter an amount from 0 to 99,999,999.99 with up to two decimal places.";
		}
	}
	if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(values.deadline)) {
		errors.deadline = "Choose a deadline date and time.";
	}
	if (Object.keys(errors).length) return { errors };
	return {
		payload: {
			category: values.category as RequestCategory,
			title: values.title.trim(),
			description: values.description.trim(),
			pickupAddress: values.pickupAddress.trim(),
			deliveryAddress: values.deliveryAddress.trim(),
			shoppingBudget: values.shoppingBudget.trim()
				? Number(values.shoppingBudget)
				: null,
			helperReward: Number(values.helperReward),
			// Offset-free local dates are interpreted as Europe/Oslo by the Java API.
			// The backend validates future dates and rejects ambiguous/nonexistent DST times.
			deadline: values.deadline,
		},
	};
}

const osloDateFormat = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	dateStyle: "medium",
	timeStyle: "short",
});

export function formatOsloDate(value: string): string {
	return osloDateFormat.format(new Date(value));
}
