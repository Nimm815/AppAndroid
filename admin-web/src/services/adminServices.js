const AUTH_ADMIN_API = import.meta.env.VITE_AUTH_ADMIN_API_URL || 'http://localhost:8081/api/admin'
const TASK_ADMIN_API = import.meta.env.VITE_TASK_ADMIN_API_URL || 'http://localhost:8082/api/admin'
const PRODUCTIVITY_ADMIN_API = import.meta.env.VITE_PRODUCTIVITY_ADMIN_API_URL || 'http://localhost:8083/api/admin'

export class ApiNotConfiguredError extends Error {
  constructor(message) {
    super(message)
    this.name = 'ApiNotConfiguredError'
  }
}

async function request(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) throw new Error(`Microservice trả về lỗi ${response.status}`)
  return response.status === 204 ? null : response.json()
}

export async function getActivityLogs(filters = {}) {
  const params = new URLSearchParams({
    search: filters.search || '', kind: filters.kind || 'all',
    range: filters.range || '30', page: String(filters.page || 0), size: String(filters.size || 10),
  })
  return request(`${PRODUCTIVITY_ADMIN_API}/activity?${params}`)
}

export async function changeAdminPassword() {
  throw new ApiNotConfiguredError('Chưa có API đổi mật khẩu Admin. Mật khẩu chưa được gửi hoặc thay đổi.')
}

export async function saveNotificationSettings() {
  throw new ApiNotConfiguredError('Backend chưa có API lưu cài đặt thông báo. Thay đổi chưa được lưu.')
}

export async function getBackendHealth() {
  const [auth, task, productivity] = await Promise.all([
    request(`${AUTH_ADMIN_API}/stats`), request(`${TASK_ADMIN_API}/stats`), request(`${PRODUCTIVITY_ADMIN_API}/stats`),
  ])
  return { database: `Auth ${auth.totalUsers} tài khoản · Task ${task.taskCount} công việc · Productivity ${productivity.activityCount30Days} hoạt động/30 ngày` }
}
