import { useCallback } from 'react'
import { RagChatSession } from '@/lib/rag-chat-service'
import type { RagStreamChatRequest } from '@/types/rag-dataset'
import {
  useManagedRagChatSession,
  type RagChatMessage,
  type UseRagChatSessionOptions,
} from './useManagedRagChatSession'

export type Message = RagChatMessage

export function useRagChatSession(options: UseRagChatSessionOptions = {}) {
  const { sendMessage: sendRequest, ...session } = useManagedRagChatSession({
    createSession: () => new RagChatSession(),
    startSession: (chatSession, request: RagStreamChatRequest, callbacks) => chatSession.start(request, callbacks),
    getQuestion: request => request.question,
    getErrorMessage: () => '抱歉，处理您的请求时出现了错误。请重试。',
  }, options)

  const sendMessage = useCallback((question: string, datasetIds: string[]) => sendRequest({ question, datasetIds }),
    [sendRequest])

  return { ...session, sendMessage }
}
