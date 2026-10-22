import { useEffect, useState } from 'react'
import {
  Activity, ArrowDownRight, ArrowUpRight, Bell, Check, ChevronDown, ChevronLeft,
  ChevronRight, CircleHelp, Clock3, Download, Ellipsis, Filter, LayoutDashboard,
  LockKeyhole, LogOut, Menu, MoreHorizontal, Plus, Search, Settings, ShieldCheck,
  SlidersHorizontal, Sparkles, Trash2, UserRound, UserRoundPlus, Users, X,
} from 'lucide-react'
import { ActivityPage, HelpPage, SettingsPage } from './AdminPages.jsx'

const ADMIN_API = import.meta.env.VITE_ADMIN_API_URL || 'http://localhost:8082/api/admin'
const avatarColors = ['lavender', 'mint', 'peach', 'blue', 'pink', 'sand']

function mapUser(user) {
  const nameParts = user.fullName.trim().split(/\s+/)
  const statusNames = { ACTIVE: 'Hoạt động', LOCKED: 'Bị khóa', DELETED: 'Đã xóa' }
  return {
    id: user.id,
    name: user.fullName,
    email: user.email,
    initials: nameParts.slice(-2).map(part => part[0]).join('').toLocaleUpperCase('vi'),
    color: avatarColors[user.id % avatarColors.length],
    role: user.role === 'ADMIN' ? 'Quản trị viên' : 'Người dùng',
    status: statusNames[user.status] || user.status,
    joined: user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—',
    tasks: user.taskCount,
    last: user.lastActivityAt ? new Date(user.lastActivityAt).toLocaleString('vi-VN') : 'Chưa ghi nhận',
  }
}

