import { useEffect, useMemo, useState } from "react";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import DataTable from "../components/DataTable";
import StatCard from "../components/StatCard";
import { getStats } from "../services/statsService";
import { requestLogColumns } from "../configs/requestLogColumns";
import Button from "../components/Button";
import * as XLSX from 'xlsx';
import {
  ResponsiveContainer, AreaChart, Area, PieChart, Pie, Cell, Tooltip,
  XAxis, YAxis, CartesianGrid, Legend,
} from "recharts";

import Skeleton, { SkeletonTheme } from "react-loading-skeleton";
import "react-loading-skeleton/dist/skeleton.css";

const COLORS = ["#f59e0b", "#8b5cf6", "#10b981", "#f43f5e", "#0ea5e9"];

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

const axisProps = { tick: { fill: "var(--text-faint)", fontSize: 11 }, axisLine: false, tickLine: false };
const gridProps = { strokeDasharray: "3 3", stroke: "var(--border)", vertical: false };


export default function Home() {
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState(null);
  const [requestLogs, setRequestLogs] = useState([]);


  useEffect(() => {
    setTimeout(() => setLoaded(true), 80);

    const controller = new AbortController();

    const fetchStats = async () => {
      setLoading(true);
      try {
        const data = await getStats(controller.signal);

        const apiData = data.data;

        setRequestLogs(apiData.stats || []);

        setSummary(apiData)
        setLoading(false);
      } catch (e) {
        if (e.name === "AbortError") return;
        console.error("Something went wrong", e);
      }
    };

    fetchStats();

    return () => controller.abort();
  }, []);

  // ── Chart data — derived client-side from the same requestLogs already
  // fetched above, so no extra API calls are introduced ──────────────
  const activityTrend = useMemo(() =>
    requestLogs.map((item) => ({
      label: new Date(item.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      tokens: item.totalTokens,
      latency: item.responseTimeInMs,
    })),
    [requestLogs]
  );

  const typeBreakdown = useMemo(() => {
    const map = {};
    requestLogs.forEach((item) => { map[item.type] = (map[item.type] || 0) + 1; });
    return Object.entries(map).map(([name, value]) => ({ name, value }));
  }, [requestLogs]);

  const exportToExcel = () => {
    const excelData = requestLogs.map((row) => ({
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

  const StatCardSkeleton = () => (
    <Card hoverable>
      <Skeleton width={100} height={32} />
      <Skeleton width={120} height={12} className="mt-3" />
    </Card>
  );
  const RecentRequestsSkeleton = () => (
    <Card className="!p-0 overflow-hidden">
      {/* Header */}
      <div
        className="px-4 sm:px-6 py-4 border-b flex flex-wrap items-center justify-between gap-3"
        style={{ borderColor: "var(--border)" }}
      >
        <div>
          <Skeleton width={150} height={18} />
          <Skeleton width={70} height={12} className="mt-2" />
        </div>

        <Skeleton width={130} height={36} borderRadius={8} />
      </div>

      {/* Table Header */}
      <div className="px-6 py-4 border-b" style={{ borderColor: "var(--border)" }}>
        <div className="grid grid-cols-6 gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} height={12} />
          ))}
        </div>
      </div>

      {/* Table Rows */}
      <div className="px-6">
        {Array.from({ length: 8 }).map((_, row) => (
          <div
            key={row}
            className="grid grid-cols-6 gap-6 py-4 border-b"
            style={{ borderColor: "var(--border)" }}
          >
            {Array.from({ length: 6 }).map((_, col) => (
              <Skeleton key={col} height={14} />
            ))}
          </div>
        ))}
      </div>
    </Card>
  );
  const HomeSkeleton = () => (
    <>
      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-12">
        {Array.from({ length: 8 }).map((_, i) => (
          <StatCardSkeleton key={i} />
        ))}
      </div>

      {/* Recent Requests */}
      <RecentRequestsSkeleton />
    </>
  );

  return (
    <SkeletonTheme
      baseColor="var(--bg-subtle)"
      highlightColor="var(--border)"
    >

      <>

        <div className={`transition-all duration-700 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-0"}`}>
          <PageHeader
            tag="Configuration Hub"
            title="Manage Your"
            highlight="AI Agents"
            description="Configure, monitor, and switch between AI agents from a single control panel."
          />
        </div>

        {/* Stats */}
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

        {/* At-a-glance charts — same data as the table below, just visualized */}
        {summary && activityTrend.length > 0 && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 mt-6">
            <Card className="lg:col-span-2">
              <p className="text-sm font-bold mb-5" style={{ color: "var(--text-primary)" }}>Today's Token &amp; Latency Trend</p>
              <ResponsiveContainer width="100%" height={220}>
                <AreaChart data={activityTrend}>
                  <defs>
                    <linearGradient id="homeTokensFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#f59e0b" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#f59e0b" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="homeLatencyFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#8b5cf6" stopOpacity={0.3} />
                      <stop offset="100%" stopColor="#8b5cf6" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid {...gridProps} />
                  <XAxis dataKey="label" {...axisProps} />
                  <YAxis {...axisProps} />
                  <Tooltip content={<ChartTooltip />} />
                  <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 10, color: "var(--text-muted)" }} />
                  <Area type="monotone" dataKey="tokens" name="Tokens" stroke="#f59e0b" strokeWidth={2}
                    fill="url(#homeTokensFill)" dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                  <Area type="monotone" dataKey="latency" name="Latency (ms)" stroke="#8b5cf6" strokeWidth={2}
                    fill="url(#homeLatencyFill)" dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                </AreaChart>
              </ResponsiveContainer>
            </Card>

            <Card>
              <p className="text-sm font-bold mb-5" style={{ color: "var(--text-primary)" }}>Request Type Split</p>
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie
                    data={typeBreakdown}
                    dataKey="value"
                    nameKey="name"
                    outerRadius={80}
                    innerRadius={44}
                    paddingAngle={3}
                    cornerRadius={4}
                  >
                    {typeBreakdown.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} stroke="none" />)}
                  </Pie>
                  <Tooltip content={<ChartTooltip />} />
                  <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 10, color: "var(--text-muted)" }} />
                </PieChart>
              </ResponsiveContainer>
            </Card>
          </div>
        )}

        {/* Recent AI Activity */}
        {loading ? <HomeSkeleton /> : (

          <div
            className={`mt-12 transition-all duration-700 delay-200 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
              }`}
          >

            <Card className="!p-0 overflow-hidden">
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
                    {requestLogs.length} records
                  </p>
                </div>

                <Button
                  variant="secondary"
                  onClick={exportToExcel}
                  disabled={!requestLogs.length}
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
                data={requestLogs}
                height="500px"
                emptyMessage="No data for the selected filters"
              />
            </Card>
          </div>
        )}
      </>
    </SkeletonTheme>
  );
}
