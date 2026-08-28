import { API_ENDPOINTS } from "./api-config"
import type { ApiResponse } from "@/types/agent"
import { MessageType } from "@/types/conversation"
import { withToast } from "./toast-utils"
import { httpClient, type RequestConfig } from "@/lib/http-client"

// 消息类型定义
export interface MessageDTO {
  id: string
  sessionId: string
  role: string
  content: string
  messageType?: MessageType | string
  createdAt?: string
  updatedAt?: string
  fileUrls?: string[] // 文件URL列表
}

// 获取会话消息列表
export async function getSessionMessages(
  sessionId: string,
  config: RequestConfig = {},
): Promise<ApiResponse<MessageDTO[]>> {
  try {
    const data = await httpClient.get<ApiResponse<MessageDTO[]>>(
      API_ENDPOINTS.SESSION_MESSAGES(sessionId),
      config,
    )
    return data
  } catch (error) {
 
    return {
      code: 500,
      message: error instanceof Error ? error.message : "获取会话消息失败",
      data: [] as MessageDTO[],
      timestamp: Date.now(),
    }
  }
}

export const getSessionMessagesWithToast = withToast(getSessionMessages, {
  showSuccessToast: false,
  errorTitle: "获取会话消息失败"
})

