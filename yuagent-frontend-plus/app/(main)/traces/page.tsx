"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import {
  Activity,
  AlertCircle,
  MessageSquare,
  Trash2,
  TrendingUp,
  Zap,
} from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import {
  deleteAgentTraceRecordsWithToast,
  getUserAgentTraceStatisticsWithToast,
  type AgentTraceStatistics,
} from "@/lib/agent-trace-service"

export default function TracesPage() {
  const router = useRouter()
  const [agents, setAgents] = useState<AgentTraceStatistics[]>([])
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState("")
  const [searchQuery, setSearchQuery] = useState("")
  const [filteredAgents, setFilteredAgents] = useState<AgentTraceStatistics[]>([])
  const [agentToDelete, setAgentToDelete] = useState<AgentTraceStatistics | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const latestRequestIdRef = useRef(0)

  const loadAgentTraceStatistics = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current
    setLoading(true)
    setErrorMessage("")

    try {
      const response = await getUserAgentTraceStatisticsWithToast()
      if (requestId !== latestRequestIdRef.current) {
        return
      }

      if (response.code === 200) {
        const data = response.data ?? []
        setAgents(data)
        setFilteredAgents(data)
      } else {
        setAgents([])
        setFilteredAgents([])
        setErrorMessage(response.message || "加载执行追踪失败")
      }
    } catch (error) {
      if (requestId === latestRequestIdRef.current) {
        setAgents([])
        setFilteredAgents([])
        setErrorMessage(error instanceof Error ? error.message : "加载执行追踪失败")
      }
    } finally {
      if (requestId === latestRequestIdRef.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void loadAgentTraceStatistics()
  }, [loadAgentTraceStatistics])

  useEffect(() => {
    if (!searchQuery.trim()) {
      setFilteredAgents(agents)
      return
    }

    const filtered = agents.filter((agent) =>
      agent.agentName.toLowerCase().includes(searchQuery.toLowerCase())
    )
    setFilteredAgents(filtered)
  }, [searchQuery, agents])

  const formatNumber = (num: number) => {
    if (num >= 1000) {
      return `${(num / 1000).toFixed(1)}K`
    }
    return num.toString()
  }

  const formatSuccessRate = (rate: number) => `${(rate * 100).toFixed(1)}%`

  const formatTime = (timeStr: string) => new Date(timeStr).toLocaleString("zh-CN")

  const getSuccessRateColor = (rate: number) => {
    if (rate >= 0.9) return "text-green-600"
    if (rate >= 0.7) return "text-yellow-600"
    return "text-red-600"
  }

  const handleDeleteAgentTrace = async () => {
    if (!agentToDelete) return

    try {
      setIsDeleting(true)
      const response = await deleteAgentTraceRecordsWithToast(agentToDelete.agentId)
      if (response.code === 200) {
        setAgents((prev) => prev.filter((agent) => agent.agentId !== agentToDelete.agentId))
        setFilteredAgents((prev) => prev.filter((agent) => agent.agentId !== agentToDelete.agentId))
        setAgentToDelete(null)
      }
    } finally {
      setIsDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="container mx-auto p-6">
        <div className="mb-6">
          <h1 className="mb-2 text-3xl font-bold">执行追踪</h1>
          <p className="text-muted-foreground">查看您的 Agent 执行历史和性能统计</p>
        </div>

        <div className="mb-6">
          <Skeleton className="h-10 w-80" />
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Card key={i}>
              <CardHeader>
                <Skeleton className="h-6 w-32" />
                <Skeleton className="h-4 w-24" />
              </CardHeader>
              <CardContent className="space-y-4">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    )
  }

  if (errorMessage) {
    return (
      <div className="container mx-auto p-6">
        <div className="mb-6">
          <h1 className="mb-2 text-3xl font-bold">执行追踪</h1>
          <p className="text-muted-foreground">查看您的 Agent 执行历史和性能统计</p>
        </div>

        <div className="flex flex-col items-center justify-center py-16 text-center">
          <AlertCircle className="mb-4 h-16 w-16 text-destructive" />
          <h2 className="mb-2 text-xl font-semibold">加载失败</h2>
          <p className="max-w-md text-muted-foreground">{errorMessage}</p>
          <Button className="mt-6" onClick={() => void loadAgentTraceStatistics()}>
            重试
          </Button>
        </div>
      </div>
    )
  }

  if (agents.length === 0) {
    return (
      <div className="container mx-auto p-6">
        <div className="mb-6">
          <h1 className="mb-2 text-3xl font-bold">执行追踪</h1>
          <p className="text-muted-foreground">查看您的 Agent 执行历史和性能统计</p>
        </div>

        <div className="flex flex-col items-center justify-center py-16">
          <Activity className="mb-4 h-16 w-16 text-muted-foreground" />
          <h2 className="mb-2 text-xl font-semibold">暂无执行记录</h2>
          <p className="max-w-md text-center text-muted-foreground">
            您的 Agent 还没有执行记录。开始与 Agent 对话后，执行追踪信息将会在这里显示。
          </p>
          <Link href="/studio">
            <Button className="mt-4">前往工作室</Button>
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto p-6">
      <div className="mb-6">
        <h1 className="mb-2 text-3xl font-bold">执行追踪</h1>
        <p className="text-muted-foreground">查看您的 Agent 执行历史和性能统计</p>
      </div>

      <div className="mb-6">
        <Input
          placeholder="搜索 Agent..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="max-w-md"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {filteredAgents.map((agent) => (
          <Card
            key={agent.agentId}
            className="h-full cursor-pointer transition-shadow hover:shadow-lg"
            onClick={() => router.push(`/traces/agents/${agent.agentId}`)}
          >
            <CardHeader>
              <CardTitle className="flex items-center justify-between gap-2">
                <span className="truncate">{agent.agentName}</span>
                <div className="flex items-center gap-2">
                  <Badge variant={agent.lastExecutionSuccess ? "default" : "destructive"}>
                    {agent.lastExecutionSuccess ? "正常" : "异常"}
                  </Badge>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-red-500 hover:bg-red-500/10 hover:text-red-400"
                    onClick={(e) => {
                      e.stopPropagation()
                      setAgentToDelete(agent)
                    }}
                    aria-label={`删除${agent.agentName}的追踪记录`}
                    title="删除追踪记录"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </CardTitle>
              <p className="text-sm text-muted-foreground">最后执行 {formatTime(agent.lastExecutionTime)}</p>
            </CardHeader>

            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center space-x-2">
                  <Activity className="h-4 w-4 text-blue-600" />
                  <div>
                    <div className="text-sm font-medium">{formatNumber(agent.totalExecutions)}</div>
                    <div className="text-xs text-muted-foreground">总执行</div>
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  <TrendingUp className={`h-4 w-4 ${getSuccessRateColor(agent.successRate)}`} />
                  <div>
                    <div className={`text-sm font-medium ${getSuccessRateColor(agent.successRate)}`}>
                      {formatSuccessRate(agent.successRate)}
                    </div>
                    <div className="text-xs text-muted-foreground">成功率</div>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center space-x-2">
                  <Zap className="h-4 w-4 text-green-600" />
                  <div>
                    <div className="text-sm font-medium">{formatNumber(agent.totalTokens)}</div>
                    <div className="text-xs text-muted-foreground">Token 数</div>
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  <MessageSquare className="h-4 w-4 text-sky-600" />
                  <div>
                    <div className="text-sm font-medium">{agent.totalSessions}</div>
                    <div className="text-xs text-muted-foreground">会话数</div>
                  </div>
                </div>
              </div>

              {agent.totalToolCalls > 0 && (
                <div className="border-t pt-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">工具调用</span>
                    <span className="font-medium">{formatNumber(agent.totalToolCalls)} 次</span>
                  </div>
                </div>
              )}

              {agent.failedExecutions > 0 && (
                <div className="flex items-center space-x-2 text-orange-600">
                  <AlertCircle className="h-4 w-4" />
                  <span className="text-sm">{agent.failedExecutions} 次执行失败</span>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {filteredAgents.length === 0 && searchQuery && (
        <div className="flex flex-col items-center justify-center py-16">
          <Activity className="mb-4 h-16 w-16 text-muted-foreground" />
          <h2 className="mb-2 text-xl font-semibold">未找到相关 Agent</h2>
          <p className="text-muted-foreground">尝试使用其他关键词搜索</p>
        </div>
      )}

      <AlertDialog open={!!agentToDelete} onOpenChange={(open) => !open && setAgentToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除追踪记录</AlertDialogTitle>
            <AlertDialogDescription>
              将删除助理“{agentToDelete?.agentName}”的全部执行追踪记录。此操作无法撤销，但不会影响助理本身。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault()
                void handleDeleteAgentTrace()
              }}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={isDeleting}
            >
              {isDeleting ? "删除中..." : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
