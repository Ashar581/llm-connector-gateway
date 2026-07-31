import { useMemo, useState } from "react";

export default function DataTable({
    columns = [],
    data = [],
    emptyMessage = "No records found",
    height = "400px",
}) {
    const [filters, setFilters] = useState({});

    const handleFilterChange = (field, value) => {
        setFilters((prev) => ({
            ...prev,
            [field]: value,
        }));
    };

    const filteredData = useMemo(() => {
        return data.filter((row) =>
            columns.every((col) => {
                const searchValue = filters[col.accessor];

                if (!searchValue) return true;

                const cellValue =
                    row[col.accessor] !== null &&
                        row[col.accessor] !== undefined
                        ? String(row[col.accessor]).toLowerCase()
                        : "";

                return cellValue.includes(searchValue.toLowerCase());
            })
        );
    }, [data, filters, columns]);

    return (
        <div
            className="overflow-x-auto overflow-y-auto scrollbar-thin scrollbar-track-transparent scrollbar-thumb-amber-400/30 hover:scrollbar-thumb-amber-400/50"
            style={{ height }}
        >
            <table className="w-full border-collapse">
                <thead
                    className="sticky top-0 z-10"
                    style={{
                        backgroundColor: "var(--bg-secondary)",
                        color: "var(--text-muted)",
                        borderBottom: "1px solid var(--border)",
                    }}
                >
                    <tr style={{ background: "var(--bg-subtle)" }}>
                        {columns.map((col) => (
                            <th
                                key={col.accessor}
                                className="px-4 py-3 text-left text-xs font-bold tracking-widest uppercase"
                                style={{
                                    color: "var(--text-muted)",
                                    borderBottom: "1px solid var(--border)",
                                }}
                            >
                                {col.header}
                            </th>
                        ))}
                    </tr>

                    <tr style={{ background: "var(--bg-subtle)" }}>
                        {columns.map((col) => (
                            <th
                                key={`${col.accessor}-search`}
                                className="px-2 py-2"
                            >
                                <input
                                    type="text"
                                    placeholder={`Search ${col.header}`}
                                    value={filters[col.accessor] || ""}
                                    onChange={(e) =>
                                        handleFilterChange(col.accessor, e.target.value)
                                    }
                                    className="w-full px-3 py-2 text-xs rounded-md border outline-none transition-all duration-200"
                                    style={{
                                        background: "var(--bg-primary)",
                                        border: "1px solid var(--border)",
                                        color: "var(--text-secondary)",
                                    }}
                                />
                            </th>
                        ))}
                    </tr>
                </thead>

                <tbody>
                    {filteredData.length === 0 ? (
                        <tr>
                            <td
                                colSpan={columns.length}
                                className="px-4 py-12 text-center text-sm"
                                style={{ color: "var(--text-faint)" }}
                            >
                                {emptyMessage}
                            </td>
                        </tr>
                    ) : (
                        filteredData.map((row, index) => (
                            <tr
                                key={index}
                                className="transition-colors"
                                style={{
                                    borderBottom: "1px solid var(--border)",
                                }}
                                onMouseEnter={(e) =>
                                (e.currentTarget.style.background =
                                    "var(--bg-subtle)")
                                }
                                onMouseLeave={(e) =>
                                (e.currentTarget.style.background =
                                    "transparent")
                                }
                            >
                                {columns.map((col) => (
                                    <td
                                        key={col.accessor}
                                        className="px-4 py-3 text-xs"
                                    >
                                        {col.render
                                            ? col.render(
                                                row[col.accessor],
                                                row
                                            )
                                            : row[col.accessor]}
                                    </td>
                                ))}
                            </tr>
                        ))
                    )}
                </tbody>
            </table>
        </div>
    );
}