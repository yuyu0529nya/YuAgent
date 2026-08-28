import type { ApiResponse } from "@/types/agent"
import { API_ENDPOINTS } from "@/lib/api-config"
import { httpClient } from "@/lib/http-client"
import { withToast } from "./toast-utils"

export interface SessionDTO {
  id: string
  title: string
  description: string | null
  createdAt: string
  updatedAt: string
  archived: boolean
  agentId: string
  multiModal?: boolean
}

function failure<T>(error: unknown, fallbackMessage: string, data: T): ApiResponse<T> {
  return {
    code: 500,
    message: error instanceof Error ? error.message : fallbackMessage,
    data,
    timestamp: Date.now(),
  }
}

/** 获取当前用户的全部会话。 */
export async function getUserSessions(): Promise<ApiResponse<SessionDTO[]>> {
  try {
    return await httpClient.get<ApiResponse<SessionDTO[]>>(API_ENDPOINTS.SESSION)
  } catch (error) {
    return failure(error, "获取会话列表失败", [])
  }
}

/** 获取指定助理的会话列表。 */
export async function getAgentSessions(agentId: string): Promise<ApiResponse<SessionDTO[]>> {
  try {
    return await httpClient.get<ApiResponse<SessionDTO[]>>(API_ENDPOINTS.SESSION_DETAIL(agentId))
  } catch (error) {
    return failure(error, "获取助理会话列表失败", [])
  }
}

export async function createAgentSession(agentId: string): Promise<ApiResponse<SessionDTO>> {
  try {
    return await httpClient.post<ApiResponse<SessionDTO>>(API_ENDPOINTS.SESSION_DETAIL(agentId))
  } catch (error) {
    return failure(error, "创建助理会话失败", null as unknown as SessionDTO)
  }
}

export async function updateAgentSession(sessionId: string, title: string): Promise<ApiResponse<null>> {
  try {
    return await httpClient.put<ApiResponse<null>>(API_ENDPOINTS.SESSION_DETAIL(sessionId), {}, { params: { title } })
  } catch (error) {
    return failure(error, "更新助理会话失败", null)
  }
}

export async function deleteAgentSession(sessionId: string): Promise<ApiResponse<null>> {
  try {
    return await httpClient.delete<ApiResponse<null>>(API_ENDPOINTS.DELETE_SESSION(sessionId))
  } catch (error) {
    return failure(error, "删除助理会话失败", null)
  }
}

export async function interruptSession(sessionId: string): Promise<ApiResponse<string>> {
  try {
    return await httpClient.post<ApiResponse<string>>(API_ENDPOINTS.INTERRUPT_SESSION(sessionId))
  } catch (error) {
    return failure(error, "中断会话失败", "")
  }
}

export const getUserSessionsWithToast = withToast(getUserSessions, {
  showSuccessToast: false,
  errorTitle: "获取会话列表失败",
})

export const getAgentSessionsWithToast = withToast(getAgentSessions, {
  showSuccessToast: false,
  errorTitle: "获取助理会话列表失败",
})

export const createAgentSessionWithToast = withToast(createAgentSession, {
  successTitle: "创建助理会话成功",
  errorTitle: "创建助理会话失败",
})

export const updateAgentSessionWithToast = withToast(updateAgentSession, {
  successTitle: "更新助理会话成功",
  errorTitle: "更新助理会话失败",
})

export const deleteAgentSessionWithToast = withToast(deleteAgentSession, {
  successTitle: "删除助理会话成功",
  errorTitle: "删除助理会话失败",
})

export const interruptSessionWithToast = withToast(interruptSession, {
  showSuccessToast: false,
  errorTitle: "中断会话失败",
})

export const AgentSessionService = {
  getUserSessions,
  getAgentSessions,
  createAgentSession,
  updateAgentSession,
  deleteAgentSession,
  interruptSession,
  getUserSessionsWithToast,
  getAgentSessionsWithToast,
  createAgentSessionWithToast,
  updateAgentSessionWithToast,
  deleteAgentSessionWithToast,
  interruptSessionWithToast,
}
