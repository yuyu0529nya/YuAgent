import { API_CONFIG, API_ENDPOINTS } from "@/lib/api-config"
import { getAuthenticatedHeaders, handleUnauthorized } from "@/lib/http-client"
import { consumeSseStream } from "@/lib/sse-stream"
import type { RagStreamChatRequest, RagThinkingData, SSEMessage } from "@/types/rag-dataset"

export interface RagChatOptions {
  onThinking?: (data: RagThinkingData) => void
  onThinkingContent?: (content: string, timestamp?: number) => void
  onThinkingEnd?: () => void
  onContent?: (content: string, timestamp?: number) => void
  onError?: (error: string) => void
  onDone?: () => void
  signal?: AbortSignal
}

type StreamEventType =
  | "RAG_RETRIEVAL_START"
  | "RAG_RETRIEVAL_PROGRESS"
  | "RAG_RETRIEVAL_END"
  | "RAG_RETRIEVAL_COMPLETE"
  | "RAG_THINKING_START"
  | "RAG_THINKING_PROGRESS"
  | "RAG_THINKING_END"
  | "RAG_ANSWER_START"
  | "RAG_ANSWER_PROGRESS"
  | "RAG_ANSWER_END"
  | "RAG_ANSWER_COMPLETE"
  | "TEXT"
  | "ERROR"

function parseDocuments(payload?: string): RagThinkingData["documents"] {
  if (!payload) {
    return []
  }

  try {
    const parsed: unknown = JSON.parse(payload)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function emitEvent(message: SSEMessage, options: RagChatOptions): boolean {
  const eventType = (message.messageType ?? message.type) as StreamEventType | undefined
  const content = message.content ?? message.error ?? ""

  switch (eventType) {
    case "RAG_RETRIEVAL_START":
      options.onThinking?.({ type: "retrieval", status: "start", message: content || "开始检索相关文档..." })
      break
    case "RAG_RETRIEVAL_PROGRESS":
      options.onThinking?.({ type: "retrieval", status: "progress", message: content })
      break
    case "RAG_RETRIEVAL_END":
    case "RAG_RETRIEVAL_COMPLETE": {
      const documents = parseDocuments(message.payload) ?? []
      options.onThinking?.({
        type: "retrieval",
        status: "end",
        message: content || "检索完成",
        documents,
        retrievedCount: documents.length
      })
      break
    }
    case "RAG_THINKING_START":
      options.onThinking?.({ type: "thinking", status: "start", message: content })
      break
    case "RAG_THINKING_PROGRESS":
      options.onThinkingContent?.(content, message.timestamp)
      break
    case "RAG_THINKING_END":
      options.onThinkingEnd?.()
      break
    case "RAG_ANSWER_START":
      options.onThinking?.({ type: "answer", status: "start", message: content || "开始生成回答..." })
      break
    case "RAG_ANSWER_PROGRESS":
      options.onContent?.(content, message.timestamp)
      break
    case "TEXT":
      // RAG 在无检索结果或业务异常时会以 TEXT 发送一条可展示的结束消息。
      options.onContent?.(content, message.timestamp)
      break
    case "ERROR":
      options.onError?.(content || "未知错误")
      return true
    case "RAG_ANSWER_END":
    case "RAG_ANSWER_COMPLETE":
      return true
  }

  return message.done === true
}

async function streamRagChat(url: string, request: RagStreamChatRequest, options: RagChatOptions): Promise<void> {
  let finished = false
  const finish = () => {
    if (finished) {
      return
    }
    finished = true
    options.onDone?.()
  }

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        ...getAuthenticatedHeaders()
      },
      body: JSON.stringify(request),
      signal: options.signal
    })

    if (!response.ok) {
      if (response.status === 401) {
        handleUnauthorized()
      }
      throw new Error(`请求失败（HTTP ${response.status}）`)
    }

    if (!response.body) {
      throw new Error("服务未返回流式响应")
    }

    await consumeSseStream(response.body, {
      parse: (payload) => JSON.parse(payload) as SSEMessage,
      onData: () => {},
      isComplete: (message) => emitEvent(message, options),
      onError: (error) => options.onError?.(error.message),
      onComplete: finish,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      return
    }
    options.onError?.(error instanceof Error ? error.message : "RAG 请求失败")
  }
}

export function ragStreamChatByUserRag(
  userRagId: string,
  request: RagStreamChatRequest,
  options: RagChatOptions = {}
): Promise<void> {
  return streamRagChat(
    `${API_CONFIG.BASE_URL}${API_ENDPOINTS.RAG_STREAM_CHAT_BY_USER_RAG(userRagId)}`,
    request,
    options
  )
}

export function ragStreamChat(request: RagStreamChatRequest, options: RagChatOptions = {}): Promise<void> {
  return streamRagChat(`${API_CONFIG.BASE_URL}${API_ENDPOINTS.RAG_STREAM_CHAT}`, request, options)
}

class BaseRagChatSession {
  private abortController: AbortController | null = null

  protected async run(start: (signal: AbortSignal) => Promise<void>): Promise<void> {
    this.abort()
    const controller = new AbortController()
    this.abortController = controller

    try {
      await start(controller.signal)
    } finally {
      if (this.abortController === controller) {
        this.abortController = null
      }
    }
  }

  abort(): void {
    this.abortController?.abort()
    this.abortController = null
  }

  isActive(): boolean {
    return this.abortController !== null
  }
}

export class UserRagChatSession extends BaseRagChatSession {
  start(userRagId: string, request: RagStreamChatRequest, options: RagChatOptions): Promise<void> {
    return this.run((signal) => ragStreamChatByUserRag(userRagId, request, { ...options, signal }))
  }
}

export class RagChatSession extends BaseRagChatSession {
  start(request: RagStreamChatRequest, options: RagChatOptions): Promise<void> {
    return this.run((signal) => ragStreamChat(request, { ...options, signal }))
  }
}
