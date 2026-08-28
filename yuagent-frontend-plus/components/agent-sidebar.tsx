"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Bot, Plus, RefreshCw, Search } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { getUserSessionsWithToast, type SessionDTO } from "@/lib/agent-session-service"
import { cn } from "@/lib/utils"

function toConversation(session: SessionDTO) {
  return {
    id: session.id,
    name: session.title,
    avatar: session.title.charAt(0).toUpperCase(),
    lastMessage: session.description || "有什么可以帮您的？",
  }
}

export function AgentSidebar() {
  const pathname = usePathname()
  const [searchQuery, setSearchQuery] = useState("")
  const [conversations, setConversations] = useState<ReturnType<typeof toConversation>[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryCount, setRetryCount] = useState(0)
  const latestRequestIdRef = useRef(0)

  const fetchConversations = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current
    try {
      setLoading(true)
      setError(null)
      const response = await getUserSessionsWithToast()
      if (requestId !== latestRequestIdRef.current) {
        return
      }

      if (response.code === 200) {
        setConversations(response.data.filter((session) => !session.archived).map(toConversation))
      } else {
        setError(response.message || "获取会话列表失败")
      }
    } catch (fetchError) {
      if (requestId !== latestRequestIdRef.current) {
        return
      }

      const message = fetchError instanceof Error ? fetchError.message : "未知错误"
      setError(`获取会话列表失败: ${message}`)
    } finally {
      if (requestId === latestRequestIdRef.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void fetchConversations()
    return () => {
      latestRequestIdRef.current += 1
    }
  }, [fetchConversations, retryCount])

  const visibleConversations = conversations.filter((conversation) =>
    conversation.name.toLowerCase().includes(searchQuery.toLowerCase()),
  )

  return (
    <div className="flex h-[calc(100vh-3.5rem)] w-[300px] flex-col border-r">
      <div className="border-b p-4">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">我的会话</h2>
          <Button size="icon" variant="ghost" asChild>
            <Link href="/studio/new">
              <Plus className="h-4 w-4" />
              <span className="sr-only">创建新助理</span>
            </Link>
          </Button>
        </div>
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            type="search"
            placeholder="搜索会话..."
            className="pl-8"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>
      </div>
      <ScrollArea className="flex-1">
        <div className="p-2">
          {loading ? (
            Array.from({ length: 5 }).map((_, index) => (
              <div key={index} className="mb-2 flex gap-3 rounded-lg px-3 py-2">
                <Skeleton className="h-9 w-9 rounded-full" />
                <div className="flex-1 space-y-1">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            ))
          ) : error ? (
            <div className="py-8 text-center">
              <div className="mb-2 text-red-500">{error}</div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setRetryCount((count) => count + 1)}
                className="flex items-center gap-1"
              >
                <RefreshCw className="h-4 w-4" />
                重试
              </Button>
            </div>
          ) : visibleConversations.length > 0 ? (
            visibleConversations.map((conversation) => (
              <Link
                key={conversation.id}
                href={`/explore/chat/${conversation.id}`}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors hover:bg-accent",
                  pathname === `/explore/chat/${conversation.id}` ? "bg-accent" : "transparent",
                )}
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
                  {conversation.avatar}
                </div>
                <div className="flex-1 overflow-hidden">
                  <div className="font-medium">{conversation.name}</div>
                  <div className="truncate text-xs text-muted-foreground">{conversation.lastMessage}</div>
                </div>
              </Link>
            ))
          ) : (
            <div className="py-8 text-center text-muted-foreground">
              {searchQuery ? "没有找到匹配的会话" : "暂无会话"}
            </div>
          )}
        </div>
      </ScrollArea>
      <div className="border-t p-4">
        <Button className="w-full" asChild>
          <Link href="/studio/new">
            <Bot className="mr-2 h-4 w-4" />
            创建新助理
          </Link>
        </Button>
      </div>
    </div>
  )
}
