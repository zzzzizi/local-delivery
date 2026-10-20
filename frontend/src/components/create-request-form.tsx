"use client";

import {
	type FormEvent,
	type ReactNode,
	useEffect,
	useRef,
	useState,
} from "react";
import { ApiError, apiRequest } from "../lib/api";
import {
	categoryLabels,
	type DeliveryRequest,
	emptyRequest,
	formatOsloDate,
	prepareRequest,
	type RequestErrors,
	type RequestValues,
} from "../lib/create-request";

function Field({
	name,
	label,
	hint,
	error,
	children,
}: {
	name: keyof RequestValues;
	label: string;
	hint?: string;
	error?: string;
	children: ReactNode;
}) {
	return (
		<div className="form-field">
			<label htmlFor={name}>{label}</label>
			{hint && (
				<p className="field-hint" id={`${name}-hint`}>
					{hint}
				</p>
			)}
			{children}
			{error && (
				<p className="field-error" id={`${name}-error`}>
					{error}
				</p>
			)}
		</div>
	);
}

export function CreateRequestForm() {
	const [values, setValues] = useState<RequestValues>(emptyRequest);
	const [errors, setErrors] = useState<RequestErrors>({});
	const [message, setMessage] = useState("");
	const [pending, setPending] = useState(false);
	const [created, setCreated] = useState<DeliveryRequest | null>(null);
	const submitting = useRef(false);
	const feedback = useRef<HTMLDivElement>(null);

	useEffect(() => {
		if (message || created || Object.keys(errors).length)
			feedback.current?.focus();
	}, [message, created, errors]);

	function control(name: keyof RequestValues, hasHint = false) {
		return {
			id: name,
			name,
			value: values[name],
			"aria-invalid": errors[name] ? true : undefined,
			"aria-describedby":
				[hasHint && `${name}-hint`, errors[name] && `${name}-error`]
					.filter(Boolean)
					.join(" ") || undefined,
			onChange: (event: { target: { value: string } }) => {
				setValues((previous) => ({ ...previous, [name]: event.target.value }));
			},
		};
	}

	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (submitting.current) return;
		const result = prepareRequest(values);
		if (result.errors) {
			setErrors(result.errors);
			setMessage("Please correct the highlighted fields.");
			return;
		}
		submitting.current = true;
		setPending(true);
		setErrors({});
		setMessage("");
		try {
			const request = await apiRequest<DeliveryRequest>("/requests", {
				method: "POST",
				body: result.payload,
			});
			if (!request?.id) throw new Error("Missing created request");
			setCreated(request);
		} catch (error) {
			if (error instanceof ApiError && error.status >= 400) {
				const fieldErrors: RequestErrors = {};
				for (const key of Object.keys(
					emptyRequest,
				) as (keyof RequestValues)[]) {
					if (error.fields[key]) fieldErrors[key] = error.fields[key];
				}
				setErrors(fieldErrors);
				setMessage(
					Object.keys(fieldErrors).length
						? "Please correct the highlighted fields."
						: error.message,
				);
			} else {
				setMessage(
					"We couldn’t confirm your submission. Your entries are still here. Check your connection and whether the request was saved before trying again.",
				);
			}
		} finally {
			submitting.current = false;
			setPending(false);
		}
	}

	if (created) {
		return (
			<div className="request-confirmation">
				<div
					ref={feedback}
					tabIndex={-1}
					className="success-notice"
					role="status"
				>
					<h2>Request created</h2>
					<p>Your request #{created.id} is saved and ready for a helper.</p>
				</div>
				<article className="request-card" aria-label="Created request">
					<div className="request-card-heading">
						<p className="eyebrow">{categoryLabels[created.category]}</p>
						<span className="status-badge">{created.status}</span>
					</div>
					<h2>{created.title}</h2>
					<p className="request-description">{created.description}</p>
					<dl className="request-details">
						<div>
							<dt>Pickup address</dt>
							<dd>{created.pickupAddress}</dd>
						</div>
						<div>
							<dt>Delivery address</dt>
							<dd>{created.deliveryAddress}</dd>
						</div>
						<div>
							<dt>Shopping budget</dt>
							<dd>
								{created.shoppingBudget === null
									? "Not specified"
									: `${created.shoppingBudget} NOK`}
							</dd>
						</div>
						<div>
							<dt>Helper reward</dt>
							<dd>{created.helperReward} NOK</dd>
						</div>
						<div>
							<dt>Deadline (Oslo time)</dt>
							<dd>{formatOsloDate(created.deadline)}</dd>
						</div>
						<div>
							<dt>Helper</dt>
							<dd>
								{created.helperId === null
									? "Waiting for a helper"
									: `Helper #${created.helperId}`}
							</dd>
						</div>
					</dl>
				</article>
				<button
					className="button secondary"
					type="button"
					onClick={() => {
						setCreated(null);
						setValues(emptyRequest);
						requestAnimationFrame(() =>
							document.getElementById("category")?.focus(),
						);
					}}
				>
					Create another request
				</button>
			</div>
		);
	}

	return (
		<form
			className="request-form"
			onSubmit={submit}
			noValidate
			aria-label="Create a delivery request"
			aria-busy={pending}
		>
			<p className="form-note">
				All fields are required except shopping budget.
			</p>
			{message && (
				<div ref={feedback} tabIndex={-1} role="alert" className="error-notice">
					<h2>Request needs attention</h2>
					<p>{message}</p>
				</div>
			)}
			<fieldset disabled={pending}>
				<legend className="sr-only">Request details</legend>
				<Field name="category" label="Category" error={errors.category}>
					<select {...control("category")} required>
						<option value="">Choose a category</option>
						{Object.entries(categoryLabels).map(([value, label]) => (
							<option key={value} value={value}>
								{label}
							</option>
						))}
					</select>
				</Field>
				<Field name="title" label="Title" error={errors.title}>
					<input
						{...control("title")}
						required
						maxLength={200}
						placeholder="e.g. Pick up a parcel from the post office"
					/>
				</Field>
				<Field
					name="description"
					label="Description"
					hint="Include what needs collecting and any useful instructions."
					error={errors.description}
				>
					<textarea {...control("description", true)} required rows={4} />
				</Field>
				<div className="form-grid">
					<Field
						name="pickupAddress"
						label="Pickup address"
						error={errors.pickupAddress}
					>
						<input {...control("pickupAddress")} required maxLength={500} />
					</Field>
					<Field
						name="deliveryAddress"
						label="Delivery address"
						error={errors.deliveryAddress}
					>
						<input {...control("deliveryAddress")} required maxLength={500} />
					</Field>
					<Field
						name="shoppingBudget"
						label="Shopping budget (NOK, optional)"
						hint="Leave blank if no purchase is needed."
						error={errors.shoppingBudget}
					>
						<input
							{...control("shoppingBudget", true)}
							inputMode="decimal"
							placeholder="0.00"
						/>
					</Field>
					<Field
						name="helperReward"
						label="Helper reward (NOK)"
						hint="Use up to two decimal places. 0 is allowed."
						error={errors.helperReward}
					>
						<input
							{...control("helperReward", true)}
							required
							inputMode="decimal"
							placeholder="0.00"
						/>
					</Field>
				</div>
				<Field
					name="deadline"
					label="Deadline (Oslo time)"
					hint="Choose a future date and time in Oslo."
					error={errors.deadline}
				>
					<input
						{...control("deadline", true)}
						type="datetime-local"
						required
						step={60}
					/>
				</Field>
				<button type="submit" className="button">
					{pending ? "Posting request…" : "Post request"}
				</button>
			</fieldset>
			<p className="submission-status" role="status">
				{pending ? "Saving your request. Please wait…" : ""}
			</p>
		</form>
	);
}
