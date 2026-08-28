"use client"

import { useEffect, useRef, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import { AlertCircle, Archive, ArrowLeft, Clock, MessageSquare, TrendingUp, Zap } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  getAgentSessionTraceStatisticsWithToast,
  type SessionTraceStatistics,
} from "@/lib/agent-trace-service"

const DELETED_AGENT_NAME = "已删除助理"

export default function AgentSessionsPage() {
  const params = useParams()
  const router = useRouter()
  const agentId = params.agentId as string

  const [sessions, setSessions] = useState<SessionTraceStatistics[]>([])
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState("")
  const [searchQuery, setSearchQuery] = useState("")
  const [showArchived, setShowArchived] = useState(false)
  const [filteredSessions, setFilteredSessions] = useState<SessionTraceStatistics[]>([])
  const [agentName, setAgentName] = useState(DELETED_AGENT_NAME)
  const [refreshToken, setRefreshToken] = useState(0)
  const latestRequestIdRef = useRef(0)

  useEffect(() => {
    async function loadSessionTraceStatistics() {
      if (!agentId) return

      const requestId = ++latestRequestIdRef.current

      try {
        setLoading(true)
        setErrorMessage("")

        const response = await getAgentSessionTraceStatisticsWithToast(agentId, {
          includeArchived: true,
        })

        if (requestId !== latestRequestIdRef.current) {
          return
        }

        if (response.code !== 200) {
          setSessions([])
          setFilteredSessions([])
          setErrorMessage(response.message || "加载会话追踪失败")
          return
        }

        const data = response.data || []
        setSessions(data)
        setFilteredSessions(data)

        if (data.length > 0 && data[0].agentName) {
          setAgentName(data[0].agentName)
        } else {
          setAgentName(DELETED_AGENT_NAME)
        }
      } catch (error) {
        if (requestId !== latestRequestIdRef.current) {
          return
        }

        setSessions([])
        setFilteredSessions([])
        setErrorMessage(error instanceof Error ? error.message : "加载会话追踪失败")
      } finally {
        if (requestId === latestRequestIdRef.current) {
          setLoading(false)
        }
      }
    }

    void loadSessionTraceStatistics()
    return () => {
      latestRequestIdRef.current += 1
    }
  }, [agentId, refreshToken])

  useEffect(() => {
    let filtered = sessions

    if (!showArchived) {
      filtered = filtered.filter(session => !session.isArchived)
    }

    if (searchQuery.trim()) {
      const keyword = searchQuery.toLowerCase()
      filtered = filtered.filter(session =>
        (session.sessionTitle || "").toLowerCase().includes(keyword)
      )
    }

    setFilteredSessions(filtered)
  }, [searchQuery, showArchived, sessions])

  const formatNumber = (num: number) => {
    if (num >= 1000) {
      return (num / 1000).toFixed(1) + "K"
    }
    return num.toString()
  }

  const formatSuccessRate = (rate: number) => `${(rate * 100).toFixed(1)}%`

  const formatTime = (timeStr: string) => new Date(timeStr).toLocaleString("zh-CN")

  const formatExecutionTime = (timeMs: number) => {
    if (timeMs < 1000) {
      return `${timeMs}ms`
    }
    return `${(timeMs / 1000).toFixed(1)}s`
  }

  const getSuccessRateColor = (rate: number) => {
    if (rate >= 0.9) return "text-green-600"
    if (rate >= 0.7) return "text-yellow-600"
    return "text-red-600"
  }

  if (loading) {
    return (
      <div className="container mx-auto p-6">
        <div className="mb-6">
          <Skeleton className="mb-4 h-10 w-32" />
          <Skeleton className="mb-2 h-8 w-48" />
          <Skeleton className="h-4 w-64" />
        </div>

        <div className="mb-6 space-y-4">
          <Skeleton className="h-10 w-80" />
          <Skeleton className="h-5 w-32" />
        </div>

        <div className="space-y-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      </div>
    )
  }

  if (errorMessage) {
    return (
      <div className="container mx-auto p-6">
        <div className="mb-6">
          <Button variant="ghost" onClick={() => router.back()} className="mb-4">
            <ArrowLeft className="mr-2 h-4 w-4" />
            返回
          </Button>
          <h1 className="mb-2 text-3xl font-bold">{agentName} 会话追踪</h1>
          <p className="text-muted-foreground">该助理的历史会话暂时无法加载。</p>
        </div>

        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 text-center">
            <AlertCircle className="mb-4 h-16 w-16 text-destructive" />
            <h2 className="mb-2 text-xl font-semibold">加载失败</h2>
            <p className="max-w-md text-muted-foreground">{errorMessage}</p>
            <div className="mt-6 flex gap-3">
              <Button variant="outline" onClick={() => router.back()}>
                返回上一页
              </Button>
              <Button onClick={() => setRefreshToken((token) => token + 1)}>
                重试
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (sessions.length === 0) {
    return (
      <div className="container mx-auto p-6">
        <div className="mb-6">
          <Button variant="ghost" onClick={() => router.back()} className="mb-4">
            <ArrowLeft className="mr-2 h-4 w-4" />
            返回
          </Button>
          <h1 className="mb-2 text-3xl font-bold">{agentName} 会话追踪</h1>
          <p className="text-muted-foreground">查看该 Agent 的所有会话执行统计</p>
        </div>

        <div className="flex flex-col items-center justify-center py-16">
          <MessageSquare className="mb-4 h-16 w-16 text-muted-foreground" />
          <h2 className="mb-2 text-xl font-semibold">暂无会话记录</h2>
          <p className="max-w-md text-center text-muted-foreground">
            这个助理当前没有可展示的历史会话。
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto p-6">
      <div className="mb-6">
        <Button variant="ghost" onClick={() => router.back()} className="mb-4">
          <ArrowLeft className="mr-2 h-4 w-4" />
          返回
        </Button>
        <h1 className="mb-2 text-3xl font-bold">{agentName} 会话追踪</h1>
        <p className="text-muted-foreground">查看该 Agent 的所有会话执行统计</p>
      </div>

      <div className="mb-6 space-y-4">
        <Input
          placeholder="搜索会话..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="max-w-md"
        />

        <div className="flex items-center space-x-2">
          <Checkbox
            id="show-archived"
            checked={showArchived}
            onCheckedChange={(checked) => setShowArchived(Boolean(checked))}
          />
          <label htmlFor="show-archived" className="text-sm">
            显示已归档会话
          </label>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>会话列表 ({filteredSessions.length})</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>会话名称</TableHead>
                <TableHead>执行次数</TableHead>
                <TableHead>成功率</TableHead>
                <TableHead>Token 数</TableHead>
                <TableHead>工具调用</TableHead>
                <TableHead>执行时间</TableHead>
                <TableHead>最后执行</TableHead>
                <TableHead>状态</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredSessions.map((session) => (
                <TableRow
                  key={session.sessionId}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => router.push(`/traces/agents/${agentId}/sessions/${session.sessionId}`)}
                >
                  <TableCell>
                    <div className="flex items-center space-x-2">
                      {session.isArchived && (
                        <Archive className="h-4 w-4 text-muted-foreground" />
                      )}
                      <span className={session.isArchived ? "text-muted-foreground" : ""}>
                        {session.sessionTitle}
                      </span>
                    </div>
                  </TableCell>

                  <TableCell>
                    <div className="flex items-center space-x-1">
                      <MessageSquare className="h-4 w-4 text-blue-600" />
                      <span>{formatNumber(session.totalExecutions)}</span>
                    </div>
                  </TableCell>

                  <TableCell>
                    <div className="flex items-center space-x-1">
                      <TrendingUp className={`h-4 w-4 ${getSuccessRateColor(session.successRate)}`} />
                      <span className={getSuccessRateColor(session.successRate)}>
                        {formatSuccessRate(session.successRate)}
                      </span>
                    </div>
                  </TableCell>

                  <TableCell>
                    <div className="flex items-center space-x-1">
                      <Zap className="h-4 w-4 text-green-600" />
                      <span>{formatNumber(session.totalTokens)}</span>
                    </div>
                  </TableCell>

                  <TableCell>
                    {session.totalToolCalls > 0 ? (
                      <span>{formatNumber(session.totalToolCalls)} 次</span>
                    ) : (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </TableCell>

                  <TableCell>
                    <div className="flex items-center space-x-1">
                      <Clock className="h-4 w-4 text-gray-600" />
                      <span>{formatExecutionTime(session.totalExecutionTime)}</span>
                    </div>
                  </TableCell>

                  <TableCell className="text-sm">
                    {formatTime(session.lastExecutionTime)}
                  </TableCell>

                  <TableCell>
                    <div className="flex items-center space-x-2">
                      <Badge variant={session.lastExecutionSuccess ? "default" : "destructive"}>
                        {session.lastExecutionSuccess ? "正常" : "异常"}
                      </Badge>
                      {session.failedExecutions > 0 && (
                        <div className="flex items-center space-x-1 text-orange-600">
                          <AlertCircle className="h-3 w-3" />
                          <span className="text-xs">{session.failedExecutions}</span>
                        </div>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {filteredSessions.length === 0 && (searchQuery || !showArchived) && (
            <div className="flex flex-col items-center justify-center py-8">
              <MessageSquare className="mb-4 h-12 w-12 text-muted-foreground" />
              <h3 className="mb-2 text-lg font-medium">未找到相关会话</h3>
              <p className="text-muted-foreground">
                {searchQuery ? "尝试使用其他关键词搜索" : "尝试显示已归档会话"}
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
