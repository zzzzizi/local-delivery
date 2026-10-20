import type { Metadata } from "next";
import { CreateRequestForm } from "../../../components/create-request-form";

export const metadata: Metadata = { title: "Create Request" };

export default function CreateRequestPage() {
	return (
		<>
			<h1>Create Request</h1>
			<p className="intro">
				Tell a neighbour what you need and where it needs to go.
			</p>
			<CreateRequestForm />
		</>
	);
}
