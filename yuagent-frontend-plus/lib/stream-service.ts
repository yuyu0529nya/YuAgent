import { API_CONFIG, API_ENDPOINTS } from "@/lib/api-config"
import { getAuthenticatedHeaders, handleUnauthorized } from "@/lib/http-client"
import { createStreamResponseError } from "@/lib/stream-response-error"

/**
 * 流式聊天API服务
 * 处理与流式响应相关的API调用
 */

/**
 * 获取认证头
 * @returns 认证头对象
 */
function getAuthHeaders(): HeadersInit {
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  }

  Object.assign(headers, getAuthenticatedHeaders())

  return headers
}

/**
 * Converts unsuccessful stream responses into stable errors for the UI layer.
 */
async function throwStreamResponseError(response: Response): Promise<never> {
  if (response.status === 401) {
    handleUnauthorized()
  }
  throw await createStreamResponseError(response)
}

/**
 * 发送流式聊天消息
 * @param sessionId 会话ID
 * @param message 消息内容
 * @param fileUrls 可选的文件URL列表，用于多模态功能
 * @returns 流式响应
 */
export async function streamChat(
  sessionId: string,
  message: string,
  fileUrls?: string[],
  signal?: AbortSignal,
): Promise<Response> {
  try {
    // 使用API_ENDPOINTS.CHAT常量
    const url = `${API_CONFIG.BASE_URL}${API_ENDPOINTS.CHAT}`
    
 
    
    // 构建请求体，包含可选的文件URL
    const requestBody: { sessionId: string; message: string; fileUrls?: string[] } = {
      sessionId,
      message,
    }
    
    // 如果有文件URL，添加到请求体中
    if (fileUrls && fileUrls.length > 0) {
      requestBody.fileUrls = fileUrls
    }
    
    const response = await fetch(url, {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(requestBody),
      signal,
    })
    
    if (!response.ok) {
      return throwStreamResponseError(response)
    }
    
    return response
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") {
      throw error
    }

    throw error
  }
}

