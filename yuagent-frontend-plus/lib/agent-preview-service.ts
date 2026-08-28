import { API_CONFIG } from "@/lib/api-config"
import { getAuthenticatedHeaders, handleUnauthorized } from "@/lib/http-client"
import { consumeSseStream } from "@/lib/sse-stream"
import { createStreamResponseError } from "@/lib/stream-response-error"

export interface AgentPreviewRequest {
  userMessage: string
  systemPrompt?: string
  toolIds?: string[]
  toolPresetParams?: Record<string, Record<string, Record<string, string>>>
  messageHistory?: MessageHistoryItem[]
  modelId?: string
  fileUrls?: string[]
  knowledgeBaseIds?: string[]
}

export interface MessageHistoryItem {
  id?: string
  role: "USER" | "ASSISTANT" | "SYSTEM"
  content: string
  createdAt?: string
  fileUrls?: string[]
}

export interface AgentChatResponse {
  content: string
  done: boolean
  messageType?: string
  taskId?: string
  payload?: string
  timestamp: number
  tasks?: unknown[]
  sessionId?: string
  provider?: string
  model?: string
  files?: string[]
}

export async function previewAgentStream(
  request: AgentPreviewRequest,
  signal?: AbortSignal,
): Promise<ReadableStream<Uint8Array>> {
  const response = await fetch(`${API_CONFIG.BASE_URL}/agents/sessions/preview`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...getAuthenticatedHeaders(),
    },
    body: JSON.stringify(request),
    credentials: "include",
    signal,
  })

  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorized()
    }
    throw await createStreamResponseError(response)
  }
  if (!response.body) {
    throw new Error("服务未返回流式响应")
  }
  return response.body
}

export function handlePreviewStream(
  stream: ReadableStream<Uint8Array>,
  onData: (response: AgentChatResponse) => void,
  onError: (error: Error) => void = () => {},
  onComplete: () => void = () => {},
): Promise<void> {
  return consumeSseStream(stream, {
    parse: (payload) => JSON.parse(payload) as AgentChatResponse,
    onData,
    onError,
    onComplete,
  })
}

export async function previewAgent(
  request: AgentPreviewRequest,
  onMessage: (content: string) => void,
  onComplete: (fullContent: string) => void,
  onError?: (error: Error) => void,
): Promise<void> {
  try {
    const stream = await previewAgentStream(request)
    let fullContent = ""

    await handlePreviewStream(
      stream,
      (response) => {
        const displayableTypes = [undefined, "TEXT", "TOOL_CALL"]
        if (displayableTypes.includes(response.messageType) && response.content) {
          fullContent += response.content
          onMessage(response.content)
        }
      },
      onError,
      () => onComplete(fullContent),
    )
  } catch (error) {
    onError?.(error instanceof Error ? error : new Error("预览请求失败"))
  }
}
