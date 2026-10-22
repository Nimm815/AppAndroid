import { useEffect, useState } from 'react'
import { Activity, AlertCircle, CheckCircle2, ChevronDown, ChevronRight, CircleHelp, Eye, EyeOff, LockKeyhole, RefreshCw, Search, ShieldCheck, SlidersHorizontal, UserRound } from 'lucide-react'
import { changeAdminPassword, getActivityLogs, getBackendHealth, saveNotificationSettings } from './services/adminServices.js'

const errorText = error => error?.message || 'Đã xảy ra lỗi không xác định.'

export function ActivityPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [kind, setKind] = useState('all')
  const [result, setResult] = useState('all')
  const [range, setRange] = useState('30')
  const [page, setPage] = useState(0)
  const load = async () => {
    setLoading(true); setError('')
    try { setData(await getActivityLogs({ search, kind, result, range, page, size: 10 })) }
    catch (err) { setData(null); setError(errorText(err)) }
    finally { setLoading(false) }
  }
  useEffect(() => { load() }, [search, kind, result, range, page]) // eslint-disable-line react-hooks/exhaustive-deps
  const rows = data?.content || []
  return <>
    <section className="stats-grid activity-stats">
      <Stat icon={<Activity size={17}/>} label="Tổng hoạt động" value={data?.totalElements} tone="violet"/>
      <Stat icon={<CheckCircle2 size={17}/>} label="Thành công" value={data?.successCount} tone="green"/>
      <Stat icon={<AlertCircle size={17}/>} label="Thất bại" value={data?.failureCount} tone="orange"/>
    </section>
    <section className="panel activity-log-panel">
      <div className="users-panel-heading"><div><div className="panel-title">Nhật ký hoạt động</div><div className="panel-subtitle">Các thao tác quản trị do backend ghi nhận</div></div><button className="button button-light" onClick={load} disabled={loading}><RefreshCw size={14}/> Làm mới</button></div>
      <div className="subpage-toolbar">
        <label className="table-search"><Search size={15}/><input aria-label="Tìm nhật ký" placeholder="Tìm trong nội dung..." value={search} onChange={e => { setSearch(e.target.value); setPage(0) }}/></label>
        <label className="filter-select"><SlidersHorizontal size={14}/><select aria-label="Lọc loại hoạt động" value={kind} onChange={e => setKind(e.target.value)}><option value="all">Tất cả hoạt động</option><option value="login_success">Đăng nhập thành công</option><option value="login_failed">Đăng nhập thất bại</option><option value="user_lock">Khóa tài khoản</option><option value="user_unlock">Mở khóa tài khoản</option><option value="settings_update">Đổi cài đặt</option></select><ChevronDown size={13}/></label>
        <label className="filter-select"><select aria-label="Lọc kết quả" value={result} onChange={e => setResult(e.target.value)}><option value="all">Mọi kết quả</option><option value="success">Thành công</option><option value="failure">Thất bại</option></select><ChevronDown size={13}/></label>
        <label className="filter-select"><select aria-label="Khoảng thời gian" value={range} onChange={e => setRange(e.target.value)}><option value="1">24 giờ qua</option><option value="7">7 ngày qua</option><option value="30">30 ngày qua</option><option value="all">Toàn bộ</option></select><ChevronDown size={13}/></label>
      </div>
      {error && <div className="subpage-notice notice-warning" role="status"><AlertCircle size={17}/><div><strong>Chưa tải được nhật ký</strong><span>{error}</span></div></div>}
      {loading ? <div className="empty-state"><Activity size={20}/><strong>Đang tải nhật ký...</strong></div> : !error && rows.length === 0 ? <div className="empty-state"><Search size={20}/><strong>Chưa có bản ghi hoạt động</strong><span>Backend chưa trả về dữ liệu nhật ký.</span></div> : null}
      {rows.length > 0 && <div className="table-scroll"><table className="activity-table"><thead><tr>{['Thời gian','Người thực hiện','Loại hoạt động','Nội dung hành động','Đối tượng liên quan','Kết quả'].map(x => <th key={x}>{x.toLocaleUpperCase('vi')}</th>)}</tr></thead><tbody>{rows.map((row, i) => <tr key={row.id ?? i}><td>{row.createdAt ? new Date(row.createdAt).toLocaleString('vi-VN') : '—'}</td><td>{row.actorName || '—'}</td><td>{row.type || '—'}</td><td>{row.description || row.action || '—'}</td><td>{row.relatedEntity || '—'}</td><td><span className={`status-pill ${row.success ? 'active' : 'locked'}`}>{row.success ? 'Thành công' : 'Thất bại'}</span></td></tr>)}</tbody></table></div>}
      {data && <div className="table-footer"><span>{data.totalElements ?? rows.length} bản ghi</span><div className="pagination"><button className="page-arrow" disabled={page <= 0} onClick={() => setPage(p => p - 1)} aria-label="Trang trước">‹</button><span>{page + 1}</span><button className="page-arrow" disabled={(page + 1) * 10 >= (data.totalElements || 0)} onClick={() => setPage(p => p + 1)} aria-label="Trang sau">›</button></div></div>}
    </section>
  </>
}

