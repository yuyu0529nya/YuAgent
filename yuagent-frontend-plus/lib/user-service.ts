import { httpClient } from "@/lib/http-client"
import { withToast } from "@/lib/toast-utils"

// 用户信息类型
export interface UserInfo {
  id: string
  nickname: string
  email: string
  phone: string
}

// 更新用户信息请求类型
export interface UserUpdateRequest {
  nickname: string
}

// 修改密码请求类型
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

// API响应类型
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

interface JwtPayload {
  userId?: unknown
  sub?: unknown
  id?: unknown
}

// 解析 JWT Token 获取用户 ID。
function parseJwt(token: string): JwtPayload | null {
  try {
    const base64Url = token.split('.')[1]
    if (!base64Url) {
      return null
    }
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='))
      .split('').map(function(c) {
      return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    const payload: unknown = JSON.parse(jsonPayload)
    return payload !== null && typeof payload === 'object' ? payload as JwtPayload : null
  } catch {
    return null
  }
}

function getPayloadUserId(payload: JwtPayload): string | null {
  for (const value of [payload.userId, payload.sub, payload.id]) {
    if (typeof value === 'string' && value.trim()) {
      return value
    }
  }
  return null
}

// 获取当前用户ID
export function getCurrentUserId(): string | null {
  if (typeof window === "undefined") {
    return null; // 服务端渲染时返回null
  }
  
  // 优先从localStorage获取token并解析
  const token = localStorage.getItem("auth_token");
  if (token) {
    const payload = parseJwt(token);
    if (payload) {
      return getPayloadUserId(payload)
    }
  }
  
  // 如果无法从token解析，返回null
  return null;
}

// 获取当前用户ID（异步版本，从API获取）
export async function getCurrentUserIdAsync(): Promise<string | null> {
  try {
    const response = await getUserInfo();
    if (response.code === 200 && response.data) {
      return response.data.id;
    }
    return null;
  } catch (error) {
 
    return null;
  }
}

// 获取用户信息
export async function getUserInfo(): Promise<ApiResponse<UserInfo>> {
  try {
    
    const response = await httpClient.get<ApiResponse<UserInfo>>(`/users`)
    
    return response
  } catch (error) {
 
    // 返回格式化的错误响应
    return {
      code: 500,
      message: error instanceof Error ? error.message : "未知错误",
      data: null as unknown as UserInfo,
      timestamp: Date.now(),
    }
  }
}

// 更新用户信息
export async function updateUserInfo(userData: UserUpdateRequest): Promise<ApiResponse<null>> {
  try {
 
    
    const response = await httpClient.post<ApiResponse<null>>('/users', userData)
    
    return response
  } catch (error) {
 
    // 返回格式化的错误响应
    return {
      code: 500,
      message: error instanceof Error ? error.message : "未知错误",
      data: null,
      timestamp: Date.now(),
    }
  }
}

// 修改密码
export async function changePassword(passwordData: ChangePasswordRequest): Promise<ApiResponse<null>> {
  try {
 
    
    const response = await httpClient.put<ApiResponse<null>>('/users/password', passwordData)
    
    return response
  } catch (error) {
 
    // 返回格式化的错误响应
    return {
      code: 500,
      message: error instanceof Error ? error.message : "未知错误",
      data: null,
      timestamp: Date.now(),
    }
  }
}

// 带提示的函数封装
export const getUserInfoWithToast = withToast(getUserInfo, {
  showSuccessToast: false,
  showErrorToast: true,
  errorTitle: "获取用户信息失败"
})

export const updateUserInfoWithToast = withToast(updateUserInfo, {
  showSuccessToast: true,
  showErrorToast: true,
  successTitle: "个人资料已更新",
  errorTitle: "更新个人资料失败"
})

export const changePasswordWithToast = withToast(changePassword, {
  showSuccessToast: true,
  showErrorToast: true,
  successTitle: "密码修改成功",
  errorTitle: "密码修改失败"
}) 
