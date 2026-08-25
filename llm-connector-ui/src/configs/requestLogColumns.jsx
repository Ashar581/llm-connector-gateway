export const requestLogColumns = [
    {
        header: "Model",
        accessor: "modelName",
        render: (value) => (
            <span
                className="font-mono"
                style={{ color: "#f59e0b" }}
            >
                {value}
            </span>
        ),
    },
    {
        header: "Agent",
        accessor: "agentName",
        render: (value) =>
            value || (
                <span style={{ color: "var(--text-faint)" }}>
                    —
                </span>
            ),
    },
    {
        header: "Type",
        accessor: "type",
        render: (value) => (
            <span
                className="text-xs px-2 py-0.5 rounded-full font-semibold tracking-wide"
                style={{
                    background: "rgba(245,158,11,0.1)",
                    color: "#f59e0b",
                    border: "1px solid rgba(245,158,11,0.2)",
                }}
            >
                {value}
            </span>
        ),
    },
    {
        header: "Tokens",
        accessor: "totalTokens",
        render: (value) => value?.toLocaleString(),
    },
    {
        header: "Prompt Token",
        accessor: "promptTokens",
        render: (value) => value?.toLocaleString(),
    },
    {
        header: "Completion Token",
        accessor: "completionTokens",
        render: (value) => value?.toLocaleString(),
    },
    {
        header: "Latency",
        accessor: "responseTimeInMs",
        render: (value) => (
            <span
                className="px-4 py-3 text-xs font-mono"
                style={{ color: value > 3000 ? "#f59e0b" : "var(--text-muted)" }}
            >
                {value}ms
            </span>
        ),
    },
    {
        header: "Date",
        accessor: "createdAt",
        render: (value) =>
        (<span className='' style={{ fontSize: 10, color: "var(--text-faint)" }}>
            {new Date(value).toLocaleString(undefined, {
                year: "numeric",
                month: "short",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
            })}
        </span>),
    },
];
