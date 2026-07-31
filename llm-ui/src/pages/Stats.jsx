import { useEffect, useMemo, useState } from "react";
import {
	ResponsiveContainer, AreaChart, Area, BarChart, Bar,
	PieChart, Pie, Cell, Tooltip, XAxis, YAxis, CartesianGrid, Legend, LabelList,
} from "recharts";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Button from "../components/Button";
import StatCard from "../components/StatCard";
import { getModelStats } from "../services/statsService";
import { getAgents, getModels } from "../services/agentService";
import * as XLSX from 'xlsx';
import DataTable from "../components/DataTable";
import { requestLogColumns } from "../configs/requestLogColumns";
import Skeleton, { SkeletonTheme } from "react-loading-skeleton";
import "react-loading-skeleton/dist/skeleton.css";

const COLORS = ["#f59e0b", "#8b5cf6", "#10b981", "#f43f5e", "#0ea5e9"];

function toUTCStart(dateStr, timeStr) {
	if (!dateStr) return "";

	const localDate = new Date(
		`${dateStr}T${timeStr || "00:00"}`
	);

	return localDate.toISOString();
}

function toUTCEnd(dateStr, timeStr) {
	if (!dateStr) return "";

	const localDate = new Date(
		`${dateStr}T${timeStr || "23:59"}:59`
	);

	return localDate.toISOString();
}

// ── Shared input wrapper that uses .input-theme ──────────────
function Field({ label, children, hint }) {
	return (
		<div className="flex flex-col gap-1.5 min-w-0">
			<div className="flex items-center justify-between">
				<label className="text-xs font-semibold uppercase tracking-widest"
					style={{ color: "var(--text-muted)" }}>
					{label}
				</label>
				{hint && <span className="text-xs" style={{ color: "var(--text-faint)" }}>{hint}</span>}
			</div>
			{children}
		</div>
	);
}

// ── Recharts custom tooltip — uses CSS variables ─────────────
function ChartTooltip({ active, payload, label }) {
	if (!active || !payload?.length) return null;
	return (
		<div className="rounded-lg px-3 py-2.5 text-xs border shadow-xl"
			style={{
				background: "var(--bg-secondary)",
				border: "1px solid var(--border-strong)",
				color: "var(--text-secondary)",
			}}>
			<p className="mb-1.5 font-semibold" style={{ color: "var(--text-muted)" }}>{label}</p>
			{payload.map((p, i) => (
				<p key={i} className="font-mono" style={{ color: p.color }}>
					{p.name}: <strong>{typeof p.value === "number" ? p.value.toLocaleString() : p.value}</strong>
				</p>
			))}
		</div>
	);
}

// ── Chart card wrapper ───────────────────────────────────────
function ChartCard({ title, children }) {
	return (
		<Card>
			<p className="text-sm font-bold mb-5" style={{ color: "var(--text-primary)" }}>{title}</p>
			{children}
		</Card>
	);
}

// ── Active filter chip ───────────────────────────────────────
function Chip({ label, value, onRemove }) {
	return (
		<div className="flex items-center gap-1.5 rounded-full px-3 py-1 text-xs border"
			style={{
				background: "rgba(245,158,11,0.08)",
				borderColor: "rgba(245,158,11,0.25)",
				color: "var(--text-secondary)",
			}}>
			<span style={{ color: "var(--text-muted)" }}>{label}:</span>
			<span>{value}</span>
			<button
				onClick={onRemove}
				className="ml-0.5 leading-none hover:opacity-70 transition-opacity"
				style={{ background: "none", border: "none", cursor: "pointer", color: "var(--text-muted)", fontSize: 14 }}
			>×</button>
		</div>
	);
}

// ── shared axis / grid props ─────────────────────────────────
const axisProps = { tick: { fill: "var(--text-faint)", fontSize: 11 }, axisLine: false, tickLine: false };
const gridProps = { strokeDasharray: "3 3", stroke: "var(--border)", vertical: false };

const INPUT_CLS = "input-theme rounded-lg px-3 py-2 text-xs w-full";

