import { useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Badge from "../components/Badge";
import Button from "../components/Button";
import StatCard from "../components/StatCard";
import DataTable from "../components/DataTable";
import UserModal from "../components/UserModal";
import RoleModal from "../components/RoleModal";
import GroupModal from "../components/GroupModal";
import RouteAccessPanel from "../components/RouteAccessPanel";
import { getAllUsers, getUser, addUser, updateUser } from "../services/userService";
import { getAllRoles, getRole, addRole, updateRole } from "../services/roleService";
import { getAllGroups, getGroup, addGroup, updateGroup } from "../services/groupService";

function extractApiError(e) {
  const data = e?.response?.data;
  if (data?.message) return data.message;
  if (typeof data === "string" && data.length) return data;
  return e?.message ?? "Something went wrong.";
}

const TABS = [
  { id: "users", label: "Users" },
  { id: "roles", label: "Roles" },
  { id: "groups", label: "Groups" },
  { id: "access", label: "Access" },
];

function formatDate(value) {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString(undefined, {
      year: "numeric", month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit",
    });
  } catch {
    return "—";
  }
}

function ChipList({ items, accent = "rgb(56,189,248)", accentBg = "rgba(56,189,248,0.1)", accentBorder = "rgba(56,189,248,0.25)" }) {
  if (!items || items.length === 0) {
    return <span className="text-xs" style={{ color: "var(--text-faint)" }}>—</span>;
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.map((item, i) => (
        <span
          key={i}
          className="text-[10px] px-2 py-0.5 rounded-full uppercase tracking-wider"
          style={{ backgroundColor: accentBg, border: `1px solid ${accentBorder}`, color: accent }}
        >
          {item}
        </span>
      ))}
    </div>
  );
}

