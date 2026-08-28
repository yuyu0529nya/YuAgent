import { useCallback } from 'react'
import { UserRagChatSession } from '@/lib/rag-chat-service'
import {
  useManagedRagChatSession,
  type RagChatMessage,
  type UseRagChatSessionOptions,
} from './useManagedRagChatSession'

export type Message = RagChatMessage

interface UserRagChatRequest {
  question: string
  userRagId: string
}

export function useUserRagChatSession(options: UseRagChatSessionOptions = {}) {
  const { sendMessage: sendRequest, ...session } = useManagedRagChatSession({
    createSession: () => new UserRagChatSession(),
    startSession: (chatSession, request: UserRagChatRequest, callbacks) => chatSession.start(request.userRagId,
      { question: request.question }, callbacks),
    getQuestion: request => request.question,
    getErrorMessage: error => error || '抱歉，处理您的请求时出现了错误。请重试。',
  }, options)

  const sendMessage = useCallback((question: string, userRagId: string) => {
    if (!userRagId) return Promise.resolve()
    return sendRequest({ question, userRagId })
  }, [sendRequest])

  return { ...session, sendMessage }
}
