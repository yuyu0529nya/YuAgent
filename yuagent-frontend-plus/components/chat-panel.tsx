"use client"

import { useState, useRef, useEffect, useCallback } from "react"
import { Send, Wrench, Clock, Square } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { streamChat } from "@/lib/api"
import { toast } from "@/hooks/use-toast"
import { getSessionMessages, type MessageDTO } from "@/lib/session-message-service"
import { AgentSessionService } from "@/lib/agent-session-service"
import { API_CONFIG, API_ENDPOINTS } from "@/lib/api-config"
import { Skeleton } from "@/components/ui/skeleton"
import { MessageMarkdown } from "@/components/ui/message-markdown"
import { MessageType, type Message as MessageInterface } from "@/types/conversation"
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { nanoid } from 'nanoid'
import MultiModalUpload, { type ChatFile } from "@/components/multi-modal-upload"
import MessageFileDisplay from "@/components/message-file-display"

interface ChatPanelProps {
  conversationId: string
  isFunctionalAgent?: boolean
  agentName?: string

  onToggleScheduledTaskPanel?: () => void // 新增：切换定时任务面板的回调
  multiModal?: boolean // 新增：是否启用多模态功能
}

interface AssistantMessage {
  id: string
  hasContent: boolean
}

interface StreamData {
  content: string
  done: boolean
  sessionId: string
  provider?: string
  model?: string
  timestamp: number
  messageType?: string // 消息类型
  files?: string[] // 新增：文件URL列表
}