export default function Stats() {
	const [loading, setLoading] = useState(false);
	const [filters, setFilters] = useState({
		agentName: "", modelName: "bonsai", server: "",
		startDate: "", startTime: "", endDate: "", endTime: "",
	});

	const [summary, setSummary] = useState(null);
	const [logs, setLogs] = useState([]);
	const [agents, setAgents] = useState([]);
	const [models, setModels] = useState([]);

	// ── Data loading ───────────────────────────────────────────
	const loadStats = async () => {
		setLoading(true);
		const controller = new AbortController();
		try {
			const params = {
				agentName: filters.agentName || undefined,
				modelName: filters.agentName ? undefined : (filters.modelName || ''),
				server: filters.server || undefined,
				startDate: filters.startDate ? toUTCStart(filters.startDate, filters.startTime) : undefined,
				endDate: filters.endDate ? toUTCEnd(filters.endDate, filters.endTime) : undefined,
			};
			const response = await getModelStats(params, controller.signal);
			const data = response.data;
			setSummary(data);
			setLogs(data.stats || []);
		} catch (err) {
			if (err.name !== "AbortError") console.error(err);
		} finally {
			setLoading(false);
		}
	};
	const renderCustomizedLabel = (props) => {
		const { x, y, percent } = props;

		return (
			<text
				x={x}
				y={y}
				fill="var(--text-secondary)"
				fontSize={12}
				fontWeight={600}
				textAnchor="middle"
				dominantBaseline="central"
			>
				{(percent * 100).toFixed(0)}%
			</text>
		);
	};

	const exportToExcel = () => {
		const excelData = logs.map((row) => ({
			Model: row.modelName,
			Agent: row.agentName || "",
			Type: row.type,
			Tokens: row.totalTokens,
			Latency_ms: row.responseTimeInMs,
			'Date-Time': new Date(row.createdAt).toLocaleString(),
		}));

		const worksheet = XLSX.utils.json_to_sheet(excelData);
		const workbook = XLSX.utils.book_new();

		XLSX.utils.book_append_sheet(workbook, worksheet, "Recent Requests");

		XLSX.writeFile(
			workbook,
			`AI_Usage_Report_${new Date().toISOString().split("T")[0]}.xlsx`
		);
	};

	useEffect(() => {
		const controller = new AbortController();

		const loadDropdowns = async () => {
			try {
				const [agentData, modelData] = await Promise.all([
					getAgents(controller.signal),
					getModels(controller.signal),
				]);
				setAgents(agentData.map((a) => a.name));
				setModels([
					...(modelData?.free?.models?.map((m) => m.id) ?? []),
					...(modelData?.paid?.models?.map((m) => m.id) ?? []),
				]);
			} catch (e) {
				if (e.name !== "AbortError") console.error("Failed to load dropdowns:", e);
			}
		};

		loadDropdowns();
		loadStats();
		return () => controller.abort();
	}, []);

	const set = (key, val) => setFilters((p) => ({ ...p, [key]: val }));
	const clearAll = () => setFilters({ agentName: "", modelName: "", server: "", startDate: "", startTime: "", endDate: "", endTime: "" });

	const agentSelected = !!filters.agentName;
	const modelSelected = !!filters.modelName;

	const activeChips = [
		filters.agentName && { label: "Agent", value: filters.agentName, key: "agentName" },
		filters.modelName && { label: "Model", value: filters.modelName, key: "modelName" },
		filters.server && { label: "Server", value: filters.server, key: "server" },
		filters.startDate && { label: "From", value: `${filters.startDate}${filters.startTime ? " " + filters.startTime : ""}`, key: "startDate" },
		filters.endDate && { label: "To", value: `${filters.endDate}${filters.endTime ? " " + filters.endTime : ""}`, key: "endDate" },
	].filter(Boolean);

	// ── Chart data ─────────────────────────────────────────────
	const modelUsage = useMemo(() => {
		const map = {};
		logs.forEach((item) => {
			if (!map[item.modelName]) map[item.modelName] = { model: item.modelName, tokens: 0, requests: 0 };
			map[item.modelName].tokens += item.totalTokens;
			map[item.modelName].requests += 1;
		});
		return Object.values(map);
	}, [logs]);

	const typeDistribution = useMemo(() => {
		const map = {};
		logs.forEach((item) => { map[item.type] = (map[item.type] || 0) + 1; });
		return Object.entries(map).map(([name, value]) => ({ name, value }));
	}, [logs]);

	const totalTypeCount = useMemo(
		() => typeDistribution.reduce((sum, d) => sum + d.value, 0),
		[typeDistribution]
	);

	const timeSeries = useMemo(() =>
		logs.map((item) => ({
			timestamp: item.createdAt,
			label: new Date(item.createdAt).toLocaleTimeString([], {
				hour: "2-digit",
				minute: "2-digit",
			}),
			tokens: item.totalTokens,
			latency: item.responseTimeInMs,
		})),
		[logs]
	);

	// Buckets response time into human-readable ranges — surfaces the tail
	// latency a plain average would hide.
	const latencyBuckets = useMemo(() => {
		const buckets = [
			{ label: "<500ms", max: 500, count: 0 },
			{ label: "0.5–1s", max: 1000, count: 0 },
			{ label: "1–2s", max: 2000, count: 0 },
			{ label: "2–5s", max: 5000, count: 0 },
			{ label: "5s+", max: Infinity, count: 0 },
		];
		logs.forEach((item) => {
			const t = item.responseTimeInMs ?? 0;
			const bucket = buckets.find((b) => t < b.max);
			if (bucket) bucket.count += 1;
		});
		return buckets;
	}, [logs]);

	// Same per-model totals as the token chart, ranked by request count instead
	// of token volume — a model can be cheap-but-frequent or costly-but-rare.
	const requestsByModel = useMemo(
		() => [...modelUsage].sort((a, b) => b.requests - a.requests).slice(0, 8),
		[modelUsage]
	);

	const StatCardSkeleton = () => (
		<Card>
			<Skeleton height={12} width={90} />
			<Skeleton height={36} width={120} className="mt-4" />
			<Skeleton height={10} width={80} className="mt-3" />
		</Card>
	);
	const FilterSkeleton = () => (
		<Card>
			<Skeleton width={80} height={14} className="mb-6" />

			<div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
				{[...Array(6)].map((_, i) => (
					<div key={i}>
						<Skeleton height={10} width={60} className="mb-2" />
						<Skeleton height={38} />
					</div>
				))}
			</div>
		</Card>
	);
	const ChartSkeleton = () => (
		<Card>
			<Skeleton width={180} height={18} className="mb-5" />

			<Skeleton
				height={240}
				borderRadius={12}
			/>
		</Card>
	);
	const TableSkeleton = () => (
		<Card className="p-0 overflow-hidden">

			<div className="px-6 py-4 border-b flex justify-between">
				<div>
					<Skeleton width={150} height={18} />
					<Skeleton width={80} height={12} className="mt-2" />
				</div>

				<Skeleton width={120} height={38} />
			</div>

			<div className="p-6">
				{[...Array(8)].map((_, i) => (
					<div
						key={i}
						className="grid grid-cols-6 gap-4 mb-5"
					>
						{[...Array(6)].map((_, j) => (
							<Skeleton
								key={j}
								height={18}
							/>
						))}
					</div>
				))}
			</div>

		</Card>
	);
	const DashboardSkeleton = () => (
		<>
			<FilterSkeleton />

			<div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-6">
				{[...Array(8)].map((_, i) => (
					<StatCardSkeleton key={i} />
				))}
			</div>

			<div className="grid grid-cols-1 lg:grid-cols-2 gap-5 mt-6">
				{[...Array(6)].map((_, i) => (
					<ChartSkeleton key={i} />
				))}
			</div>

			<div className="mt-6">
				<TableSkeleton />
			</div>
		</>
	);

	// ────────────────────────────────────────────────────────────
	return (
		<SkeletonTheme
			baseColor="var(--bg-subtle)"
			highlightColor="var(--border)"
		>
			<div className="space-y-6">
				<PageHeader
					tag="Analytics"
					title="AI Usage"
					highlight="Statistics"
					description="Monitor token consumption, latency and model usage."
				/>

				{/* ── Filter bar — full width ───────────────────────── */}
				{loading ? (
					<DashboardSkeleton />
				) : (
					<Card>
						<p className="text-xs font-bold tracking-widest uppercase mb-4 text-amber-500">Filters</p>

						{/* Main filter grid */}
						<div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 items-end">

							{/* Agent */}
							<Field label="Agent" hint={modelSelected ? "Clear model first" : undefined}>
								<select
									className={INPUT_CLS}
									disabled={modelSelected}
									value={filters.agentName}
									onChange={(e) => setFilters((p) => ({ ...p, agentName: e.target.value, modelName: "" }))}
									style={{ opacity: modelSelected ? 0.45 : 1, cursor: modelSelected ? "not-allowed" : "pointer" }}
								>
									<option value="">All agents</option>
									{agents.map((n) => <option key={n} value={n}>{n}</option>)}
								</select>
							</Field>

							{/* Model */}
							<Field label="Model" hint={agentSelected ? "Clear agent first" : undefined}>
								<select
									className={INPUT_CLS}
									disabled={agentSelected}
									value={filters.modelName}
									onChange={(e) => setFilters((p) => ({ ...p, modelName: e.target.value, agentName: "" }))}
									style={{ opacity: agentSelected ? 0.45 : 1, cursor: agentSelected ? "not-allowed" : "pointer" }}
								>
									<option value="">All models</option>
									{models.map((id) => <option key={id} value={id}>{id}</option>)}
								</select>
							</Field>

							{/* Server */}
							<Field label="Server">
								<input
									className={INPUT_CLS}
									placeholder="e.g. prod-01"
									value={filters.server}
									onChange={(e) => set("server", e.target.value)}
								/>
							</Field>

							{/* From date + time */}
							<Field label="From">
								<div className="flex gap-1.5">
									<input
										type="date"
										// className={`${INPUT_CLS} flex-1 min-w-0`}
										className="input-theme rounded-lg w-8 flex items-center justify-end p-0 pr-1.25 bg-transparent cursor-pointer relative overflow-hidden"
										value={filters.startDate}
										onChange={(e) => set("startDate", e.target.value)}
									/>
									<input
										type="time"
										className={`${INPUT_CLS} w-24`}
										value={filters.startTime}
										onChange={(e) => set("startTime", e.target.value)}
										disabled={!filters.startDate}
										style={{ opacity: !filters.startDate ? 0.4 : 1 }}
									/>
								</div>
							</Field>

							{/* To date + time */}
							<Field label="To">
								<div className="flex gap-1.5">
									<input
										type="date"
										// className={`${INPUT_CLS} flex-1 min-w-0`}
										className="input-theme rounded-lg w-8 flex items-center justify-end p-0 pr-1.25 bg-transparent cursor-pointer relative overflow-hidden"
										value={filters.endDate}
										onChange={(e) => set("endDate", e.target.value)}
									/>
									<input
										type="time"
										className={`${INPUT_CLS} w-24`}
										value={filters.endTime}
										onChange={(e) => set("endTime", e.target.value)}
										disabled={!filters.endDate}
										style={{ opacity: !filters.endDate ? 0.4 : 1 }}
									/>
								</div>
							</Field>

							{/* Apply button */}
							<div className="flex flex-col gap-1.5">
								<label className="text-xs invisible select-none">Apply</label>
								<Button
									variant="primary"
									onClick={loadStats}
									disabled={loading}
									className="w-full flex items-center justify-center gap-2"
								>
									{loading ? (
										<>
											<span className="w-3 h-3 border-2 border-black/20 border-t-black rounded-full animate-spin" />
											Loading…
										</>
									) : "Apply"}
								</Button>
							</div>
						</div>

						{/* Active filter chips */}
						{activeChips.length > 0 && (
							<div className="flex flex-wrap items-center gap-2 mt-4 pt-4 border-t" style={{ borderColor: "var(--border)" }}>
								{activeChips.map((chip) => (
									<Chip
										key={chip.key}
										label={chip.label}
										value={chip.value}
										onRemove={() => {
											const extra = chip.key === "startDate" ? { startTime: "" } : chip.key === "endDate" ? { endTime: "" } : {};
											setFilters((p) => ({ ...p, [chip.key]: "", ...extra }));
										}}
									/>
								))}
								<button
									onClick={clearAll}
									className="text-xs uppercase tracking-wider transition-colors hover:text-amber-500 ml-1"
									style={{ background: "none", border: "none", cursor: "pointer", color: "var(--text-muted)" }}
								>
									Clear all
								</button>
							</div>
						)}
					</Card>
				)}


				{/* ── KPI cards ─────────────────────────────────────── */}
				{summary && (
					<div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
						<StatCard label="Total Requests" icon="requests" value={summary.totalAiRequests.toLocaleString()} accent="#f59e0b" />
						<StatCard label="Total Tokens" icon="tokens" value={summary.totalToken.toLocaleString()} accent="#8b5cf6" />
						<StatCard label="Total Prompt Tokens" icon="prompt" value={summary.totalPromptTokens.toLocaleString()} accent="#0ea5e9" />
						<StatCard label="Total Completion Tokens" icon="completion" value={summary.totalCompletionTokens.toLocaleString()} accent="#f43f5e" />
						<StatCard label="Avg Tokens / Req" icon="avgTokens" value={Number(summary.averageTotalTokens).toFixed(0)} accent="#10b981" />
						<StatCard label="Avg Response Time" icon="avgTime" value={`${(summary.averageTimeInMs / 1000).toFixed(2)}s`} accent="#f59e0b" />
						<StatCard label="Avg Prompt Tokens" icon="avgPrompt" value={Number(summary.averagePromptTokens).toFixed(0)} accent="#0ea5e9" />
						<StatCard label="Avg Completion Tokens" icon="avgCompletion" value={`${(summary.averageCompletionTokens).toFixed(0)}`} accent="#8b5cf6" />
					</div>
				)}

				{/* ── Charts ────────────────────────────────────────── */}
				<div className="grid grid-cols-1 lg:grid-cols-2 gap-5">

					<ChartCard title="Token Consumption Trend">
						<ResponsiveContainer width="100%" height={240}>
							<AreaChart data={timeSeries}>
								<defs>
									<linearGradient id="tokensFill" x1="0" y1="0" x2="0" y2="1">
										<stop offset="0%" stopColor="#f59e0b" stopOpacity={0.35} />
										<stop offset="100%" stopColor="#f59e0b" stopOpacity={0} />
									</linearGradient>
								</defs>
								<CartesianGrid {...gridProps} />
								<XAxis dataKey="label" {...axisProps} />
								<YAxis {...axisProps} />
								<Tooltip content={<ChartTooltip />} />
								<Area type="monotone" dataKey="tokens" name="Tokens" stroke="#f59e0b" strokeWidth={2.25}
									fill="url(#tokensFill)" dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
							</AreaChart>
						</ResponsiveContainer>
					</ChartCard>

					<ChartCard title="Response Time Trend">
						<ResponsiveContainer width="100%" height={240}>
							<AreaChart data={timeSeries}>
								<defs>
									<linearGradient id="latencyFill" x1="0" y1="0" x2="0" y2="1">
										<stop offset="0%" stopColor="#8b5cf6" stopOpacity={0.35} />
										<stop offset="100%" stopColor="#8b5cf6" stopOpacity={0} />
									</linearGradient>
								</defs>
								<CartesianGrid {...gridProps} />
								<XAxis dataKey="label" {...axisProps} />
								<YAxis {...axisProps} />
								<Tooltip content={<ChartTooltip />} />
								<Area type="monotone" dataKey="latency" name="Latency (ms)" stroke="#8b5cf6" strokeWidth={2.25}
									fill="url(#latencyFill)" dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
							</AreaChart>
						</ResponsiveContainer>
					</ChartCard>

					<ChartCard title="Token Usage by Model">
						<ResponsiveContainer width="100%" height={290}>
							<BarChart data={modelUsage} barSize={30}>
								<defs>
									{COLORS.map((c, i) => (
										<linearGradient id={`barFill-${i}`} key={c} x1="0" y1="0" x2="0" y2="1">
											<stop offset="0%" stopColor={c} stopOpacity={0.95} />
											<stop offset="100%" stopColor={c} stopOpacity={0.55} />
										</linearGradient>
									))}
								</defs>
								<CartesianGrid {...gridProps} />
								<XAxis
									dataKey="model"
									{...axisProps}
									angle={-10}
									textAnchor="end"
									height={60}
								/>
								<YAxis {...axisProps} />
								<Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--bg-subtle)" }} />
								<Bar dataKey="tokens" name="Tokens" radius={[8, 8, 0, 0]}>
									{modelUsage.map((_, i) => <Cell key={i} fill={`url(#barFill-${i % COLORS.length})`} />)}
									<LabelList dataKey="tokens" position="top"
										formatter={(v) => v.toLocaleString()}
										style={{ fill: "var(--text-faint)", fontSize: 10, fontWeight: 600 }} />
								</Bar>
							</BarChart>
						</ResponsiveContainer>
					</ChartCard>

					<ChartCard title="Request Type Distribution">
						<div className="relative">
							<ResponsiveContainer width="100%" height={290}>
								<PieChart>
									<Pie
										data={typeDistribution}
										dataKey="value"
										nameKey="name"
										outerRadius={100}
										innerRadius={54}
										paddingAngle={3}
										cornerRadius={4}
										label={renderCustomizedLabel}
										labelLine={false}
									>
										{typeDistribution.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} stroke="none" />)}
									</Pie>
									<Tooltip content={<ChartTooltip />} />
									<Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 10, color: "var(--text-muted)" }} />
								</PieChart>
							</ResponsiveContainer>
							{/* Center total — donut hole doubles as the headline number */}
							<div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none"
								style={{ paddingBottom: 28 }}>
								<span className="font-mono text-2xl font-bold" style={{ color: "var(--text-primary)" }}>
									{totalTypeCount.toLocaleString()}
								</span>
								<span className="text-[10px] uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>
									Requests
								</span>
							</div>
						</div>
					</ChartCard>

					<ChartCard title="Response Time Distribution">
						<ResponsiveContainer width="100%" height={260}>
							<BarChart data={latencyBuckets}>
								<defs>
									<linearGradient id="latencyBucketFill" x1="0" y1="0" x2="0" y2="1">
										<stop offset="0%" stopColor="#f43f5e" stopOpacity={0.9} />
										<stop offset="100%" stopColor="#f43f5e" stopOpacity={0.45} />
									</linearGradient>
								</defs>
								<CartesianGrid {...gridProps} />
								<XAxis dataKey="label" {...axisProps} />
								<YAxis {...axisProps} allowDecimals={false} />
								<Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--bg-subtle)" }} />
								<Bar dataKey="count" name="Requests" fill="url(#latencyBucketFill)" radius={[8, 8, 0, 0]}>
									<LabelList dataKey="count" position="top"
										style={{ fill: "var(--text-faint)", fontSize: 10, fontWeight: 600 }} />
								</Bar>
							</BarChart>
						</ResponsiveContainer>
					</ChartCard>

					<ChartCard title="Requests by Model">
						<ResponsiveContainer width="100%" height={Math.max(220, requestsByModel.length * 38)}>
							<BarChart data={requestsByModel} layout="vertical" margin={{ left: 8 }}>
								<defs>
									{COLORS.map((c, i) => (
										<linearGradient id={`rankFill-${i}`} key={c} x1="0" y1="0" x2="1" y2="0">
											<stop offset="0%" stopColor={c} stopOpacity={0.55} />
											<stop offset="100%" stopColor={c} stopOpacity={0.95} />
										</linearGradient>
									))}
								</defs>
								<CartesianGrid {...gridProps} horizontal={false} />
								<XAxis type="number" {...axisProps} allowDecimals={false} />
								<YAxis dataKey="model" type="category" {...axisProps} width={110} />
								<Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--bg-subtle)" }} />
								<Bar dataKey="requests" name="Requests" radius={[0, 8, 8, 0]} barSize={18}>
									{requestsByModel.map((_, i) => <Cell key={i} fill={`url(#rankFill-${i % COLORS.length})`} />)}
									<LabelList dataKey="requests" position="right"
										style={{ fill: "var(--text-faint)", fontSize: 10, fontWeight: 600 }} />
								</Bar>
							</BarChart>
						</ResponsiveContainer>
					</ChartCard>
				</div>

				{/* ── Logs table ────────────────────────────────────── */}
				<Card className="p-0! overflow-hidden">
					<div
						className="px-4 sm:px-6 py-4 border-b flex flex-wrap items-center justify-between gap-3"
						style={{ borderColor: "var(--border)" }}
					>
						<div>
							<p
								className="text-sm font-bold"
								style={{ color: "var(--text-primary)" }}
							>
								Recent Requests
							</p>
							<p
								className="text-xs mt-0.5"
								style={{ color: "var(--text-muted)" }}
							>
								{logs.length} records
							</p>
						</div>

						<Button
							variant="secondary"
							onClick={exportToExcel}
							disabled={!logs.length}
							className="flex items-center gap-2"
						>
							<svg
								xmlns="http://www.w3.org/2000/svg"
								width="16"
								height="16"
								viewBox="0 0 24 24"
								fill="none"
								stroke="currentColor"
								strokeWidth="2"
								strokeLinecap="round"
								strokeLinejoin="round"
							>
								<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
								<polyline points="7 10 12 15 17 10" />
								<line x1="12" y1="15" x2="12" y2="3" />
							</svg>
							Export Excel
						</Button>
					</div>

					<DataTable
						columns={requestLogColumns}
						data={logs}
						height="500px"
						emptyMessage="No data for the selected filters"
					/>
				</Card>
			</div>
		</SkeletonTheme>
	);
}