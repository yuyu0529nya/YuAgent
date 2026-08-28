"use client"

import React, { use, useEffect, useRef, useState } from 'react'
import dynamic from 'next/dynamic'
import { AgentSidebar } from "@/components/agent-sidebar"
import { getAgentSessionsWithToast, type SessionDTO } from "@/lib/agent-session-service"
import { getAgentBySessionIdWithToast } from "@/lib/agent-service"
import type { Agent } from "@/types/agent"

const ChatPanel = dynamic(() => import("@/components/chat-panel").then(module => module.ChatPanel), {
  ssr: false,
  loading: () => <div className="flex-1 animate-pulse bg-muted/20" aria-label="正在加载对话" />,
})

export default function ChatPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const [currentAgent, setCurrentAgent] = useState<Agent | null>(null)
  const [sessions, setSessions] = useState<SessionDTO[]>([])
  const latestRequestRef = useRef(0)

  // 通过会话ID获取Agent信息
  useEffect(() => {
    const requestId = ++latestRequestRef.current
    const fetchData = async () => {
      setCurrentAgent(null)
      setSessions([])

      const agentResponse = await getAgentBySessionIdWithToast(id)
      if (requestId !== latestRequestRef.current || agentResponse.code !== 200 || !agentResponse.data) {
        return
      }

      setCurrentAgent(agentResponse.data)

      // 获取该 Agent 的所有会话来找到当前会话的多模态设置。
      const sessionsResponse = await getAgentSessionsWithToast(agentResponse.data.id)
      if (requestId === latestRequestRef.current && sessionsResponse.code === 200 && sessionsResponse.data) {
        setSessions(sessionsResponse.data)
      }
    }

    void fetchData()

    return () => {
      if (requestId === latestRequestRef.current) {
        latestRequestRef.current += 1
      }
    }
  }, [id])

  // 获取当前会话的多模态设置
  const currentSession = sessions.find(session => session.id === id)
  const multiModal = currentSession?.multiModal || false
  
  return (
    <div className="flex h-[calc(100vh-3.5rem)] w-full">
      {/* 左侧边栏 */}
      <AgentSidebar />

      {/* 右侧聊天面板 */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <ChatPanel 
          conversationId={id} 
          agentName={currentAgent?.name || "AI助手"}
          multiModal={multiModal}
        />
      </div>
    </div>
  )
}

