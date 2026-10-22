import { useMemo, useState } from 'react'
import {
  Activity, ArrowDownRight, ArrowUpRight, Bell, Check, ChevronDown, ChevronLeft,
  ChevronRight, CircleHelp, Clock3, Download, Ellipsis, Filter, LayoutDashboard,
  LockKeyhole, LogOut, Menu, MoreHorizontal, Plus, Search, Settings, ShieldCheck,
  SlidersHorizontal, Sparkles, Trash2, UserRound, UserRoundPlus, Users, X,
} from 'lucide-react'

const initialUsers = [
  { id: 1, name: 'Nguyễn Minh Anh', email: 'minhanh.nguyen@email.com', initials: 'MA', color: 'lavender', role: 'Người dùng', status: 'Hoạt động', joined: '12 Thg 10, 2026', tasks: 42, last: '10 phút trước' },
  { id: 2, name: 'Trần Quốc Bảo', email: 'quocbao.tran@email.com', initials: 'QB', color: 'mint', role: 'Người dùng', status: 'Hoạt động', joined: '08 Thg 10, 2026', tasks: 28, last: '1 giờ trước' },
  { id: 3, name: 'Lê Thu Hà', email: 'thuha.le@email.com', initials: 'TH', color: 'peach', role: 'Người dùng', status: 'Bị khóa', joined: '05 Thg 10, 2026', tasks: 17, last: '3 ngày trước' },
  { id: 4, name: 'Phạm Gia Huy', email: 'giahuy.pham@email.com', initials: 'GH', color: 'blue', role: 'Quản trị viên', status: 'Hoạt động', joined: '28 Thg 09, 2026', tasks: 63, last: '25 phút trước' },
  { id: 5, name: 'Đỗ Khánh Linh', email: 'khanhlinh.do@email.com', initials: 'KL', color: 'pink', role: 'Người dùng', status: 'Hoạt động', joined: '21 Thg 09, 2026', tasks: 35, last: 'Hôm qua' },
  { id: 6, name: 'Võ Hoàng Nam', email: 'hoangnam.vo@email.com', initials: 'HN', color: 'sand', role: 'Người dùng', status: 'Chờ xác minh', joined: '18 Thg 09, 2026', tasks: 0, last: 'Chưa đăng nhập' },
  { id: 7, name: 'Bùi Ngọc Mai', email: 'ngocmai.bui@email.com', initials: 'NM', color: 'mint', role: 'Người dùng', status: 'Hoạt động', joined: '15 Thg 09, 2026', tasks: 51, last: '2 giờ trước' },
  { id: 8, name: 'Đặng Tuấn Kiệt', email: 'tuankiet.dang@email.com', initials: 'TK', color: 'lavender', role: 'Người dùng', status: 'Hoạt động', joined: '10 Thg 09, 2026', tasks: 19, last: 'Hôm qua' },
]

const bars = [28, 42, 35, 55, 44, 62, 48, 71, 58, 77, 63, 84, 67, 75, 60, 89, 73, 95, 69, 81, 64, 90, 76, 100]