export default function Admin() {
  const [loaded, setLoaded] = useState(false);
  const [activeTab, setActiveTab] = useState("users");

  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);

  const [userModalOpen, setUserModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [userDetailLoading, setUserDetailLoading] = useState(false);

  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [selectedRole, setSelectedRole] = useState(null);
  const [roleDetailLoading, setRoleDetailLoading] = useState(false);

  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [groupDetailLoading, setGroupDetailLoading] = useState(false);

  useEffect(() => { setTimeout(() => setLoaded(true), 80); }, []);

  useEffect(() => {
    const controller = new AbortController();
    const load = async () => {
      setLoading(true);
      try {
        const [u, r, g] = await Promise.all([
          getAllUsers(undefined, controller.signal),
          getAllRoles(controller.signal),
          getAllGroups(controller.signal),
        ]);
        setUsers(u ?? []);
        setRoles(r ?? []);
        setGroups(g ?? []);
      } catch (e) {
        if (e.name === "AbortError" || e.name === "CanceledError") return;
        console.error("Failed to load admin data", e);
        toast.error(extractApiError(e));
      } finally {
        setLoading(false);
      }
    };
    load();
    return () => controller.abort();
  }, []);

  // ── Users ────────────────────────────────────────────────
  const openCreateUser = () => { setSelectedUser(null); setUserModalOpen(true); };

  // Edit / double-click both funnel through here: open immediately with the
  // row we already have (so the modal shows correct Edit vs Create state
  // right away — it always has an id), then hydrate with the authoritative
  // single-record GET so name/email/groups/roles are guaranteed fresh and
  // complete rather than whatever the list endpoint happened to include.
  const openEditUser = async (row) => {
    setSelectedUser(row);
    setUserModalOpen(true);
    const identifier = row?.id ?? row?.email ?? row?.username;
    if (identifier == null) return;
    setUserDetailLoading(true);
    try {
      const fresh = await getUser(identifier);
      // Some single-record lookups come back with a null id depending on
      // which identifier was used server-side — never let that clobber the
      // real id we already had from the row.
      if (fresh) setSelectedUser({ ...fresh, id: fresh.id ?? row?.id ?? null });
    } catch (e) {
      console.error("Failed to fetch user details", e);
      toast.error("Could not refresh the latest user details — showing cached data.");
    } finally {
      setUserDetailLoading(false);
    }
  };

  const handleSaveUser = async (payload, isEdit) => {
    try {
      if (isEdit) {
        const updated = await updateUser(payload);
        setUsers((prev) => prev.map((u) => (u.id === payload.id ? { ...u, ...(updated ?? payload) } : u)));
        toast.success("User updated successfully");
      } else {
        const created = await addUser(payload);
        setUsers((prev) => [...prev, created ?? payload]);
        toast.success("User created successfully");
      }
    } catch (e) {
      toast.error(extractApiError(e));
      throw e;
    }
  };

  const toggleUserActive = async (user) => {
    const toastId = toast.loading(user.active ? "Deactivating user..." : "Activating user...");
    try {
      const payload = { id: user.id, active: !user.active };
      const updated = await updateUser(payload);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, active: updated?.active ?? !user.active } : u)));
      toast.success(user.active ? "User deactivated" : "User activated", { id: toastId });
    } catch (e) {
      toast.error(extractApiError(e), { id: toastId });
    }
  };

  // ── Roles ────────────────────────────────────────────────
  const openCreateRole = () => { setSelectedRole(null); setRoleModalOpen(true); };

  const openEditRole = async (row) => {
    setSelectedRole(row);
    setRoleModalOpen(true);
    if (!row?.code) return;
    setRoleDetailLoading(true);
    try {
      const fresh = await getRole(row.code);
      if (fresh) setSelectedRole({ ...fresh, id: fresh.id ?? row?.id ?? null, code: fresh.code ?? row?.code });
    } catch (e) {
      console.error("Failed to fetch role details", e);
      toast.error("Could not refresh the latest role details — showing cached data.");
    } finally {
      setRoleDetailLoading(false);
    }
  };

  const handleSaveRole = async (payload, isEdit) => {
    try {
      if (isEdit) {
        const updated = await updateRole(payload);
        setRoles((prev) => prev.map((r) => (r.id === payload.id ? { ...r, ...(updated ?? payload) } : r)));
        toast.success("Role updated successfully");
      } else {
        const created = await addRole(payload);
        setRoles((prev) => [...prev, created ?? payload]);
        toast.success("Role created successfully");
      }
    } catch (e) {
      toast.error(extractApiError(e));
      throw e;
    }
  };

  // ── Groups ───────────────────────────────────────────────
  const openCreateGroup = () => { setSelectedGroup(null); setGroupModalOpen(true); };

  const openEditGroup = async (row) => {
    setSelectedGroup(row);
    setGroupModalOpen(true);
    if (!row?.code) return;
    setGroupDetailLoading(true);
    try {
      const fresh = await getGroup(row.code);
      if (fresh) setSelectedGroup({ ...fresh, id: fresh.id ?? row?.id ?? null, code: fresh.code ?? row?.code });
    } catch (e) {
      console.error("Failed to fetch group details", e);
      toast.error("Could not refresh the latest group details — showing cached data.");
    } finally {
      setGroupDetailLoading(false);
    }
  };

  const handleSaveGroup = async (payload, isEdit) => {
    try {
      if (isEdit) {
        const updated = await updateGroup(payload);
        setGroups((prev) => prev.map((g) => (g.id === payload.id ? { ...g, ...(updated ?? payload) } : g)));
        toast.success("Group updated successfully");
      } else {
        const created = await addGroup(payload);
        setGroups((prev) => [...prev, created ?? payload]);
        toast.success("Group created successfully");
      }
    } catch (e) {
      toast.error(extractApiError(e));
      throw e;
    }
  };

  // ── Columns ──────────────────────────────────────────────
  const userColumns = useMemo(() => [
    {
      header: "Name",
      accessor: "fullname",
      render: (_v, row) => (
        <div className="flex items-center gap-2">
          <span
            className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${row.active ? "bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]" : "bg-[var(--border-strong)]"}`}
          />
          <div>
            <div className="text-xs font-bold" style={{ color: "var(--text-primary)" }}>
              {row.fullname || `${row.firstName ?? ""} ${row.lastName ?? ""}`.trim()}
            </div>
            <div className="text-[10px]" style={{ color: "var(--text-faint)" }}>@{row.username}</div>
          </div>
        </div>
      ),
    },
    { header: "Email", accessor: "email" },
    {
      header: "Phone",
      accessor: "phoneNumber",
      render: (v, row) => v ? `${row.countryCode ?? ""} ${v}` : <span style={{ color: "var(--text-faint)" }}>—</span>,
    },
    {
      header: "Groups",
      accessor: "groups",
      render: (v) => <ChipList items={Array.from(v ?? [])} accent="rgb(52,211,153)" accentBg="rgba(52,211,153,0.1)" accentBorder="rgba(52,211,153,0.25)" />,
    },
    {
      header: "Roles",
      accessor: "roles",
      render: (v) => <ChipList items={Array.from(v ?? [])} accent="rgb(167,139,250)" accentBg="rgba(167,139,250,0.1)" accentBorder="rgba(167,139,250,0.25)" />,
    },
    {
      header: "Status",
      accessor: "active",
      render: (v) => <Badge status={v ? "active" : "idle"} />,
    },
    {
      header: "Actions",
      accessor: "id",
      render: (_v, row) => (
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => openEditUser(row)}>Edit</Button>
          <Button variant={row.active ? "ghost" : "success"} size="sm" onClick={() => toggleUserActive(row)}>
            {row.active ? "Deactivate" : "Activate"}
          </Button>
        </div>
      ),
    },
  ], []);

  const roleColumns = useMemo(() => [
    { header: "Name", accessor: "name", render: (v) => <span className="text-xs font-bold" style={{ color: "var(--text-primary)" }}>{v}</span> },
    { header: "Code", accessor: "code", render: (v) => v ? <span className="text-xs font-mono" style={{ color: "rgb(167,139,250)" }}>{v}</span> : <span style={{ color: "var(--text-faint)" }}>—</span> },
    { header: "Description", accessor: "description", render: (v) => <span className="line-clamp-2">{v || <span style={{ color: "var(--text-faint)" }}>—</span>}</span> },
    { header: "Created", accessor: "createdAt", render: formatDate },
    {
      header: "Actions",
      accessor: "id",
      render: (_v, row) => <Button variant="outline" size="sm" onClick={() => openEditRole(row)}>Edit</Button>,
    },
  ], []);

  const groupColumns = useMemo(() => [
    { header: "Name", accessor: "name", render: (v) => <span className="text-xs font-bold" style={{ color: "var(--text-primary)" }}>{v}</span> },
    { header: "Code", accessor: "code", render: (v) => v ? <span className="text-xs font-mono" style={{ color: "rgb(52,211,153)" }}>{v}</span> : <span style={{ color: "var(--text-faint)" }}>—</span> },
    { header: "Description", accessor: "description", render: (v) => <span className="line-clamp-2">{v || <span style={{ color: "var(--text-faint)" }}>—</span>}</span> },
    {
      header: "Roles",
      accessor: "roles",
      render: (v) => <ChipList items={(v ?? []).map((r) => r.name)} accent="rgb(167,139,250)" accentBg="rgba(167,139,250,0.1)" accentBorder="rgba(167,139,250,0.25)" />,
    },
    { header: "Created", accessor: "createdAt", render: formatDate },
    {
      header: "Actions",
      accessor: "id",
      render: (_v, row) => <Button variant="outline" size="sm" onClick={() => openEditGroup(row)}>Edit</Button>,
    },
  ], []);

  const activeCount = users.filter((u) => u.active).length;

  return (
    <>
      <UserModal
        open={userModalOpen}
        onClose={() => setUserModalOpen(false)}
        user={selectedUser}
        allRoles={roles}
        allGroups={groups}
        onSave={handleSaveUser}
        loading={userDetailLoading}
      />
      <RoleModal
        open={roleModalOpen}
        onClose={() => setRoleModalOpen(false)}
        role={selectedRole}
        onSave={handleSaveRole}
        loading={roleDetailLoading}
      />
      <GroupModal
        open={groupModalOpen}
        onClose={() => setGroupModalOpen(false)}
        group={selectedGroup}
        allRoles={roles}
        onSave={handleSaveGroup}
        loading={groupDetailLoading}
      />

      <div className={`transition-all duration-700 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
        <PageHeader
          tag="Identity & Access"
          title="Admin"
          highlight="Console"
          description="Manage users, roles, and groups across the gateway."
        />
      </div>

      {/* Summary stats */}
      <div className={`grid grid-cols-2 md:grid-cols-4 gap-4 mb-8 transition-all duration-700 delay-75 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
        <StatCard label="Total Users" value={users.length} sub={`${activeCount} active`} accent="#f59e0b" icon="requests" />
        <StatCard label="Total Roles" value={roles.length} accent="#a78bfa" icon="tokens" />
        <StatCard label="Total Groups" value={groups.length} accent="#34d399" icon="completion" />
        <StatCard label="Inactive Users" value={users.length - activeCount} accent="#f43f5e" icon="prompt" />
      </div>

      {/* Tab bar */}
      <div className={`flex items-center gap-2 mb-6 transition-all duration-700 delay-100 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setActiveTab(t.id)}
            className="px-5 py-2.5 text-xs uppercase tracking-widest rounded transition-all duration-200"
            style={activeTab === t.id
              ? { backgroundColor: "rgba(245,158,11,0.1)", border: "1px solid rgba(245,158,11,0.4)", color: "#f59e0b", fontWeight: 700 }
              : { border: "1px solid var(--border)", color: "var(--text-muted)" }
            }
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className={`transition-all duration-700 delay-150 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
        {activeTab === "users" && (
          <Card>
            <div className="flex items-center justify-between mb-5">
              <div>
                <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>All Users</div>
                <div className="text-[11px] mt-0.5" style={{ color: "var(--text-faint)" }}>Double-click a row to view or edit</div>
              </div>
              <Button variant="primary" onClick={openCreateUser}>+ New User</Button>
            </div>
            {loading ? (
              <LoadingRows />
            ) : (
              <DataTable columns={userColumns} data={users} emptyMessage="No users found" height="520px" onRowDoubleClick={openEditUser} />
            )}
          </Card>
        )}

        {activeTab === "roles" && (
          <Card>
            <div className="flex items-center justify-between mb-5">
              <div>
                <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>All Roles</div>
                <div className="text-[11px] mt-0.5" style={{ color: "var(--text-faint)" }}>Double-click a row to view or edit</div>
              </div>
              <Button variant="primary" onClick={openCreateRole}>+ New Role</Button>
            </div>
            {loading ? (
              <LoadingRows />
            ) : (
              <DataTable columns={roleColumns} data={roles} emptyMessage="No roles found" height="520px" onRowDoubleClick={openEditRole} />
            )}
          </Card>
        )}

        {activeTab === "groups" && (
          <Card>
            <div className="flex items-center justify-between mb-5">
              <div>
                <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>All Groups</div>
                <div className="text-[11px] mt-0.5" style={{ color: "var(--text-faint)" }}>Double-click a row to view or edit</div>
              </div>
              <Button variant="primary" onClick={openCreateGroup}>+ New Group</Button>
            </div>
            {loading ? (
              <LoadingRows />
            ) : (
              <DataTable columns={groupColumns} data={groups} emptyMessage="No groups found" height="520px" onRowDoubleClick={openEditGroup} />
            )}
          </Card>
        )}

        {activeTab === "access" && <RouteAccessPanel allRoles={roles} />}
      </div>
    </>
  );
}

function LoadingRows() {
  return (
    <div className="space-y-3 py-6">
      {[...Array(5)].map((_, i) => (
        <div
          key={i}
          className="h-10 rounded animate-pulse"
          style={{ backgroundColor: "var(--bg-subtle)" }}
        />
      ))}
    </div>
  );
}