export function ChatPanel({ conversationId, isFunctionalAgent = false, agentName = "AI助手", onToggleScheduledTaskPanel, multiModal = false }: ChatPanelProps) {
  const [input, setInput] = useState("")
  const [messages, setMessages] = useState<MessageInterface[]>([])
  const [isTyping, setIsTyping] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [autoScroll, setAutoScroll] = useState(true)
  const [isThinking, setIsThinking] = useState(false)
  const [currentAssistantMessage, setCurrentAssistantMessage] = useState<AssistantMessage | null>(null)
  const [uploadedFiles, setUploadedFiles] = useState<ChatFile[]>([]) // 新增：已上传的文件列表
  const [isInterrupting, setIsInterrupting] = useState(false) // 新增：中断状态
  const [canInterrupt, setCanInterrupt] = useState(false) // 新增：是否可以中断

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const chatContainerRef = useRef<HTMLDivElement>(null)
  const abortControllerRef = useRef<AbortController | null>(null) // 新增：中断控制器
  const sessionLoadRequestRef = useRef(0)
  // 会话切换或请求结束后使旧流失效，避免其异步分片写入当前会话。
  const chatRequestRef = useRef(0)
  
  // 新增：使用useRef保存不需要触发重新渲染的状态
  const hasReceivedFirstResponse = useRef(false);
  const messageContentAccumulator = useRef({
    content: "",
    type: MessageType.TEXT as MessageType
  });

  // 添加消息序列计数器
  const messageSequenceNumber = useRef(0);
  const pendingAssistantMessageRef = useRef<{
    id: string
    content: string
    type: MessageType
  } | null>(null)
  const streamUpdateFrameRef = useRef<number | null>(null)

  const upsertAssistantMessage = useCallback((messageId: string, messageData: {
    content: string
    type: MessageType
  }) => {
    setMessages(prev => {
      const messageIndex = prev.findIndex(message => message.id === messageId)

      if (messageIndex >= 0) {
        const nextMessages = [...prev]
        nextMessages[messageIndex] = {
          ...nextMessages[messageIndex],
          content: messageData.content,
        }
        return nextMessages
      }

      return [
        ...prev,
        {
          id: messageId,
          role: "assistant",
          content: messageData.content,
          type: messageData.type,
          createdAt: new Date().toISOString(),
        },
      ]
    })
  }, [])

  const flushPendingAssistantMessage = useCallback(() => {
    streamUpdateFrameRef.current = null
    const pendingMessage = pendingAssistantMessageRef.current
    pendingAssistantMessageRef.current = null

    if (pendingMessage) {
      upsertAssistantMessage(pendingMessage.id, pendingMessage)
    }
  }, [upsertAssistantMessage])

  const scheduleAssistantMessageUpdate = useCallback((messageId: string, messageData: {
    content: string
    type: MessageType
  }) => {
    pendingAssistantMessageRef.current = { id: messageId, ...messageData }
    setCurrentAssistantMessage({ id: messageId, hasContent: true })

    if (streamUpdateFrameRef.current === null) {
      streamUpdateFrameRef.current = requestAnimationFrame(flushPendingAssistantMessage)
    }
  }, [flushPendingAssistantMessage])

  // 在组件初始化和conversationId变更时重置状态
  useEffect(() => {
    chatRequestRef.current += 1
    hasReceivedFirstResponse.current = false;
    messageContentAccumulator.current = {
      content: "",
      type: MessageType.TEXT
    };
    messageSequenceNumber.current = 0;
    
    // 重置中断相关状态
    setCanInterrupt(false);
    setIsInterrupting(false);
    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    pendingAssistantMessageRef.current = null
    if (streamUpdateFrameRef.current !== null) {
      cancelAnimationFrame(streamUpdateFrameRef.current)
      streamUpdateFrameRef.current = null
    }
  }, [conversationId]);

  useEffect(() => () => {
    if (streamUpdateFrameRef.current !== null) {
      cancelAnimationFrame(streamUpdateFrameRef.current)
    }
  }, [])

  // 获取会话消息
  useEffect(() => {
    const requestId = ++sessionLoadRequestRef.current
    const controller = new AbortController()
    const isCurrentRequest = () => (
      sessionLoadRequestRef.current === requestId && !controller.signal.aborted
    )

    const fetchSessionMessages = async () => {
      if (!conversationId) return
      
      try {
        setLoading(true)
        setError(null)
        // 清空之前的消息，避免显示上一个会话的内容
        setMessages([])
        
        // 获取会话消息
        const messagesResponse = await getSessionMessages(conversationId, {
          signal: controller.signal,
        })

        if (!isCurrentRequest()) {
          return
        }
        
        if (messagesResponse.code === 200 && messagesResponse.data) {
          // 转换消息格式
          const formattedMessages = messagesResponse.data.map((msg: MessageDTO) => {
            // 将SYSTEM角色的消息视为assistant
            const normalizedRole = msg.role === "SYSTEM" ? "assistant" : msg.role as "USER" | "SYSTEM" | "assistant"
            
            // 获取消息类型，优先使用messageType字段
            let messageType = MessageType.TEXT
            if (msg.messageType) {
              // 尝试转换为枚举值
              try {
                messageType = msg.messageType as MessageType
              } catch (e) {
 
              }
            }
            
            return {
              id: msg.id,
              role: normalizedRole,
              content: msg.content,
              type: messageType,
              createdAt: msg.createdAt,
              updatedAt: msg.updatedAt,
              fileUrls: msg.fileUrls || [] // 添加文件URL列表
            }
          })
          
          setMessages(formattedMessages)
        } else {
          const errorMessage = messagesResponse.message || "获取会话消息失败"
          setError(errorMessage)
          toast({
            title: "获取会话消息失败",
            description: errorMessage,
            variant: "destructive",
          })
        }
      } catch (error) {
        if (!isCurrentRequest()) {
          return
        }
        setError(error instanceof Error ? error.message : "获取会话消息时发生未知错误")
      } finally {
        if (isCurrentRequest()) {
          setLoading(false)
        }
      }
    }

    fetchSessionMessages()
    return () => controller.abort()
  }, [conversationId])

  // 滚动到底部
  useEffect(() => {
    if (autoScroll) {
      // 流式消息每帧都会更新。此处持续启动 smooth 动画会导致浏览器反复取消并重算滚动，
      // 长回复时容易卡顿；用户主动发送时仍由 scrollToBottom 保持平滑滚动。
      messagesEndRef.current?.scrollIntoView({ behavior: isTyping ? "auto" : "smooth" })
    }
  }, [messages, isTyping, autoScroll])

  // 处理对话中断
  const handleInterrupt = async () => {
    if (!conversationId || !canInterrupt || isInterrupting) {
      return
    }

    // 先让正在读取的流失效，再发起服务端中断，防止中断竞态下仍消费到末尾分片。
    chatRequestRef.current += 1
    setIsInterrupting(true)
    
    try {
      // 1. 取消当前的网络请求
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
        abortControllerRef.current = null
      }

      // 2. 调用AgentSessionService中断接口
      const response = await AgentSessionService.interruptSession(conversationId)
      
      if (response.code === 200) {
        toast({
          title: "对话已中断",
          variant: "default"
        })
      } else {
        throw new Error(response.message || "中断失败")
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "中断对话失败"
 
      toast({
        title: "中断失败",
        description: errorMessage,
        variant: "destructive"
      })
    } finally {
      setIsInterrupting(false)
      setCanInterrupt(false)
      setIsTyping(false)
      setIsThinking(false)
    }
  }

  // 监听滚动事件
  useEffect(() => {
    const chatContainer = chatContainerRef.current
    if (!chatContainer) return

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = chatContainer
      // 判断是否滚动到底部附近（20px误差范围）
      const isAtBottom = scrollHeight - scrollTop - clientHeight < 20
      setAutoScroll(isAtBottom)
    }

    chatContainer.addEventListener('scroll', handleScroll)
    return () => chatContainer.removeEventListener('scroll', handleScroll)
  }, [])

  // 处理用户主动发送消息时强制滚动到底部
  const scrollToBottom = () => {
    setAutoScroll(true)
    // 使用setTimeout确保在下一个渲染周期执行
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
    }, 100)
  }

  // 处理发送消息
  const parseSSEBlock = (block: string): { event: string; data: StreamData | null } | null => {
    const lines = block.split(/\r?\n/)
    const eventLine = lines.find(line => line.startsWith('event:'))
    const dataLines = lines
      .filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).replace(/^ /, ''))

    if (dataLines.length === 0) {
      return null
    }

    try {
      return {
        event: eventLine?.slice(6).trim() || 'message',
        data: JSON.parse(dataLines.join('\n')) as StreamData
      }
    } catch {
      return null
    }
  }

  const handleSendMessage = async () => {
    // Enter 快捷键不会受 Button 的 disabled 属性限制；此处必须作为唯一发送入口兜底。
    if (isTyping || isInterrupting || (!input.trim() && uploadedFiles.length === 0)) return

    const requestId = ++chatRequestRef.current
    const isCurrentRequest = () => chatRequestRef.current === requestId

    // 添加调试信息
 
    
    // 获取已完成上传的文件URL
    const completedFiles = uploadedFiles.filter(file => file.url && file.uploadProgress === 100)
    const fileUrls = completedFiles.map(file => file.url)

    const userMessage = input.trim()
    setInput("")
    setUploadedFiles([]) // 清空已上传的文件
    setIsTyping(true)
    setIsThinking(true) // 设置思考状态
    setCurrentAssistantMessage(null) // 重置助手消息状态
    setCanInterrupt(true) // 启用中断功能
    setIsInterrupting(false) // 重置中断状态
    scrollToBottom() // 用户发送新消息时强制滚动到底部
    
    // 创建新的AbortController
    const requestController = new AbortController()
    abortControllerRef.current = requestController
    
    // 重置所有状态
    resetMessageAccumulator()
    hasReceivedFirstResponse.current = false
    messageSequenceNumber.current = 0; // 重置消息序列计数器

    // 输出文件URL到控制台
    if (fileUrls.length > 0) {
 
    }

    // 添加用户消息到消息列表
    const userMessageId = `user-${Date.now()}`
    setMessages((prev) => [
      ...prev,
      {
        id: userMessageId,
        role: "USER",
        content: userMessage,
        type: MessageType.TEXT,
        createdAt: new Date().toISOString(),
        fileUrls: fileUrls.length > 0 ? fileUrls : undefined // 修改：使用fileUrls
      },
    ])

    try {
      // 发送消息到服务器并获取流式响应，包含文件URL
      const response = await streamChat(
        userMessage,
        conversationId,
        fileUrls.length > 0 ? fileUrls : undefined,
        requestController.signal,
      )

      if (!isCurrentRequest() || requestController.signal.aborted) {
        return
      }

      // 检查响应状态，如果不是成功状态，则关闭思考状态并返回
      if (!response.ok) {
        // 错误已在streamChat中处理并显示toast
        if (isCurrentRequest()) {
          setIsTyping(false)
          setIsThinking(false) // 关闭思考状态，修复动画一直显示的问题
        }
        return // 直接返回，不继续处理
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error("No reader available")
      }

      // 生成基础消息ID，作为所有消息序列的前缀
      const baseMessageId = Date.now().toString()
      
      // 重置状态
      hasReceivedFirstResponse.current = false;
      messageContentAccumulator.current = {
        content: "",
        type: MessageType.TEXT
      };
      
      const decoder = new TextDecoder()
      let buffer = ""

      while (true) {
        // 既检查自身取消状态，也检查是否已被会话切换或后续请求取代。
        if (requestController.signal.aborted || !isCurrentRequest()) {
 
          break
        }
        
        const { done, value } = await reader.read()
        if (done) {
          buffer += decoder.decode()
          if (buffer.trim() && isCurrentRequest()) {
            const parsed = parseSSEBlock(buffer)
            if (parsed?.data) {
              if (parsed.event === 'interrupt') {
                setIsTyping(false)
                setIsThinking(false)
                setCanInterrupt(false)
              } else {
                handleStreamDataMessage(parsed.data, baseMessageId)
              }
            }
          }
          break
        }

        // 解码数据块并添加到缓冲区
        buffer += decoder.decode(value, { stream: true })
        
        // 处理缓冲区中的SSE数据
        const lines = buffer.split(/\r?\n\r?\n/)
        // 保留最后一个可能不完整的行
        buffer = lines.pop() || ""
        
        for (const block of lines) {
          if (!isCurrentRequest() || requestController.signal.aborted) {
            break
          }

          const parsed = parseSSEBlock(block)
          if (!parsed?.data) {
            continue
          }

          if (parsed.event === 'interrupt') {
            setIsTyping(false)
            setIsThinking(false)
            setCanInterrupt(false)
            break
          }

          handleStreamDataMessage(parsed.data, baseMessageId)
        }
      }
    } catch (error) {
 
      
      // 如果是中断导致的错误，不显示错误提示
      if (!isCurrentRequest() || (error instanceof Error && error.name === 'AbortError')) {
 
      } else {
        setIsThinking(false) // 错误发生时关闭思考状态
        toast({
          title: "发送消息失败",
          description: error instanceof Error ? error.message : "未知错误",
          variant: "destructive",
        })
      }
    } finally {
      if (isCurrentRequest()) {
        setIsTyping(false)
        setCanInterrupt(false) // 重置中断状态
        setIsInterrupting(false)
      }
      if (abortControllerRef.current === requestController) {
        abortControllerRef.current = null
      }
    }
  }

  // 消息处理主函数 - 完全重构
  const handleStreamDataMessage = (data: StreamData, baseMessageId: string) => {
    // 首次响应处理
    if (!hasReceivedFirstResponse.current) {
      hasReceivedFirstResponse.current = true;
      setIsThinking(false);
    }
    
    // 处理错误消息
    if (isErrorMessage(data)) {
      handleErrorMessage(data);
      return;
    }
    
    // 获取消息类型，默认为TEXT
    const messageType = data.messageType as MessageType || MessageType.TEXT;
    
    // 生成当前消息序列的唯一ID
    const currentMessageId = `assistant-${messageType}-${baseMessageId}-seq${messageSequenceNumber.current}`;
    
 
    
    // 处理消息内容（用于UI显示）
    const displayableTypes = [undefined, "TEXT", "TOOL_CALL"];
    const isDisplayableType = displayableTypes.includes(data.messageType);
    
    if (isDisplayableType && data.content) {
      // 累积消息内容
      messageContentAccumulator.current.content += data.content;
      messageContentAccumulator.current.type = messageType;
      
      // 每帧最多刷新一次，避免流式分片触发过多 React 重渲染
      scheduleAssistantMessageUpdate(currentMessageId, messageContentAccumulator.current);
    }
    
    // 消息结束信号处理
    if (data.done) {
 
      
      // 如果是可显示类型且有内容，完成该消息
      if (isDisplayableType && messageContentAccumulator.current.content) {
        finalizeMessage(currentMessageId, messageContentAccumulator.current);
      }
      
      // 无论如何，都重置消息累积器，准备接收下一条消息
      resetMessageAccumulator();
      
      // 增加消息序列计数
      messageSequenceNumber.current += 1;
      
 
    }
  }
  
  // 完成消息处理
  const finalizeMessage = (messageId: string, messageData: {
    content: string;
    type: MessageType;
  }) => {
 
    
    // 如果消息内容为空，不处理
    if (!messageData.content || messageData.content.trim() === "") {
 
      return;
    }
    
    // 确保最后一个尚未渲染的分片立即显示
    flushPendingAssistantMessage()
    
  }

  // 重置消息累积器
  const resetMessageAccumulator = () => {
 
    messageContentAccumulator.current = {
      content: "",
      type: MessageType.TEXT
    };
  };

  // 处理按键事件
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      handleSendMessage()
    }
  }

  // 格式化消息时间
  const formatMessageTime = (timestamp?: string) => {
    if (!timestamp) return '';
    try {
      const date = new Date(timestamp);
      return date.toLocaleString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
      });
    } catch (e) {
      return '';
    }
  };

  // 根据消息类型获取图标和文本
  const getMessageTypeInfo = (type: MessageType) => {
    switch (type) {
      case MessageType.TOOL_CALL:
        return {
          icon: <Wrench className="h-5 w-5 text-blue-500" />,
          text: '工具调用'
        };
      case MessageType.TEXT:
      default:
        return {
          icon: null,
          text: agentName
        };
    }
  };


  // 判断是否为错误消息
  const isErrorMessage = (data: StreamData): boolean => {
    return !!data.content && (
      data.content.includes("Error updating database") || 
      data.content.includes("PSQLException") || 
      data.content.includes("任务执行过程中发生错误")
    );
  };

  // 处理错误消息
  const handleErrorMessage = (data: StreamData) => {
 
    toast({
      title: "任务执行错误",
      description: "服务器处理任务时遇到问题，请稍后再试",
      variant: "destructive",
    });
  };

  return (
    <div className="relative flex h-full w-full flex-col overflow-hidden bg-white">
      <div 
        ref={chatContainerRef}
        className="flex-1 overflow-y-auto px-4 pt-3 pb-4 w-full"
      >
        {loading ? (
          // 加载状态
          <div className="flex items-center justify-center h-full w-full">
            <div className="text-center">
              <div className="inline-block animate-spin rounded-full h-8 w-8 border-2 border-gray-200 border-t-blue-500 mb-2"></div>
              <p className="text-gray-500">正在加载消息...</p>
            </div>
          </div>
        ) : (
          <div className="space-y-4 w-full">
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-md p-3 text-sm text-red-600">
                {error}
              </div>
            )}
            
            {/* 消息内容 */}
            <div className="space-y-6 w-full">
              {messages.length === 0 ? (
                <div className="flex items-center justify-center h-20 w-full">
                  <p className="text-gray-400">暂无消息，开始发送消息吧</p>
                </div>
              ) : (
                messages.map((message) => (
                  <div
                    key={message.id}
                    className={`w-full`}
                  >
                    {/* 用户消息 */}
                    {message.role === "USER" ? (
                      <div className="flex justify-end">
                        <div className="max-w-[80%]">
                          {/* 文件显示 - 在消息内容之前 */}
                          {message.fileUrls && message.fileUrls.length > 0 && (
                            <div className="mb-3">
                              <MessageFileDisplay fileUrls={message.fileUrls} />
                            </div>
                          )}
                          
                          {/* 消息内容 */}
                          {message.content && (
                            <div className="bg-blue-50 text-gray-800 p-3 rounded-lg shadow-sm">
                              {message.content}
                            </div>
                          )}
                          
                          <div className="text-xs text-gray-500 mt-1 text-right">
                            {formatMessageTime(message.createdAt)}
                          </div>
                        </div>
                      </div>
                    ) : (
                      /* AI消息 */
                      <div className="flex">
                        <div className="h-8 w-8 mr-2 bg-gray-100 rounded-full flex items-center justify-center flex-shrink-0">
                          {message.type && message.type !== MessageType.TEXT 
                            ? getMessageTypeInfo(message.type).icon 
                            : <div className="text-lg">🤖</div>
                          }
                        </div>
                        <div className="max-w-[95%]">
                          {/* 消息类型指示 */}
                          <div className="flex items-center mb-1 text-xs text-gray-500">
                            <span className="font-medium">
                              {message.type ? getMessageTypeInfo(message.type).text : agentName}
                            </span>
                            <span className="mx-1 text-gray-400">·</span>
                            <span>{formatMessageTime(message.createdAt)}</span>
                          </div>
                          
                          {/* 文件显示 - 在消息内容之前 */}
                          {message.fileUrls && message.fileUrls.length > 0 && (
                            <div className="mb-3">
                              <MessageFileDisplay fileUrls={message.fileUrls} />
                            </div>
                          )}
                          
                          {/* 消息内容 */}
                          {message.content && (
                            <div className="p-3 rounded-lg">
                              <MessageMarkdown showCopyButton={true}
                                content={message.content}
                                isStreaming={Boolean((message as { isStreaming?: boolean }).isStreaming)}
                              />
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                ))
              )}
              
              {/* 思考中提示 */}
              {isThinking && (!currentAssistantMessage || !currentAssistantMessage.hasContent) && (
                <div className="flex items-start">
                  <div className="h-8 w-8 mr-2 bg-gray-100 rounded-full flex items-center justify-center flex-shrink-0">
                    <div className="text-lg">🤖</div>
                  </div>
                  <div className="max-w-[95%]">
                    <div className="flex items-center mb-1 text-xs text-gray-500">
                      <span className="font-medium">{agentName}</span>
                      <span className="mx-1 text-gray-400">·</span>
                      <span>刚刚</span>
                    </div>
                    <div className="space-y-2 p-3 rounded-lg">
                      <div className="flex space-x-2 items-center">
                        <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                        <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse delay-75"></div>
                        <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse delay-150"></div>
                        <div className="text-sm text-gray-500 animate-pulse">思考中...</div>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
              {!autoScroll && isTyping && (
                <Button
                  variant="outline"
                  size="sm"
                  className="fixed bottom-20 right-6 rounded-full shadow-md bg-white"
                  onClick={scrollToBottom}
                >
                  <span>↓</span>
                </Button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 输入框 */}
      <div className="border-t p-2 bg-white">
        {/* 已上传文件显示区域 - 在输入框上方 */}
        {uploadedFiles.length > 0 && (
          <div className="mb-2 px-2">
            <div className="flex flex-wrap gap-2">
              {uploadedFiles.map((file) => (
                <div
                  key={file.id}
                  className="flex items-center gap-2 px-3 py-2 bg-blue-50 rounded-lg text-sm border border-blue-200"
                >
                  <div className="flex-shrink-0 w-5 h-5 bg-blue-100 rounded flex items-center justify-center">
                    {file.type.startsWith('image/') ? (
                      <span className="text-sm">🖼️</span>
                    ) : (
                      <span className="text-sm">📄</span>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate max-w-32">
                      {file.name}
                    </p>
                    {/* 上传进度条 */}
                    {file.uploadProgress !== undefined && file.uploadProgress < 100 && (
                      <div className="w-full bg-gray-200 rounded-full h-1 mt-1">
                        <div
                          className="bg-blue-600 h-1 rounded-full transition-all duration-300"
                          style={{ width: `${file.uploadProgress}%` }}
                        />
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => {
                      setUploadedFiles(prev => prev.filter(f => f.id !== file.id))
                    }}
                    className="flex-shrink-0 w-4 h-4 rounded-full bg-red-100 hover:bg-red-200 flex items-center justify-center transition-colors"
                    disabled={isTyping}
                  >
                    <span className="text-xs text-red-600">×</span>
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
        
        {/* 输入框和按钮区域 */}
        <div className="flex items-end gap-2">
          {/* 多模态文件上传按钮 */}
          <MultiModalUpload
            multiModal={multiModal}
            uploadedFiles={uploadedFiles}
            setUploadedFiles={setUploadedFiles}
            disabled={isTyping}
            className="flex-shrink-0"
            showFileList={false}
          />
          
          {/* 定时任务按钮 */}
          {isFunctionalAgent && (
            <Button
              variant="ghost"
              size="icon"
              className="h-10 w-10 flex-shrink-0"
              onClick={onToggleScheduledTaskPanel}
              title="定时任务"
            >
              <Clock className="h-5 w-5 text-gray-500 hover:text-primary" />
            </Button>
          )}
          
          <Textarea
            placeholder="输入消息...(Shift+Enter换行, Enter发送)"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyPress}
            className="min-h-[56px] flex-1 resize-none overflow-hidden rounded-xl bg-white px-3 py-2 font-normal border-gray-200 shadow-sm focus-visible:ring-2 focus-visible:ring-blue-400 focus-visible:ring-opacity-50"
            rows={Math.min(5, Math.max(2, input.split('\n').length))}
          />
          
          {/* 中断/发送按钮 - 根据状态条件渲染 */}
          {canInterrupt ? (
            <Button 
              onClick={handleInterrupt}
              disabled={isInterrupting}
              className="h-10 w-10 rounded-xl bg-red-500 hover:bg-red-600 shadow-sm flex-shrink-0"
              title="中断对话"
            >
              <Square className="h-5 w-5" />
            </Button>
          ) : (
            <Button 
              onClick={handleSendMessage} 
              disabled={(!input.trim() && uploadedFiles.length === 0) || isTyping} 
              className="h-10 w-10 rounded-xl bg-blue-500 hover:bg-blue-600 shadow-sm flex-shrink-0"
              title="发送消息"
            >
              <Send className="h-5 w-5" />
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