function App() {
  const [users, setUsers] = useState(initialUsers)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('Tất cả trạng thái')
  const [roleFilter, setRoleFilter] = useState('Tất cả vai trò')
  const [page, setPage] = useState('Tổng quan')
  const [menuId, setMenuId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toast, setToast] = useState('')
  const [form, setForm] = useState({ name: '', email: '', role: 'Người dùng' })

  const filteredUsers = useMemo(() => users.filter(user => {
    const textMatch = `${user.name} ${user.email}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi'))
    const statusMatch = statusFilter === 'Tất cả trạng thái' || user.status === statusFilter
    const roleMatch = roleFilter === 'Tất cả vai trò' || user.role === roleFilter
    return textMatch && statusMatch && roleMatch
  }), [users, query, statusFilter, roleFilter])

  const notify = (message) => { setToast(message); window.setTimeout(() => setToast(''), 2600) }
  const changeStatus = (user) => {
    const next = user.status === 'Bị khóa' ? 'Hoạt động' : 'Bị khóa'
    setUsers(items => items.map(item => item.id === user.id ? { ...item, status: next } : item))
    setMenuId(null)
    notify(`${next === 'Bị khóa' ? 'Đã khóa' : 'Đã mở khóa'} tài khoản ${user.name}`)
  }
  const openCreate = () => { setEditing(null); setForm({ name: '', email: '', role: 'Người dùng' }); setShowForm(true) }
  const openEdit = (user) => { setEditing(user); setForm({ name: user.name, email: user.email, role: user.role }); setMenuId(null); setShowForm(true) }
  const saveUser = (event) => {
    event.preventDefault()
    if (editing) {
      setUsers(items => items.map(item => item.id === editing.id ? { ...item, ...form, initials: form.name.split(' ').slice(-2).map(part => part[0]).join('').toUpperCase() } : item))
      notify('Đã cập nhật thông tin người dùng')
    } else {
      const initials = form.name.split(' ').slice(-2).map(part => part[0]).join('').toUpperCase()
      setUsers(items => [{ ...form, id: Date.now(), initials, color: 'blue', status: 'Hoạt động', joined: 'Hôm nay', tasks: 0, last: 'Chưa đăng nhập' }, ...items])
      notify('Đã thêm người dùng mới')
    }
    setShowForm(false)
  }
  const softDelete = (user) => {
    setUsers(items => items.map(item => item.id === user.id ? { ...item, status: 'Đã xóa' } : item))
    setMenuId(null)
    notify(`Đã chuyển ${user.name} vào danh sách đã xóa`)
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Check size={18} strokeWidth={3} /></div><span>taskflow<span className="brand-dot">.</span></span></div>
      <div className="workspace-label">KHÔNG GIAN LÀM VIỆC</div>
      <div className="workspace"><div className="workspace-icon"><LayoutDashboard size={17}/></div><div><strong>TaskFlow Workspace</strong><small>Gói quản trị</small></div><ChevronDown size={15} className="workspace-chevron"/></div>
      <div className="nav-label">MENU CHÍNH</div>
      <nav className="nav-list">
        <button className={`nav-item ${page === 'Tổng quan' ? 'active' : ''}`} onClick={() => setPage('Tổng quan')}><LayoutDashboard size={18}/><span>Tổng quan</span></button>
        <button className={`nav-item ${page === 'Người dùng' ? 'active' : ''}`} onClick={() => setPage('Người dùng')}><Users size={18}/><span>Người dùng</span><span className="nav-count">{users.filter(user => user.status !== 'Đã xóa').length}</span></button>
        <button className="nav-item muted" onClick={() => notify('Mục hoạt động sẽ được bổ sung sau')}><Activity size={18}/><span>Hoạt động</span></button>
      </nav>
      <div className="nav-label tools-label">CÔNG CỤ</div>
      <nav className="nav-list">
        <button className="nav-item muted" onClick={() => notify('Cài đặt đang được phát triển')}><Settings size={18}/><span>Cài đặt</span></button>
        <button className="nav-item muted" onClick={() => notify('Trung tâm trợ giúp đang được phát triển')}><CircleHelp size={18}/><span>Trung tâm trợ giúp</span></button>
      </nav>
      <div className="sidebar-bottom"><div className="plan-card"><div className="plan-icon"><Sparkles size={16}/></div><strong>Quản lý thật nhẹ nhàng</strong><p>Theo dõi hệ thống và hỗ trợ người dùng ngay tại đây.</p><button onClick={() => notify('Đây là bản giao diện mẫu')}>Tìm hiểu thêm <ChevronRight size={14}/></button><div className="plan-orb orb-one"/><div className="plan-orb orb-two"/></div><div className="admin-profile"><div className="avatar admin-avatar">AD</div><div className="profile-copy"><strong>Admin Demo</strong><small>Quản trị viên</small></div><button className="icon-button profile-more" aria-label="Tùy chọn tài khoản"><MoreHorizontal size={19}/></button></div></div>
    </aside>

    <main className="main-area">
      <header className="topbar"><div className="breadcrumbs"><button className="mobile-menu"><Menu size={18}/></button><span>Trang chủ</span><ChevronRight size={15}/><strong>{page}</strong></div><div className="topbar-actions"><div className="global-search"><Search size={16}/><input placeholder="Tìm kiếm..." /><kbd>⌘ K</kbd></div><button className="icon-button notification" aria-label="Thông báo" onClick={() => notify('Bạn đã xem hết thông báo')}><Bell size={18}/><i/></button><div className="topbar-divider"/><button className="top-profile" onClick={() => notify('Tài khoản Admin Demo')}><div className="avatar admin-avatar">AD</div><span><strong>Admin Demo</strong><small>Quản trị viên</small></span><ChevronDown size={15}/></button></div></header>

      <div className="content">
        <div className="page-heading"><div><div className="eyebrow"><span className="eyebrow-line"/> THỨ NĂM, 22 THÁNG 10, 2026</div><h1>{page === 'Người dùng' ? 'Quản lý người dùng' : 'Tổng quan hệ thống'}<span className="wave">{page === 'Người dùng' ? '' : '✦'}</span></h1><p>{page === 'Người dùng' ? 'Xem và quản lý các tài khoản trong hệ thống.' : 'Chào buổi sáng! Đây là những gì đang diễn ra hôm nay.'}</p></div><div className="heading-buttons"><button className="button button-light" onClick={() => notify('Đã tạo bản xuất dữ liệu mẫu')}><Download size={16}/> Xuất báo cáo</button><button className="button button-primary" onClick={openCreate}><Plus size={17}/> Thêm người dùng</button></div></div>

        <section className="stats-grid">
          <StatCard icon={<Users size={18}/>} label="Tổng người dùng" value={users.filter(user => user.status !== 'Đã xóa').length.toLocaleString('vi-VN')} change="12.8%" note="so với tháng trước" trend="up" tone="violet" spark={[22,32,25,39,28,46,38,55,42,62,51,72]} />
          <StatCard icon={<UserRoundPlus size={18}/>} label="Người dùng mới" value="284" change="8.2%" note="so với tháng trước" trend="up" tone="green" spark={[30,22,35,28,45,34,49,38,57,47,65,74]} />
          <StatCard icon={<Activity size={18}/>} label="Đang hoạt động" value="1,842" change="3.1%" note="so với tháng trước" trend="up" tone="blue" spark={[47,40,55,46,62,51,66,57,74,62,80,72]} />
          <StatCard icon={<Clock3 size={18}/>} label="Chờ xác minh" value={users.filter(user => user.status === 'Chờ xác minh').length.toString().padStart(2, '0')} change="2.4%" note="so với tháng trước" trend="down" tone="orange" spark={[66,60,70,54,62,48,56,42,49,36,42,30]} />
        </section>

        <section className="analytics-grid">
          <div className="panel activity-panel"><div className="panel-heading"><div><div className="panel-title">Tổng quan hoạt động</div><div className="panel-subtitle">Lượt hoạt động trong 30 ngày qua</div></div><button className="select-button" onClick={() => notify('Đang hiển thị dữ liệu 30 ngày qua')}>30 ngày qua <ChevronDown size={15}/></button></div><div className="activity-summary"><strong>24,589</strong><span className="trend-pill"><ArrowUpRight size={14}/> 14.6%</span><small>so với kỳ trước</small></div><div className="chart"><div className="chart-y"><span>5k</span><span>4k</span><span>3k</span><span>2k</span><span>1k</span></div><div className="chart-main"><div className="grid-lines"><i/><i/><i/><i/><i/></div><div className="bars">{bars.map((height, i) => <div key={i} className={`bar ${i > 19 ? 'bar-highlight' : ''}`} style={{ height: `${height}%` }} title={`${height * 50} lượt`}/>)}</div><div className="chart-x"><span>01 Thg 10</span><span>05 Thg 10</span><span>10 Thg 10</span><span>15 Thg 10</span><span>20 Thg 10</span><span>22 Thg 10</span></div></div></div></div>
          <div className="panel distribution-panel"><div className="panel-heading"><div><div className="panel-title">Phân bố người dùng</div><div className="panel-subtitle">Theo trạng thái tài khoản</div></div><button className="icon-button dots" aria-label="Tùy chọn"><Ellipsis size={19}/></button></div><div className="donut-wrap"><div className="donut"><div className="donut-center"><strong>2,480</strong><small>Tổng tài khoản</small></div></div></div><div className="legend"><div><i className="legend-dot dot-active"/><span>Hoạt động</span><strong>2,134</strong><small>86%</small></div><div><i className="legend-dot dot-pending"/><span>Chờ xác minh</span><strong>218</strong><small>9%</small></div><div><i className="legend-dot dot-locked"/><span>Bị khóa</span><strong>128</strong><small>5%</small></div></div></div>
        </section>

        <section className="panel users-panel"><div className="users-panel-heading"><div><div className="panel-title">Người dùng gần đây <span className="small-count">{filteredUsers.length}</span></div><div className="panel-subtitle">Danh sách tài khoản mới nhất trong hệ thống</div></div><button className="link-button" onClick={() => setPage('Người dùng')}>Xem tất cả <ChevronRight size={15}/></button></div>
          <div className="table-toolbar"><div className="table-search"><Search size={16}/><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm theo tên hoặc email..."/><kbd>/</kbd></div><div className="filter-actions"><label className="filter-select"><Filter size={15}/><select value={statusFilter} onChange={event => setStatusFilter(event.target.value)}><option>Tất cả trạng thái</option><option>Hoạt động</option><option>Bị khóa</option><option>Chờ xác minh</option><option>Đã xóa</option></select><ChevronDown size={14}/></label><label className="filter-select role-filter"><SlidersHorizontal size={15}/><select value={roleFilter} onChange={event => setRoleFilter(event.target.value)}><option>Tất cả vai trò</option><option>Người dùng</option><option>Quản trị viên</option></select><ChevronDown size={14}/></label><button className="icon-button table-more" aria-label="Tùy chọn bảng" onClick={() => notify('Bảng đang hiển thị đầy đủ cột')}><Ellipsis size={19}/></button></div></div>
          <div className="table-scroll"><table><thead><tr><th className="check-col"><input type="checkbox" aria-label="Chọn tất cả"/></th><th>NGƯỜI DÙNG</th><th>VAI TRÒ</th><th>TRẠNG THÁI</th><th>NGÀY THAM GIA</th><th>CÔNG VIỆC</th><th>HOẠT ĐỘNG CUỐI</th><th></th></tr></thead><tbody>{filteredUsers.slice(0, 6).map(user => <tr key={user.id}><td className="check-col"><input type="checkbox" aria-label={`Chọn ${user.name}`}/></td><td><div className="user-cell"><div className={`avatar user-avatar ${user.color}`}>{user.initials}</div><div><strong>{user.name}</strong><small>{user.email}</small></div></div></td><td><span className={`role-pill ${user.role === 'Quản trị viên' ? 'role-admin' : ''}`}>{user.role === 'Quản trị viên' && <ShieldCheck size={12}/>} {user.role}</span></td><td><Status status={user.status}/></td><td className="date-cell">{user.joined}</td><td className="task-cell">{user.tasks}</td><td className="last-cell">{user.last}</td><td className="row-menu-cell"><button className="icon-button row-menu-button" aria-label={`Tùy chọn ${user.name}`} onClick={() => setMenuId(menuId === user.id ? null : user.id)}><MoreHorizontal size={18}/></button>{menuId === user.id && <div className="row-menu"><button onClick={() => openEdit(user)}><UserRound size={14}/> Chỉnh sửa</button>{user.status !== 'Đã xóa' && <button onClick={() => changeStatus(user)}><LockKeyhole size={14}/> {user.status === 'Bị khóa' ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}</button>}<button className="danger-menu" onClick={() => softDelete(user)}><Trash2 size={14}/> Xóa mềm</button></div>}</td></tr>)}</tbody></table>{filteredUsers.length === 0 && <div className="empty-state"><Search size={20}/><strong>Không tìm thấy người dùng</strong><span>Thử thay đổi từ khóa hoặc bộ lọc.</span></div>}</div>
          <div className="table-footer"><span>Hiển thị <strong>{Math.min(filteredUsers.length, 6)}</strong> trong <strong>{filteredUsers.length}</strong> người dùng</span><div className="pagination"><button className="page-arrow" onClick={() => setPage('Người dùng')} aria-label="Trang trước"><ChevronLeft size={16}/></button><button className="page-number selected">1</button><button className="page-number" onClick={() => notify('Dữ liệu mẫu chỉ có một trang')}>2</button><button className="page-number" onClick={() => notify('Dữ liệu mẫu chỉ có một trang')}>3</button><span>...</span><button className="page-number" onClick={() => notify('Dữ liệu mẫu chỉ có một trang')}>12</button><button className="page-arrow" onClick={() => notify('Đang ở dữ liệu mẫu trang đầu')} aria-label="Trang tiếp"><ChevronRight size={16}/></button></div></div>
        </section>
        <footer className="footer"><span>© 2026 TaskFlow Admin. Bản giao diện minh họa.</span><div><button onClick={() => notify('Tài liệu đang được cập nhật')}>Tài liệu</button><button onClick={() => notify('Chính sách đang được cập nhật')}>Chính sách bảo mật</button><span className="system-status"><i/> Hệ thống hoạt động</span></div></footer>
      </div>
    </main>

    {showForm && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setShowForm(false) }}><form className="user-modal" onSubmit={saveUser}><div className="modal-heading"><div><span className="modal-icon"><UserRoundPlus size={19}/></span><h2>{editing ? 'Chỉnh sửa người dùng' : 'Thêm người dùng'}</h2><p>{editing ? 'Cập nhật thông tin tài khoản.' : 'Nhập thông tin cơ bản cho tài khoản mới.'}</p></div><button type="button" className="icon-button" aria-label="Đóng" onClick={() => setShowForm(false)}><X size={19}/></button></div><label>Họ và tên<input required value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: Nguyễn Minh Anh"/></label><label>Địa chỉ email<input required type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} placeholder="name@example.com"/></label><label>Vai trò<select value={form.role} onChange={event => setForm({ ...form, role: event.target.value })}><option>Người dùng</option><option>Quản trị viên</option></select></label><div className="modal-note"><ShieldCheck size={15}/> Dữ liệu đang được lưu trên trình duyệt để minh họa.</div><div className="modal-actions"><button type="button" className="button button-light" onClick={() => setShowForm(false)}>Hủy</button><button type="submit" className="button button-primary">{editing ? 'Lưu thay đổi' : 'Thêm người dùng'}</button></div></form></div>}
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

