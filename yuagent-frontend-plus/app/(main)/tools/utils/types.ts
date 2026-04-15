export interface HostedInstallConfig {
  type?: "sse" | "streamableHttp"
  url?: string
  baseUrl?: string
  headers?: Record<string, string>
}

export interface StdioInstallConfig {
  type?: "stdio"
  command?: string
  args?: string[]
  env?: Record<string, string>
}

export type InstallCommandConfig = HostedInstallConfig | StdioInstallConfig

export interface MarketTool {
  id: string
  toolId?: string
  name: string
  icon: string | null
  subtitle: string
  description: string
  user_id: string
  author: string
  labels: string[]
  tool_type: string
  upload_type: string
  upload_url: string
  install_command: InstallCommandConfig
  is_office?: boolean
  installCount: number
  status: ToolStatus
  current_version?: string
  createdAt: string
  updatedAt: string
  tool_list?: ToolFunction[]
}

export interface UserTool {
  id: string
  toolId?: string
  name: string
  icon: string | null
  subtitle: string
  description: string
  userId?: string
  userName?: string | null
  author?: string
  labels: string[]
  toolType?: string
  tool_type?: string
  uploadType?: string
  upload_type?: string
  uploadUrl?: string
  upload_url?: string
  toolList?: ToolFunction[]
  tool_list?: ToolFunction[]
  status: ToolStatus
  failedStepStatus?: ToolStatus
  rejectReason?: string
  isOffice?: boolean
  is_office?: boolean
  office?: boolean
  installCount?: number | null
  currentVersion?: string | null
  current_version?: string
  installCommand?: string
  install_command?: InstallCommandConfig
  usageCount?: number
  isOwner?: boolean
  deleted?: boolean
  createdAt: string
  updatedAt: string
}

export interface ToolFunction {
  name: string
  description: string
  parameters?: {
    type?: string
    properties: Record<string, any>
    required?: string[]
  }
  inputSchema?: {
    type: string
    properties: Record<string, any>
    required?: string[]
  }
}

export enum ToolStatus {
  WAITING_REVIEW = "WAITING_REVIEW",
  GITHUB_URL_VALIDATE = "GITHUB_URL_VALIDATE",
  DEPLOYING = "DEPLOYING",
  FETCHING_TOOLS = "FETCHING_TOOLS",
  MANUAL_REVIEW = "MANUAL_REVIEW",
  APPROVED = "APPROVED",
  FAILED = "FAILED",
  PENDING = "PENDING",
  REJECTED = "REJECTED",
}

export interface DialogState {
  detailOpen: boolean
  installOpen: boolean
  deleteOpen: boolean
  selectedTool: MarketTool | UserTool | null
  toolToDelete: UserTool | null
}

export interface VersionData {
  id: string
  name: string
  icon: string | null
  subtitle: string
  description: string
  userId: string
  version: string
  toolId: string
  uploadType: string | null
  uploadUrl: string | null
  toolList: ToolFunction[]
  labels: string[]
  publicStatus: boolean
  changeLog: string
  createdAt: string
  updatedAt: string
  userName: string | null
  installCount: number | null
  office: boolean
}
