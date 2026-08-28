import { useCallback, useEffect, useRef, useState } from 'react'
import type { RagChatOptions } from '@/lib/rag-chat-service'
import type { RagThinkingData } from '@/types/rag-dataset'
import { useRafTextBuffer } from './useRafTextBuffer'

export interface RagChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  retrieval?: RagThinkingData
  thinking?: RagThinkingData
  thinkingContent?: string
  timestamp: Date
  isStreaming?: boolean
  isThinkingComplete?: boolean
  isRetrievalComplete?: boolean
}

export interface UseRagChatSessionOptions {
  onError?: (error: string) => void
  onDone?: () => void
}

interface AbortableRagSession {
  abort(): void
  isActive(): boolean
}

interface ManagedRagChatConfig<TSession extends AbortableRagSession, TRequest> {
  createSession: () => TSession
  startSession: (session: TSession, request: TRequest, options: RagChatOptions) => Promise<void>
  getQuestion: (request: TRequest) => string
  getErrorMessage: (error: string) => string
}

/** Shared streaming state machine for created and installed RAG chats. */
export function useManagedRagChatSession<TSession extends AbortableRagSession, TRequest>(
  config: ManagedRagChatConfig<TSession, TRequest>,
  options: UseRagChatSessionOptions = {}
) {
  const [messages, setMessages] = useState<RagChatMessage[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [currentThinking, setCurrentThinking] = useState<RagThinkingData | null>(null)
  const [currentThinkingContent, setCurrentThinkingContent] = useState('')
  const chatSessionRef = useRef<TSession | null>(null)
  const thinkingContentRef = useRef('')
  const isSendingRef = useRef(false)
  const isDoneCalledRef = useRef(false)
  const isMountedRef = useRef(true)
  const configRef = useRef(config)
  const optionsRef = useRef(options)
  configRef.current = config
  optionsRef.current = options

  const flushAssistantContent = useCallback((content: string) => {
    if (!isMountedRef.current) return
    setMessages(previous => {
      const last = previous[previous.length - 1]
      return !last || last.role !== 'assistant' ? previous
        : [...previous.slice(0, -1), { ...last, content: last.content + content }]
    })
  }, [])
  const { append: appendAssistantContent, flush: flushAssistantContentBuffer, reset: resetAssistantContentBuffer } =
    useRafTextBuffer(flushAssistantContent)

  const flushThinkingContent = useCallback((content: string) => {
    if (!isMountedRef.current) return
    thinkingContentRef.current += content
    setCurrentThinkingContent(thinkingContentRef.current)
    setMessages(previous => {
      const last = previous[previous.length - 1]
      return !last || last.role !== 'assistant' ? previous
        : [...previous.slice(0, -1), { ...last, thinkingContent: thinkingContentRef.current }]
    })
  }, [])
  const { append: appendThinkingContent, flush: flushThinkingContentBuffer, reset: resetThinkingContentBuffer } =
    useRafTextBuffer(flushThinkingContent)

  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
      chatSessionRef.current?.abort()
      isSendingRef.current = false
      resetAssistantContentBuffer()
      resetThinkingContentBuffer()
    }
  }, [resetAssistantContentBuffer, resetThinkingContentBuffer])

  const clearMessages = useCallback(() => {
    chatSessionRef.current?.abort()
    isSendingRef.current = false
    resetAssistantContentBuffer()
    resetThinkingContentBuffer()
    setMessages([])
    setCurrentThinking(null)
    setCurrentThinkingContent('')
    thinkingContentRef.current = ''
    isDoneCalledRef.current = false
    setIsLoading(false)
  }, [resetAssistantContentBuffer, resetThinkingContentBuffer])

  const stopGeneration = useCallback(() => {
    chatSessionRef.current?.abort()
    isSendingRef.current = false
    flushAssistantContentBuffer()
    flushThinkingContentBuffer()
    setIsLoading(false)
    setMessages(previous => {
      const last = previous[previous.length - 1]
      return !last || last.role !== 'assistant' || !last.isStreaming ? previous : [
        ...previous.slice(0, -1), { ...last, isStreaming: false, content: last.content || '生成已停止。' },
      ]
    })
  }, [flushAssistantContentBuffer, flushThinkingContentBuffer])

  const sendMessage = useCallback(async (request: TRequest) => {
    const currentConfig = configRef.current
    const question = currentConfig.getQuestion(request)
    if (!question.trim() || isSendingRef.current) return

    isSendingRef.current = true
    resetAssistantContentBuffer()
    resetThinkingContentBuffer()
    const timestamp = Date.now()
    setMessages(previous => [...previous,
      { id: `user-${timestamp}`, role: 'user', content: question, timestamp: new Date() },
      { id: `assistant-${timestamp}`, role: 'assistant', content: '', timestamp: new Date(), isStreaming: true },
    ])
    setIsLoading(true)
    setCurrentThinking(null)
    setCurrentThinkingContent('')
    thinkingContentRef.current = ''
    isDoneCalledRef.current = false

    if (!chatSessionRef.current) chatSessionRef.current = currentConfig.createSession()
    if (chatSessionRef.current.isActive()) chatSessionRef.current.abort()

    try {
      await currentConfig.startSession(chatSessionRef.current, request, {
        onThinking: data => {
          if (!isMountedRef.current) return
          setCurrentThinking(data)
          setMessages(previous => {
            const last = previous[previous.length - 1]
            if (!last || last.role !== 'assistant') return previous
            const next = { ...last }
            if (data.type === 'retrieval') {
              next.retrieval = data
              next.isRetrievalComplete = data.status === 'end'
            } else if (data.type === 'thinking' || data.type === 'answer') {
              next.thinking = data
            }
            return [...previous.slice(0, -1), next]
          })
        },
        onThinkingContent: content => appendThinkingContent(content),
        onThinkingEnd: () => {
          if (!isMountedRef.current) return
          flushThinkingContentBuffer()
          setMessages(previous => {
            const last = previous[previous.length - 1]
            return !last || last.role !== 'assistant' ? previous
              : [...previous.slice(0, -1), { ...last, isThinkingComplete: true }]
          })
        },
        onContent: content => appendAssistantContent(content),
        onError: error => {
          if (!isMountedRef.current) return
          flushAssistantContentBuffer()
          flushThinkingContentBuffer()
          setMessages(previous => {
            const last = previous[previous.length - 1]
            return !last || last.role !== 'assistant' ? previous : [
              ...previous.slice(0, -1),
              { ...last, content: configRef.current.getErrorMessage(error), isStreaming: false },
            ]
          })
          optionsRef.current.onError?.(error)
        },
        onDone: () => {
          if (!isMountedRef.current || isDoneCalledRef.current) return
          isDoneCalledRef.current = true
          flushAssistantContentBuffer()
          flushThinkingContentBuffer()
          setMessages(previous => {
            const last = previous[previous.length - 1]
            return !last || last.role !== 'assistant' ? previous
              : [...previous.slice(0, -1), { ...last, isStreaming: false }]
          })
          setIsLoading(false)
          optionsRef.current.onDone?.()
        },
      })
    } catch (error) {
      if (isMountedRef.current) optionsRef.current.onError?.(error instanceof Error ? error.message : '发送消息失败')
    } finally {
      isSendingRef.current = false
      if (isMountedRef.current) setIsLoading(false)
    }
  }, [appendAssistantContent, appendThinkingContent, flushAssistantContentBuffer, flushThinkingContentBuffer,
    resetAssistantContentBuffer, resetThinkingContentBuffer])

  return { messages, isLoading, currentThinking, currentThinkingContent, sendMessage, clearMessages, stopGeneration }
}
