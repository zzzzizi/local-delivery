export function LoadingState({ message }: { message: string }) {
	return (
		<div className="loading-state" role="status">
			<p className="loading-label">
				<span className="loading-indicator" aria-hidden="true" />
				{message}
			</p>
			<div className="loading-preview" aria-hidden="true">
				<span />
				<span />
				<span />
			</div>
		</div>
	);
}
