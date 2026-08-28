"use client"

import { useState, useEffect } from "react"
import Link from "next/link"
import { Search, X, Plus, Wrench, Download, Info, User, ArrowLeft } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardHeader, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"

import { Tool } from "@/types/tool"
import { toMarketTool } from "@/lib/market-tool-mapper"
import { getMarketTools } from "@/lib/tool-service"
import { InstallToolDialog } from "@/components/tool/install-tool-dialog"

export default function ToolsMarketPage() {
  // 工具市场状态
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [debouncedQuery, setDebouncedQuery] = useState("")
  const [tools, setTools] = useState<Tool[]>([])
  const [selectedTool, setSelectedTool] = useState<Tool | null>(null)
  const [isInstallDialogOpen, setIsInstallDialogOpen] = useState(false)
  const [refreshToken, setRefreshToken] = useState(0)
  
  // 防抖处理搜索查询
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchQuery)
    }, 500)

    return () => clearTimeout(timer)
  }, [searchQuery])

  // 获取工具市场列表
  useEffect(() => {
    const controller = new AbortController()
    let active = true

    async function fetchTools() {
      try {
        setLoading(true)
        setError(null)

        const response = await getMarketTools({
          toolName: debouncedQuery,
          page: 1,
          pageSize: 15,
        }, { signal: controller.signal })

        if (!active) {
          return
        }

        if (response.code === 200) {
          const marketTools = response.data.records.map(toMarketTool);
          setTools(marketTools);
        } else {
          setError(response.message)
        }
      } catch (error) {
        if (!active) {
          return
        }
        const errorMessage = error instanceof Error ? error.message : "未知错误"
        setError(errorMessage)
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    fetchTools()

    return () => {
      active = false
      controller.abort()
    }
  }, [debouncedQuery, refreshToken])

  // 清除搜索
  const clearSearch = () => {
    setSearchQuery("")
  }

  // 处理安装工具
  const handleInstallTool = (tool: Tool) => {
    setSelectedTool(tool)
    setIsInstallDialogOpen(true)
  }
  
  // 处理安装成功
  const handleInstallSuccess = () => {
    // 安装成功后，更新工具状态而不是刷新整个页面
    if (selectedTool) {
      // 标记为已安装
      const updatedTools = tools.map(t => {
        if (t.id === selectedTool.id || t.toolId === selectedTool.toolId) {
          return { ...t, isInstalled: true }
        }
        return t
      })
      setTools(updatedTools)
      
      // 选择性地从页面中移除已安装的工具
      // setTools(tools.filter(t => t.id !== selectedTool.id && t.toolId !== selectedTool.toolId))
    }
  }

  return (
    <div className="container py-6">
      {/* 页面头部 */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <Button variant="ghost" size="sm" asChild className="mb-2">
            <Link href="/tools">
              <ArrowLeft className="mr-2 h-4 w-4" />
              返回我的工具
            </Link>
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">工具市场</h1>
          <p className="text-muted-foreground">发现和安装新的AI助手扩展能力</p>
        </div>
      </div>
      
      {/* 搜索区域 */}
      <div className="mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input 
            type="search" 
            placeholder="搜索工具..." 
            className="pl-10 pr-10" 
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <Button 
              variant="ghost" 
              size="icon" 
              className="absolute right-1 top-1/2 -translate-y-1/2 h-7 w-7"
              onClick={clearSearch}
            >
              <X className="h-4 w-4" />
              <span className="sr-only">清除搜索</span>
            </Button>
          )}
        </div>
      </div>

      
      
      {loading ? (
        // 加载状态
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {Array.from({ length: 8 }).map((_, index) => (
            <Card key={index} className="overflow-hidden">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-10 w-10 rounded-md" />
                    <div>
                      <Skeleton className="h-5 w-32 mb-1" />
                      <Skeleton className="h-4 w-24" />
                    </div>
                  </div>
                  <Skeleton className="h-6 w-12 rounded-full" />
                </div>
              </CardHeader>
              <CardContent>
                <div className="mb-2 flex flex-wrap gap-1">
                  <Skeleton className="h-5 w-16 rounded-full" />
                  <Skeleton className="h-5 w-20 rounded-full" />
                  <Skeleton className="h-5 w-14 rounded-full" />
                </div>
                <div className="flex items-center justify-between">
                  <Skeleton className="h-4 w-24" />
                  <Skeleton className="h-4 w-20" />
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : error ? (
        // 错误状态
        <div className="text-center py-10">
          <div className="text-red-500 mb-4">{error}</div>
          <Button variant="outline" onClick={() => setRefreshToken((token) => token + 1)}>
            重试
          </Button>
        </div>
      ) : tools.length === 0 ? (
        // 空状态
        <div className="text-center py-16 border rounded-lg bg-gray-50">
          <Wrench className="h-12 w-12 mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-medium mb-2">
            {searchQuery ? "未找到匹配的工具" : "还没有工具"}
          </h3>
          <p className="text-muted-foreground mb-6">
            {searchQuery 
              ? "尝试使用不同的搜索词" 
              : "上传一个新工具或等待更多工具发布"}
          </p>
          <Button asChild>
            <Link href="/tools/upload">
              <Plus className="mr-2 h-4 w-4" />
              上传工具
            </Link>
          </Button>
        </div>
      ) : (
        // 工具列表
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {tools.map((tool) => (
            <Card key={tool.id} className="group relative overflow-hidden">
              {tool.is_office && (
                <div className="absolute top-2 right-2 z-10">
                  <Badge className="flex items-center gap-1">
                    官方
                  </Badge>
                </div>
              )}
              <CardHeader className="pb-2">
                <div className="flex items-center gap-2">
                  <div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary/10 text-primary-foreground overflow-hidden">
                    {tool.icon ? (
                      <img src={tool.icon} alt={tool.name} className="h-full w-full object-cover" />
                    ) : (
                      <Wrench className="h-5 w-5" />
                    )}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold">{tool.name}</h3>
                    </div>
                    <p className="text-sm text-muted-foreground">{tool.subtitle}</p>
                  </div>
                </div>
              </CardHeader>
              
              <CardContent>
                <div className="mb-2 flex flex-wrap gap-1">
                  {tool.labels.slice(0, 3).filter(label => label !== "官方").map((label, i) => (
                    <Badge key={i} variant="outline" className="text-xs">
                      {label}
                    </Badge>
                  ))}
                </div>
                
                <div className="flex items-center justify-between text-sm text-muted-foreground">
                  <div className="flex items-center">
                    <User className="mr-1 h-3 w-3" />
                    <span>{tool.author}</span>
                  </div>
                  <div className="flex items-center">
                    <Download className="mr-1 h-3 w-3" />
                    <span>{tool.installCount} 安装</span>
                  </div>
                </div>
              </CardContent>
              
              <div className="absolute inset-0 flex items-center justify-center bg-background/80 opacity-0 transition-opacity group-hover:opacity-100">
                <div className="flex gap-2">
                  <Button 
                    size="sm"
                    onClick={() => handleInstallTool(tool)}
                    disabled={tool.isInstalled}
                  >
                    <Download className="mr-2 h-4 w-4" />
                    {tool.isInstalled ? "已安装" : "安装"}
                  </Button>
                  <Button 
                    variant="outline" 
                    size="sm"
                    asChild
                  >
                    <Link href={`/tools/${tool.toolId}/${tool.current_version}`}>
                      <Info className="mr-2 h-4 w-4" />
                      详情
                    </Link>
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
      
      {/* 安装对话框 */}
      <InstallToolDialog 
        open={isInstallDialogOpen}
        onOpenChange={setIsInstallDialogOpen}
        tool={selectedTool}
        version={selectedTool?.current_version}
        onSuccess={handleInstallSuccess}
      />
    </div>
  )
} 
