import { API_CONFIG } from "@/lib/api-config"
import { consumeSseStream } from "@/lib/sse-stream"
import { createStreamResponseError } from "@/lib/stream-response-error"

export interface WidgetChatRequest {
  message: string
  sessionId: string
  fileUrls?: string[]
}

export interface WidgetChatResponse {
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

export async function widgetChatStream(
  publicId: string,
  request: WidgetChatRequest,
  signal?: AbortSignal,
): Promise<ReadableStream<Uint8Array>> {
  const response = await fetch(`${API_CONFIG.BASE_URL}/widget/${publicId}/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      Referer: typeof window !== "undefined" ? window.location.origin : "",
    },
    body: JSON.stringify(request),
    signal,
  })

  if (!response.ok) {
    throw await createStreamResponseError(response)
  }
  if (!response.body) {
    throw new Error("服务未返回流式响应")
  }
  return response.body
}

export function handleWidgetStream(
  stream: ReadableStream<Uint8Array>,
  onData: (data: WidgetChatResponse) => void,
  onError: (error: Error) => void,
  onComplete: () => void,
): Promise<void> {
  return consumeSseStream(stream, {
    parse: (payload) => JSON.parse(payload) as WidgetChatResponse,
    onData,
    onError,
    onComplete,
  })
}
