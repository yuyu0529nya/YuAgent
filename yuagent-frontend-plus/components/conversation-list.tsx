"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { ChevronLeft, ChevronRight, Edit, MoreHorizontal, Plus, Trash2 } from "lucide-react"
import { useWorkspace } from "@/contexts/workspace-context"
import {
  getAgentSessionsWithToast,
  createAgentSessionWithToast,
  updateAgentSessionWithToast,
  deleteAgentSessionWithToast,
  type SessionDTO,
} from "@/lib/agent-session-service"
import { toast } from "@/hooks/use-toast"

interface ConversationListProps {
  workspaceId: string
}

export function ConversationList({ workspaceId }: ConversationListProps) {
  const { selectedConversationId, setSelectedConversationId } = useWorkspace()
  const [sessions, setSessions] = useState<SessionDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [isRenameDialogOpen, setIsRenameDialogOpen] = useState(false)
  const [sessionToRename, setSessionToRename] = useState<SessionDTO | null>(null)
  const [renameTitle, setRenameTitle] = useState("")
  const [sessionToDelete, setSessionToDelete] = useState<string | null>(null)
  const [isDeletingSession, setIsDeletingSession] = useState(false)
  const [searchText, setSearchText] = useState("")
  const [isCollapsed, setIsCollapsed] = useState(false)

  const fetchSessions = async () => {
    try {
      setLoading(true)
      const response = await getAgentSessionsWithToast(workspaceId)
      if (response.code === 200) {
        setSessions(response.data)
        if (response.data.length > 0 && !selectedConversationId) {
          setSelectedConversationId(response.data[0].id)
        }
      }
    } finally {
      setLoading(false)
    }
  }

  const selectConversation = (sessionId: string) => {
    setSelectedConversationId(sessionId)
  }

  const handleDeleteSession = (sessionId: string) => {
    setSessionToDelete(sessionId)
  }

  const confirmDeleteSession = async () => {
    if (!sessionToDelete) return

    try {
      setIsDeletingSession(true)
      const response = await deleteAgentSessionWithToast(sessionToDelete)

      if (response.code === 200) {
        await fetchSessions()
        if (selectedConversationId === sessionToDelete) {
          setSelectedConversationId(null)
        }
        toast({
          title: "删除成功",
          description: "会话已删除",
        })
      }
    } finally {
      setIsDeletingSession(false)
      setSessionToDelete(null)
    }
  }

  const openRenameDialog = (session: SessionDTO) => {
    setSessionToRename(session)
    setRenameTitle(session.title)
    setIsRenameDialogOpen(true)
  }

  const handleRenameSession = async () => {
    if (!sessionToRename) return

    if (!renameTitle.trim()) {
      toast({
        description: "会话标题不能为空",
        variant: "destructive",
      })
      return
    }

    const response = await updateAgentSessionWithToast(sessionToRename.id, renameTitle)
    if (response.code === 200) {
      await fetchSessions()
      setIsRenameDialogOpen(false)
      setSessionToRename(null)
    }
  }

  useEffect(() => {
    fetchSessions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId])

  const handleQuickCreateSession = async () => {
    const response = await createAgentSessionWithToast(workspaceId)
    if (response.code === 200) {
      const defaultTitle = "新会话"
      const updateResponse = await updateAgentSessionWithToast(response.data.id, defaultTitle)
      if (updateResponse.code === 200) {
        await fetchSessions()
        setSelectedConversationId(response.data.id)
      }
    }
  }

  const visibleSessions = sessions.filter((session) =>
    session.title.toLowerCase().includes(searchText.toLowerCase()),
  )

  return (
    <div
      className={`border-r border-white/10 flex flex-col h-full bg-transparent backdrop-blur-md transition-all duration-300 ${
        isCollapsed ? "w-[40px]" : "w-[320px]"
      }`}
    >
      <div className={`${isCollapsed ? "py-4 px-0" : "p-4"} border-b border-white/10 flex items-center relative ${isCollapsed ? "h-full" : ""}`}>
        {!isCollapsed && (
          <>
            <h2 className="text-lg font-semibold text-slate-100">会话列表</h2>
            <div className="absolute right-[65px]">
              <Button size="icon" variant="ghost" onClick={handleQuickCreateSession} className="text-slate-200 hover:bg-white/10">
                <Plus className="h-4 w-4" />
                <span className="sr-only">新建会话</span>
              </Button>
            </div>
          </>
        )}

        <div
          className={`absolute ${isCollapsed ? "w-full h-12" : "w-12 border-l border-white/10 h-full"} right-0 top-0 flex items-center justify-center cursor-pointer hover:bg-white/5`}
          onClick={() => setIsCollapsed(!isCollapsed)}
        >
          {isCollapsed ? <ChevronRight className="h-4 w-4 text-slate-200" /> : <ChevronLeft className="h-4 w-4 text-slate-200" />}
        </div>
      </div>

      {!isCollapsed && (
        <>
          <div className="relative p-4">
            <Input
              placeholder="搜索会话..."
              className="pl-8 bg-[#0a0f24]/80 border-white/15 text-slate-100 placeholder:text-slate-400"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
          </div>

          <ScrollArea className="flex-1">
            <div className="p-2 space-y-1">
              {loading ? (
                Array.from({ length: 5 }).map((_, index) => (
                  <div key={index} className="flex items-center gap-3 rounded-lg px-3 py-2 mb-2">
                    <Skeleton className="h-9 w-9 rounded-full" />
                    <div className="space-y-1 flex-1">
                      <Skeleton className="h-4 w-3/4" />
                      <Skeleton className="h-3 w-1/2" />
                    </div>
                  </div>
                ))
              ) : visibleSessions.length > 0 ? (
                visibleSessions.map((session) => (
                  <div
                    key={session.id}
                    className={`relative mb-1 flex cursor-pointer items-center justify-between rounded-lg border px-3 py-3 transition-all duration-200 ${
                      selectedConversationId === session.id
                        ? "border-fuchsia-400/50 bg-gradient-to-r from-[#47378f]/70 via-[#362b75]/65 to-[#7a2c68]/60 shadow-[0_0_0_1px_rgba(168,85,247,0.18)]"
                        : "border-transparent bg-transparent hover:border-white/10 hover:bg-white/5"
                    }`}
                    onClick={() => selectConversation(session.id)}
                  >
                    <div className="flex min-w-0 flex-1 items-center justify-between">
                      <div className="min-w-0 mr-2">
                        <div className="truncate font-medium text-slate-100">{session.title}</div>
                        <div className="text-xs text-slate-300/80">{new Date(session.createdAt).toLocaleString()}</div>
                      </div>

                      <div className="inline-block">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8 min-w-8 shrink-0 text-slate-200 hover:bg-white/10"
                              onClick={(e) => {
                                e.stopPropagation()
                                e.preventDefault()
                              }}
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              onClick={(e) => {
                                e.stopPropagation()
                                e.preventDefault()
                                openRenameDialog(session)
                              }}
                            >
                              <Edit className="mr-2 h-4 w-4" />
                              重命名
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              className="text-red-500"
                              onClick={(e) => {
                                e.stopPropagation()
                                e.preventDefault()
                                handleDeleteSession(session.id)
                              }}
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              删除
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-8 text-slate-400">暂无会话</div>
              )}
            </div>
          </ScrollArea>
        </>
      )}

      <Dialog open={!!sessionToDelete} onOpenChange={(open) => !open && setSessionToDelete(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>删除会话</DialogTitle>
            <DialogDescription>确定要删除这个会话吗？此操作无法撤销。</DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button variant="outline" onClick={() => setSessionToDelete(null)} disabled={isDeletingSession}>
              取消
            </Button>
            <Button variant="destructive" onClick={confirmDeleteSession} disabled={isDeletingSession}>
              {isDeletingSession ? "删除中..." : "删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isRenameDialogOpen} onOpenChange={setIsRenameDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>重命名会话</DialogTitle>
            <DialogDescription>为这个会话设置一个新标题。</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="name">会话标题</Label>
              <Input
                id="name"
                value={renameTitle}
                onChange={(e) => setRenameTitle(e.target.value)}
                placeholder="输入新的标题..."
              />
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => setIsRenameDialogOpen(false)} variant="outline">
              取消
            </Button>
            <Button onClick={handleRenameSession}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