async function apiRequest(path, options = {}) {
  const response = await fetch(`${ADMIN_API}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `API trả về lỗi ${response.status}`)
  }
  return response.status === 204 ? null : response.json()
}

export default function App() {
  const [users, setUsers] = useState([])
  const [stats, setStats] = useState(null)
  const [totalUsers, setTotalUsers] = useState(0)
  const [loading, setLoading] = useState(true)
  const [apiError, setApiError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('Tất cả trạng thái')
  const [roleFilter, setRoleFilter] = useState('Tất cả vai trò')
  const [tablePage, setTablePage] = useState(0)
  const [page, setPage] = useState('Tổng quan')
  const [menuId, setMenuId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toast, setToast] = useState('')
  const [form, setForm] = useState({ name: '', email: '', role: 'Người dùng' })

  const backendStatus = { 'Tất cả trạng thái': 'ALL', 'Hoạt động': 'ACTIVE', 'Bị khóa': 'LOCKED', 'Đã xóa': 'DELETED' }[statusFilter]
  const backendRole = { 'Tất cả vai trò': 'ALL', 'Người dùng': 'USER', 'Quản trị viên': 'ADMIN' }[roleFilter]

  useEffect(() => {
    let cancelled = false
    const timer = window.setTimeout(async () => {
      setLoading(true)
      try {
        const params = new URLSearchParams({ search: query, status: backendStatus, role: backendRole, page: String(tablePage), size: '6' })
        const [userPage, dashboard] = await Promise.all([
          apiRequest(`/users?${params}`),
          apiRequest('/stats'),
        ])
        if (!cancelled) {
          setUsers(userPage.content.map(mapUser))
          setTotalUsers(userPage.totalElements)
          setStats(dashboard)
          setApiError('')
        }
      } catch (error) {
        if (!cancelled) setApiError(error.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }, 220)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [query, backendStatus, backendRole, refreshKey, tablePage])

  const filteredUsers = users
  const refreshData = () => setRefreshKey(value => value + 1)
  const changeQuery = (value) => { setQuery(value); setTablePage(0) }
  const changeStatusFilter = (value) => { setStatusFilter(value); setTablePage(0) }
  const changeRoleFilter = (value) => { setRoleFilter(value); setTablePage(0) }

  const notify = (message) => { setToast(message); window.setTimeout(() => setToast(''), 2600) }
  const changeStatus = async (user) => {
    const locked = user.status !== 'Bị khóa'
    try {
      await apiRequest(`/users/${user.id}/lock`, { method: 'PATCH', body: JSON.stringify({ locked }) })
      setMenuId(null)
      refreshData()
      notify(`${locked ? 'Đã khóa' : 'Đã mở khóa'} tài khoản ${user.name}`)
    } catch (error) { notify(error.message) }
  }
  const openCreate = () => { setEditing(null); setForm({ name: '', email: '', role: 'Người dùng' }); setShowForm(true) }
  const openEdit = (user) => { setEditing(user); setForm({ name: user.name, email: user.email, role: user.role }); setMenuId(null); setShowForm(true) }
  const saveUser = async (event) => {
    event.preventDefault()
    if (!editing) {
      notify('Chưa thể tạo tài khoản: cần thống nhất cách cấp thông tin đăng nhập trước.')
      return
    }
    try {
      await apiRequest(`/users/${editing.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ fullName: form.name, role: form.role === 'Quản trị viên' ? 'ADMIN' : 'USER' }),
      })
      setShowForm(false)
      refreshData()
      notify('Đã cập nhật thông tin người dùng')
    } catch (error) { notify(error.message) }
  }
  const softDelete = async (user) => {
    try {
      await apiRequest(`/users/${user.id}`, { method: 'DELETE' })
      setMenuId(null)
      refreshData()
      notify(`Đã chuyển ${user.name} vào danh sách đã xóa`)
    } catch (error) { notify(error.message) }
  }

  const activityPoints = stats?.dailyActivity || []
  const maxActivity = Math.max(1, ...activityPoints.map(point => point.count))
  const chartBars = activityPoints.slice(-24).map(point => ({ ...point, height: Math.max(5, Math.round(point.count / maxActivity * 100)) }))
  const accountTotal = (stats?.enabledUsers || 0) + (stats?.disabledUsers || 0)
  const enabledShare = accountTotal ? Math.round(stats.enabledUsers / accountTotal * 100) : 0
  const lockedShare = accountTotal ? 100 - enabledShare : 0
  const pageCount = Math.max(1, Math.ceil(totalUsers / 6))

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Check size={18} strokeWidth={3} /></div><span>taskmind<span className="brand-dot">.</span></span></div>
      <div className="workspace-label">KHÔNG GIAN LÀM VIỆC</div>
      <div className="workspace"><div className="workspace-icon"><LayoutDashboard size={17}/></div><div><strong>TaskMind Workspace</strong><small>Gói quản trị</small></div><ChevronDown size={15} className="workspace-chevron"/></div>
      <div className="nav-label">MENU CHÍNH</div>
      <nav className="nav-list">
        <button className={`nav-item ${page === 'Tổng quan' ? 'active' : ''}`} onClick={() => setPage('Tổng quan')}><LayoutDashboard size={18}/><span>Tổng quan</span></button>
        <button className={`nav-item ${page === 'Người dùng' ? 'active' : ''}`} onClick={() => setPage('Người dùng')}><Users size={18}/><span>Người dùng</span><span className="nav-count">{totalUsers}</span></button>
        <button className={`nav-item ${page === 'Hoạt động' ? 'active' : ''}`} onClick={() => setPage('Hoạt động')}><Activity size={18}/><span>Hoạt động</span></button>
      </nav>
      <div className="nav-label tools-label">CÔNG CỤ</div>
      <nav className="nav-list">
        <button className={`nav-item ${page === 'Cài đặt' ? 'active' : ''}`} onClick={() => setPage('Cài đặt')}><Settings size={18}/><span>Cài đặt</span></button>
        <button className={`nav-item ${page === 'Trung tâm trợ giúp' ? 'active' : ''}`} onClick={() => setPage('Trung tâm trợ giúp')}><CircleHelp size={18}/><span>Trung tâm trợ giúp</span></button>
      </nav>
      <div className="sidebar-bottom"><div className="plan-card"><div className="plan-icon"><Sparkles size={16}/></div><strong>Quản lý thật nhẹ nhàng</strong><p>Theo dõi hệ thống và hỗ trợ người dùng ngay tại đây.</p><button onClick={() => notify('Đây là bản giao diện mẫu')}>Tìm hiểu thêm <ChevronRight size={14}/></button><div className="plan-orb orb-one"/><div className="plan-orb orb-two"/></div><div className="admin-profile"><div className="avatar admin-avatar">AD</div><div className="profile-copy"><strong>Admin Demo</strong><small>Quản trị viên</small></div><button className="icon-button profile-more" aria-label="Tùy chọn tài khoản"><MoreHorizontal size={19}/></button></div></div>
    </aside>

    <main className="main-area">
      <header className="topbar"><div className="breadcrumbs"><button className="mobile-menu"><Menu size={18}/></button><span>Trang chủ</span><ChevronRight size={15}/><strong>{page}</strong></div><div className="topbar-actions"><div className="global-search"><Search size={16}/><input placeholder="Tìm kiếm..." /><kbd>⌘ K</kbd></div><button className="icon-button notification" aria-label="Thông báo" onClick={() => notify('Bạn đã xem hết thông báo')}><Bell size={18}/><i/></button><div className="topbar-divider"/><button className="top-profile" onClick={() => notify('Tài khoản Admin Demo')}><div className="avatar admin-avatar">AD</div><span><strong>Admin Demo</strong><small>Quản trị viên</small></span><ChevronDown size={15}/></button></div></header>

      <div className="content">
        <div className="page-heading"><div><div className="eyebrow"><span className="eyebrow-line"/> THỨ NĂM, 22 THÁNG 10, 2026</div><h1>{({ 'Tổng quan': 'Tổng quan hệ thống', 'Người dùng': 'Quản lý người dùng' })[page] || page}<span className="wave">{page === 'Tổng quan' ? '✦' : ''}</span></h1><p>{{ 'Tổng quan': 'Chào buổi sáng! Đây là tình hình TaskMind hiện tại.', 'Người dùng': 'Tìm kiếm, lọc và quản lý tài khoản trong hệ thống.', 'Hoạt động': 'Theo dõi các thao tác quản trị được backend ghi nhận.', 'Cài đặt': 'Thông tin và tùy chọn dành cho quản trị viên.', 'Trung tâm trợ giúp': 'Hướng dẫn sử dụng các chức năng hiện có.' }[page]}</p></div><div className="heading-buttons">{page === 'Người dùng' ? <button className="button button-primary" onClick={openCreate}><Plus size={17}/> Thêm người dùng</button> : page === 'Tổng quan' ? <button className="button button-light" onClick={() => notify('Xuất báo cáo sẽ được bổ sung sau')}><Download size={16}/> Xuất báo cáo</button> : null}</div></div>

        {apiError && <div className="api-error"><strong>Chưa lấy được dữ liệu từ Spring Boot.</strong> {apiError} <button onClick={refreshData}>Thử kết nối lại</button></div>}
        {page === 'Tổng quan' && <>
        <section className="stats-grid">
          <StatCard icon={<Users size={18}/>} label="Tổng người dùng" value={(stats?.totalUsers ?? 0).toLocaleString('vi-VN')} change="Tổng" note="chưa xóa mềm" trend="up" tone="violet" spark={[22,32,25,39,28,46,38,55,42,62,51,72]} />
          <StatCard icon={<UserRoundPlus size={18}/>} label="Người dùng mới" value={(stats?.newUsersThisMonth ?? 0).toLocaleString('vi-VN')} change="Tháng này" note="tài khoản mới" trend="up" tone="green" spark={[30,22,35,28,45,34,49,38,57,47,65,74]} />
          <StatCard icon={<Activity size={18}/>} label="Hoạt động 30 ngày" value={(stats?.activeUsers30Days ?? 0).toLocaleString('vi-VN')} change="30 ngày" note="có ghi nhận hoạt động" trend="up" tone="blue" spark={[47,40,55,46,62,51,66,57,74,62,80,72]} />
          <StatCard icon={<LockKeyhole size={18}/>} label="Tài khoản bị khóa" value={(stats?.lockedUsers ?? 0).toLocaleString('vi-VN')} change="Hiện tại" note="chưa xóa mềm" trend="down" tone="orange" spark={[66,60,70,54,62,48,56,42,49,36,42,30]} />
        </section>

        <section className="analytics-grid">
          <div className="panel activity-panel"><div className="panel-heading"><div><div className="panel-title">Tổng quan hoạt động</div><div className="panel-subtitle">Lượt thao tác được ghi trong 30 ngày qua</div></div><button className="select-button" onClick={refreshData}>Làm mới <ChevronDown size={15}/></button></div><div className="activity-summary"><strong>{(stats?.activityCount30Days ?? 0).toLocaleString('vi-VN')}</strong><span className="trend-pill"><Activity size={13}/> 30 ngày</span><small>tổng lượt thao tác</small></div><div className="chart"><div className="chart-y"><span>{maxActivity}</span><span>{Math.round(maxActivity * .75)}</span><span>{Math.round(maxActivity * .5)}</span><span>{Math.round(maxActivity * .25)}</span><span>0</span></div><div className="chart-main"><div className="grid-lines"><i/><i/><i/><i/><i/></div><div className="bars">{chartBars.map((point, i) => <div key={point.date} className={`bar ${i >= chartBars.length - 5 ? 'bar-highlight' : ''}`} style={{ height: `${point.height}%` }} title={`${point.date}: ${point.count} lượt`}/>)}</div><div className="chart-x"><span>30 ngày trước</span><span>Hoạt động theo ngày</span><span>Hôm nay</span></div></div></div></div>
          <div className="panel distribution-panel"><div className="panel-heading"><div><div className="panel-title">Phân bố người dùng</div><div className="panel-subtitle">Theo trạng thái tài khoản</div></div><button className="icon-button dots" aria-label="Tùy chọn"><Ellipsis size={19}/></button></div><div className="donut-wrap"><div className="donut" style={{ background: `conic-gradient(#7567e9 0 ${enabledShare}%, #f3b86d ${enabledShare}% 100%)` }}><div className="donut-center"><strong>{accountTotal.toLocaleString('vi-VN')}</strong><small>Tài khoản chưa xóa</small></div></div></div><div className="legend two-legend"><div><i className="legend-dot dot-active"/><span>Đang mở</span><strong>{stats?.enabledUsers ?? 0}</strong><small>{enabledShare}%</small></div><div><i className="legend-dot dot-locked"/><span>Bị khóa</span><strong>{stats?.disabledUsers ?? 0}</strong><small>{lockedShare}%</small></div></div></div>
        </section>
        </>}

        {page === 'Người dùng' && <section className="panel users-panel"><div className="users-panel-heading"><div><div className="panel-title">Danh sách người dùng <span className="small-count">{totalUsers}</span></div><div className="panel-subtitle">Tìm, lọc và quản lý tài khoản lấy từ backend Spring Boot</div></div><button className="link-button" onClick={refreshData}>Làm mới <ChevronRight size={15}/></button></div>
          <div className="table-toolbar"><div className="table-search"><Search size={16}/><input value={query} onChange={event => changeQuery(event.target.value)} placeholder="Tìm theo tên hoặc email..."/><kbd>/</kbd></div><div className="filter-actions"><label className="filter-select"><Filter size={15}/><select value={statusFilter} onChange={event => changeStatusFilter(event.target.value)}><option>Tất cả trạng thái</option><option>Hoạt động</option><option>Bị khóa</option><option>Đã xóa</option></select><ChevronDown size={14}/></label><label className="filter-select role-filter"><SlidersHorizontal size={15}/><select value={roleFilter} onChange={event => changeRoleFilter(event.target.value)}><option>Tất cả vai trò</option><option>Người dùng</option><option>Quản trị viên</option></select><ChevronDown size={14}/></label><button className="icon-button table-more" aria-label="Làm mới dữ liệu" onClick={refreshData}><Ellipsis size={19}/></button></div></div>
          <div className="table-scroll"><table><thead><tr><th className="check-col"><input type="checkbox" aria-label="Chọn tất cả"/></th><th>NGƯỜI DÙNG</th><th>VAI TRÒ</th><th>TRẠNG THÁI</th><th>NGÀY THAM GIA</th><th>CÔNG VIỆC</th><th>HOẠT ĐỘNG CUỐI</th><th></th></tr></thead><tbody>{filteredUsers.map(user => <tr key={user.id}><td className="check-col"><input type="checkbox" aria-label={`Chọn ${user.name}`}/></td><td><div className="user-cell"><div className={`avatar user-avatar ${user.color}`}>{user.initials}</div><div><strong>{user.name}</strong><small>{user.email}</small></div></div></td><td><span className={`role-pill ${user.role === 'Quản trị viên' ? 'role-admin' : ''}`}>{user.role === 'Quản trị viên' && <ShieldCheck size={12}/>} {user.role}</span></td><td><Status status={user.status}/></td><td className="date-cell">{user.joined}</td><td className="task-cell">{user.tasks}</td><td className="last-cell">{user.last}</td><td className="row-menu-cell"><button className="icon-button row-menu-button" aria-label={`Tùy chọn ${user.name}`} onClick={() => setMenuId(menuId === user.id ? null : user.id)}><MoreHorizontal size={18}/></button>{menuId === user.id && <div className="row-menu"><button onClick={() => openEdit(user)}><UserRound size={14}/> Chỉnh sửa</button>{user.status !== 'Đã xóa' && <button onClick={() => changeStatus(user)}><LockKeyhole size={14}/> {user.status === 'Bị khóa' ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}</button>}{user.status !== 'Đã xóa' && <button className="danger-menu" onClick={() => softDelete(user)}><Trash2 size={14}/> Xóa mềm</button>}</div>}</td></tr>)}</tbody></table>{loading && <div className="empty-state"><Activity size={20}/><strong>Đang tải dữ liệu...</strong></div>}{!loading && filteredUsers.length === 0 && <div className="empty-state"><Search size={20}/><strong>{apiError ? 'Chưa kết nối được backend' : 'Không tìm thấy người dùng'}</strong><span>{apiError ? 'Hãy kiểm tra Spring Boot và kết nối cơ sở dữ liệu.' : 'Thử thay đổi từ khóa hoặc bộ lọc.'}</span></div>}</div>
          <div className="table-footer"><span>Trang <strong>{tablePage + 1}</strong> / <strong>{pageCount}</strong> · tổng <strong>{totalUsers}</strong> người dùng</span><div className="pagination"><button className="page-arrow" disabled={tablePage === 0} onClick={() => setTablePage(value => Math.max(0, value - 1))} aria-label="Trang trước"><ChevronLeft size={16}/></button><span>{tablePage + 1} / {pageCount}</span><button className="page-arrow" disabled={tablePage + 1 >= pageCount} onClick={() => setTablePage(value => Math.min(pageCount - 1, value + 1))} aria-label="Trang tiếp"><ChevronRight size={16}/></button></div></div>
        </section>}
        {page === 'Hoạt động' && <ActivityPage/>}
        {page === 'Cài đặt' && <SettingsPage/>}
        {page === 'Trung tâm trợ giúp' && <HelpPage onNavigate={setPage}/>}
        <footer className="footer"><span>© 2026 TaskMind Admin.</span><div><button onClick={() => notify('Tài liệu đang được cập nhật')}>Tài liệu</button><button onClick={() => notify('Chính sách đang được cập nhật')}>Chính sách bảo mật</button><span className="system-status"><i/> {apiError ? 'Backend chưa kết nối' : 'Đã kết nối backend'}</span></div></footer>
      </div>
    </main>

    {showForm && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setShowForm(false) }}><form className="user-modal" onSubmit={saveUser}><div className="modal-heading"><div><span className="modal-icon"><UserRoundPlus size={19}/></span><h2>{editing ? 'Chỉnh sửa người dùng' : 'Thêm người dùng'}</h2><p>{editing ? 'Cập nhật thông tin hồ sơ trong hệ thống.' : 'Tạo tài khoản cần có quy trình cấp thông tin đăng nhập.'}</p></div><button type="button" className="icon-button" aria-label="Đóng" onClick={() => setShowForm(false)}><X size={19}/></button></div><label>Họ và tên<input required value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: Nguyễn Minh Anh"/></label><label>Địa chỉ email<input required type="email" disabled={Boolean(editing)} value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} placeholder="name@example.com"/></label><label>Vai trò<select value={form.role} onChange={event => setForm({ ...form, role: event.target.value })}><option>Người dùng</option><option>Quản trị viên</option></select></label><div className="modal-note"><ShieldCheck size={15}/> {editing ? 'Thay đổi sẽ được lưu vào backend Spring Boot.' : 'Chưa tạo tài khoản vì cách cấp mật khẩu/đăng nhập chưa được quyết định.'}</div><div className="modal-actions"><button type="button" className="button button-light" onClick={() => setShowForm(false)}>Hủy</button><button type="submit" className="button button-primary" disabled={!editing}>{editing ? 'Lưu thay đổi' : 'Chưa thể thêm'}</button></div></form></div>}
    {toast && <div className="toast"><Check size={16}/>{toast}</div>}
  </div>
}

function StatCard({ icon, label, value, change, note, trend, tone, spark }) {
  return <div className="stat-card"><div className="stat-top"><div className={`stat-icon ${tone}`}>{icon}</div><button className="icon-button stat-menu" aria-label="Thêm lựa chọn"><MoreHorizontal size={18}/></button></div><div className="stat-label">{label}</div><div className="stat-value-row"><strong>{value}</strong><div className={`sparkline ${tone}`}>{spark.map((height, i) => <i key={i} style={{ height: `${height}%` }}/>)}</div></div><div className="stat-bottom"><span className={`change ${trend}`}>{trend === 'up' ? <ArrowUpRight size={13}/> : <ArrowDownRight size={13}/>} {change}</span><span>{note}</span></div></div>
}

function Status({ status }) {
  const key = status === 'Hoạt động' ? 'active' : status === 'Bị khóa' ? 'locked' : status === 'Đã xóa' ? 'deleted' : 'pending'
  return <span className={`status-pill ${key}`}><i/>{status}</span>
}

