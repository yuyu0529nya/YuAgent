export interface Tool {
  id: string
  toolId?: string // 工具ID
  name: string
  icon: string | null
  subtitle: string
  description: string
  user_id: string
  author: string // 作者名称（前端显示用）
  labels: string[]
  tool_type: string
  upload_type: string
  upload_url: string
  install_command?: PluginInstallConfig
  tool_list: ToolItem[]
  status: ToolStatus
  is_office: boolean
  installCount: number // 前端展示用
  current_version?: string // 当前版本号
  mcpServerName?: string // MCP服务器名称，用于预设参数
  isGlobal?: boolean // 是否为全局工具
  isInstalled?: boolean // 当前用户是否已安装（仅用于页面即时状态）
  createdAt: string
  updatedAt: string
}

/** 用户工具管理接口返回的后端 DTO（保持服务端 camelCase 字段）。 */
export interface PortalToolDTO {
  id: string
  name: string
  icon: string | null
  subtitle?: string | null
  description?: string | null
  userId?: string | null
  userName?: string | null
  labels?: string[] | null
  toolType?: string | null
  uploadType?: string | null
  uploadUrl?: string | null
  toolList?: ToolItem[] | null
  status?: string | null
  isOffice?: boolean | null
  installCount?: number | null
  currentVersion?: string | null
  installCommand?: unknown
  mcpServerName?: string | null
  isGlobal?: boolean | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ToolVersion {
  id: string
  name: string
  icon: string | null
  subtitle: string
  description: string
  user_id: string
  version: string
  tool_id: string
  upload_type: string
  upload_url: string
  tool_list: ToolItem[]
  labels: string[]
  is_office: boolean
  public_status: boolean
  createdAt: string
  updatedAt: string
  author: string // 作者名称（前端展示用）
}

export interface ToolItem {
  name: string
  description: string
  enabled?: boolean
  inputSchema?: {
    type: string
    properties: Record<string, ToolParameter>
    required: string[]
  }
  parameters?: {
    properties: Record<string, ToolParameter>
    required: string[]
  }
}

export interface ToolParameter {
  description?: string | null
  [key: string]: unknown
}

export type PluginInstallConfig = HostedPluginConfig | StdioPluginConfig;

export interface HostedPluginConfig {
  type: 'sse' | 'streamableHttp'
  url?: string
  baseUrl?: string
  headers?: Record<string, string>
}

export interface StdioPluginConfig {
  type: 'stdio'
  command: string
  args: string[]
  env: Record<string, string>
}

export enum ToolStatus {
  WAITING_REVIEW = "WAITING_REVIEW",
  GITHUB_URL_VALIDATE = "GITHUB_URL_VALIDATE",
  DEPLOYING = "DEPLOYING",
  FETCHING_TOOLS = "FETCHING_TOOLS",
  MANUAL_REVIEW = "MANUAL_REVIEW",
  APPROVED = "APPROVED",
  FAILED = "FAILED"
}

// API响应基本结构
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

// 分页响应结构
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 获取工具市场工具列表请求参数
export interface GetMarketToolsParams {
  toolName?: string
  labels?: string[]
  toolType?: string
  isOffice?: boolean
  page?: number
  pageSize?: number
}

// 工具版本详情响应
export interface ToolVersionDTO {
  id: string
  name: string
  icon: string | null
  subtitle: string | null
  description: string | null
  userId: string | null
  version: string
  toolId: string
  uploadType: string | null
  uploadUrl: string | null
  toolList: ToolItem[] | null
  labels: string[] | null
  isOffice?: boolean | null
  publicStatus?: boolean | null
  changeLog?: string | null
  createdAt: string | null
  updatedAt: string | null
  userName: string | null
  versions?: ToolVersionDTO[]
  office?: boolean | null
  installCount?: number | null
  mcpServerName?: string | null
  delete?: boolean | null
}

// 用户安装工具请求参数
export interface InstallToolParams {
  toolId: string
  version: string
}

export interface PublishToolToMarketParams {
  toolId: string;
  version: string;
  changeLog: string;
} 