export function SettingsPage() {
  const [passwords, setPasswords] = useState({ current: '', next: '', confirm: '' })
  const [visible, setVisible] = useState({})
  const [passwordMessage, setPasswordMessage] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [notifications, setNotifications] = useState({ system: true, unusual: true })
  const [settingsMessage, setSettingsMessage] = useState('')
  const [health, setHealth] = useState({ state: 'loading', message: 'Đang kiểm tra backend…' })
  useEffect(() => {
    let active = true
    getBackendHealth().then(info => { if (active) setHealth({ state: 'ok', message: `Đã kết nối${info?.database ? ` · ${info.database}` : ''}` }) })
      .catch(err => { if (active) setHealth({ state: 'error', message: `Chưa kết nối được: ${errorText(err)}` }) })
    return () => { active = false }
  }, [])
  const submitPassword = async e => {
    e.preventDefault(); setPasswordError(''); setPasswordMessage('')
    if (!passwords.current || !passwords.next || !passwords.confirm) return setPasswordError('Vui lòng điền đủ ba trường mật khẩu.')
    if (passwords.next.length < 8) return setPasswordError('Mật khẩu mới cần ít nhất 8 ký tự.')
    if (passwords.next !== passwords.confirm) return setPasswordError('Mật khẩu xác nhận chưa trùng khớp.')
    try { await changeAdminPassword(passwords); setPasswordMessage('Đã cập nhật mật khẩu.') }
    catch (err) { setPasswordError(errorText(err)) }
  }
  const saveNotifications = async () => {
    try { await saveNotificationSettings(notifications); setSettingsMessage('Cài đặt đã được lưu.') }
    catch (err) { setSettingsMessage(errorText(err)) }
  }
  const passwordInput = (key, label) => <label className="settings-label" key={key}>{label}<span className="password-input"><input required minLength={key === 'next' ? 8 : undefined} type={visible[key] ? 'text' : 'password'} value={passwords[key]} onChange={e => setPasswords(p => ({ ...p, [key]: e.target.value }))} autoComplete={key === 'current' ? 'current-password' : 'new-password'}/><button type="button" aria-label={visible[key] ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`} onClick={() => setVisible(v => ({ ...v, [key]: !v[key] }))}>{visible[key] ? <EyeOff size={16}/> : <Eye size={16}/>}</button></span></label>
  return <div className="settings-grid">
    <section className="panel settings-card"><div className="settings-card-heading"><span className="settings-icon"><UserRound size={17}/></span><div><h2>Tài khoản quản trị</h2><p>Thông tin phiên Admin hiện chưa được xác thực bởi backend.</p></div></div><div className="profile-info"><div><span>Tên hiển thị</span><strong>Admin Demo <small>(nhãn giao diện)</small></strong></div><div><span>Email</span><strong>Backend chưa cung cấp</strong></div></div><p className="settings-hint">Chưa có đăng nhập, hồ sơ Admin hoặc API cập nhật hồ sơ trong dự án.</p></section>
    <section className="panel settings-card"><div className="settings-card-heading"><span className="settings-icon"><LockKeyhole size={17}/></span><div><h2>Đổi mật khẩu</h2><p>Biểu mẫu giao diện; không gửi mật khẩu khi chưa có API.</p></div></div><form onSubmit={submitPassword} className="settings-form">{passwordInput('current','Mật khẩu hiện tại')}{passwordInput('next','Mật khẩu mới')}{passwordInput('confirm','Xác nhận mật khẩu mới')}{passwordError && <p className="form-message error-text" role="alert">{passwordError}</p>}{passwordMessage && <p className="form-message" role="status">{passwordMessage}</p>}<button className="button button-primary" type="submit">Kiểm tra và đổi mật khẩu</button></form><p className="settings-hint">Mật khẩu chỉ nằm trong bộ nhớ trang hiện tại và không được lưu vào trình duyệt.</p></section>
    <section className="panel settings-card"><div className="settings-card-heading"><span className="settings-icon"><SlidersHorizontal size={17}/></span><div><h2>Thông báo</h2><p>Tùy chọn tạm thời trong phiên này.</p></div></div><Toggle label="Lỗi hệ thống" checked={notifications.system} onChange={v => setNotifications(n => ({ ...n, system: v }))}/><Toggle label="Hoạt động bất thường" checked={notifications.unusual} onChange={v => setNotifications(n => ({ ...n, unusual: v }))}/><p className="settings-hint">Các lựa chọn chưa được lưu backend; tải lại trang sẽ đặt về mặc định.</p><div className="settings-save-row"><span role="status">{settingsMessage}</span><button className="button button-light" onClick={saveNotifications}>Lưu cài đặt</button></div></section>
    <section className="panel settings-card"><div className="settings-card-heading"><span className="settings-icon"><ShieldCheck size={17}/></span><div><h2>Dịch vụ và hệ thống</h2><p>Trạng thái lấy từ kiểm tra thực tế.</p></div></div><div className="system-row"><span>TaskMind Admin</span><strong>0.1.0</strong></div><div className="system-row"><span>Spring Boot / cơ sở dữ liệu</span><strong className={health.state === 'ok' ? 'health-good' : health.state === 'error' ? 'health-bad' : ''}>{health.message}</strong></div><div className="system-row"><span>Dịch vụ gợi ý AI</span><strong>Chưa kiểm tra · chưa có API trạng thái</strong></div></section>
  </div>
}

function Toggle({ label, checked, onChange }) { return <label className="setting-toggle"><span>{label}</span><input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)}/><i aria-hidden="true"/></label> }
function Stat({ icon, label, value, tone }) { return <div className="stat-card compact-stat"><div className={`stat-icon ${tone}`}>{icon}</div><div className="stat-label">{label}</div><div className="compact-stat-value">{value == null ? '—' : Number(value).toLocaleString('vi-VN')}</div></div> }

const faqs = [
  ['Làm thế nào để đăng nhập vào Admin?', 'Hiện bản Admin đang mở trực tiếp và chưa có luồng đăng nhập hoặc xác thực phiên. “Admin Demo” chỉ là nhãn giao diện.'],
  ['Làm thế nào để tìm người dùng?', 'Mở mục Người dùng, nhập tên hoặc email vào ô tìm kiếm, sau đó dùng bộ lọc trạng thái và vai trò. Dữ liệu lấy từ Spring Boot khi backend hoạt động.'],
  ['Vì sao không thể khóa hoặc mở khóa tài khoản?', 'Các thao tác này cần backend Spring Boot và cơ sở dữ liệu hoạt động. Nếu API báo lỗi, trang sẽ hiển thị thông báo thay vì báo thành công.'],
  ['Làm gì khi website không kết nối được backend?', 'Khởi động Spring Boot, kiểm tra cấu hình URL API và trạng thái cơ sở dữ liệu trong mục Cài đặt, rồi thử tải lại dữ liệu.'],
  ['Dịch vụ AI không phản hồi thì sao?', 'Hiện chưa có dịch vụ AI/API trạng thái được tích hợp vào Admin. Chưa thể xác nhận tình trạng AI từ trang này.'],
  ['Làm thế nào để đổi mật khẩu Admin?', 'Biểu mẫu giao diện đã có kiểm tra dữ liệu, nhưng backend chưa cung cấp xác thực Admin và API đổi mật khẩu. Vì vậy hiện chưa thể đổi mật khẩu thật.'],
  ['Nhật ký hoạt động hiển thị ở đâu?', 'Mục Hoạt động đã có bộ lọc và trạng thái tải, nhưng backend hiện chưa cung cấp API đọc nhật ký nên trang sẽ báo rõ khi chưa tải được dữ liệu.'],
]

export function HelpPage({ onNavigate }) {
  return <div className="help-grid">
    <section className="panel help-card"><div className="settings-card-heading"><span className="settings-icon"><CircleHelp size={17}/></span><div><h2>Hướng dẫn sử dụng</h2><p>Tài liệu phản ánh những gì đang có trong TaskMind Admin.</p></div></div>
      <Guide title="Tổng quan hệ thống" text="Xem số liệu người dùng và hoạt động tổng hợp được API thống kê Spring Boot trả về." onClick={() => onNavigate('Tổng quan')}/>
      <Guide title="Quản lý người dùng" text="Tìm kiếm, lọc, xem danh sách; cập nhật vai trò, khóa/mở khóa hoặc xóa mềm nếu backend chấp nhận." onClick={() => onNavigate('Người dùng')}/>
      <Guide title="Nhật ký hoạt động" text="Bộ lọc và bảng đã được chuẩn bị. API nhật ký chưa có nên hiện chưa thể xem bản ghi thật." onClick={() => onNavigate('Hoạt động')}/>
      <Guide title="Cài đặt quản trị" text="Xem trạng thái backend, kiểm tra biểu mẫu đổi mật khẩu và tùy chọn thông báo chưa được lưu." onClick={() => onNavigate('Cài đặt')}/>
      <Guide title="AI và backend" text="Trạng thái backend được kiểm tra qua endpoint health hiện có. Trạng thái AI chưa có API để xác nhận." onClick={() => onNavigate('Cài đặt')}/>
    </section>
    <section className="panel help-card"><div className="panel-title">Câu hỏi thường gặp</div><div className="faq-list">{faqs.map(([q,a]) => <details key={q}><summary>{q}<ChevronDown size={15}/></summary><p>{a}</p></details>)}</div></section>
    <section className="panel help-card support-card"><h2>Liên hệ hỗ trợ</h2><p>Dự án chưa cấu hình địa chỉ email hoặc số điện thoại hỗ trợ.</p><span>Chưa có thông tin liên hệ được cung cấp.</span></section>
  </div>
}
function Guide({ title, text, onClick }) { return <button className="guide-row" onClick={onClick}><span><strong>{title}</strong><small>{text}</small></span><ChevronRight size={16}/></button> }
