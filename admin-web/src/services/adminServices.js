const ADMIN_API = import.meta.env.VITE_ADMIN_API_URL || 'http://localhost:8082/api/admin'
const backendOrigin = ADMIN_API.replace(/\/api\/admin\/?$/, '')

export class ApiNotConfiguredError extends Error {
  constructor(message) {
    super(message)
    this.name = 'ApiNotConfiguredError'
  }
}

async function request(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) throw new Error(`Backend trả về lỗi ${response.status}`)
  return response.status === 204 ? null : response.json()
}

// Backend hiện chưa có hợp đồng/API nhật ký hoạt động. Adapter này là điểm tích hợp sau.
export async function getActivityLogs() {
  throw new ApiNotConfiguredError('Backend hiện chưa cung cấp API nhật ký hoạt động. Chưa có bản ghi nào được tải.')
}

export async function changeAdminPassword() {
  throw new ApiNotConfiguredError('Chưa có API đổi mật khẩu Admin. Mật khẩu chưa được gửi hoặc thay đổi.')
}

export async function saveNotificationSettings() {
  throw new ApiNotConfiguredError('Backend chưa có API lưu cài đặt thông báo. Thay đổi chưa được lưu.')
}

export async function getBackendHealth() {
  return request(`${backendOrigin}/api/health/database`)
}
