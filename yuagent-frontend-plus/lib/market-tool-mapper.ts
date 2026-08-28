import { PortalToolDTO, Tool, ToolStatus, ToolVersionDTO } from "@/types/tool"

/**
 * Converts the public tool-version DTO into the single display model used by
 * the market, recommendation cards, and version-detail page.
 */
export function toMarketTool(data: ToolVersionDTO): Tool {
  return {
    id: data.id,
    toolId: data.toolId,
    name: data.name,
    icon: data.icon,
    subtitle: data.subtitle ?? "",
    description: data.description ?? "",
    user_id: data.userId ?? "",
    author: data.userName ?? "未知作者",
    labels: data.labels ?? [],
    tool_type: "",
    upload_type: data.uploadType ?? "",
    upload_url: data.uploadUrl ?? "",
    tool_list: data.toolList ?? [],
    status: ToolStatus.APPROVED,
    is_office: Boolean(data.isOffice ?? data.office),
    installCount: data.installCount ?? 0,
    current_version: data.version,
    mcpServerName: data.mcpServerName ?? undefined,
    createdAt: data.createdAt ?? "",
    updatedAt: data.updatedAt ?? "",
  }
}

/** Converts a user-tool DTO into the same display model as market tools. */
export function toPortalTool(data: PortalToolDTO): Tool {
  const status = Object.values(ToolStatus).includes(data.status as ToolStatus)
    ? (data.status as ToolStatus)
    : ToolStatus.WAITING_REVIEW

  return {
    id: data.id,
    toolId: data.id,
    name: data.name,
    icon: data.icon,
    subtitle: data.subtitle ?? "",
    description: data.description ?? "",
    user_id: data.userId ?? "",
    author: data.userName ?? "未知作者",
    labels: data.labels ?? [],
    tool_type: data.toolType ?? "",
    upload_type: data.uploadType ?? "",
    upload_url: data.uploadUrl ?? "",
    install_command: undefined,
    tool_list: data.toolList ?? [],
    status,
    is_office: Boolean(data.isOffice),
    installCount: data.installCount ?? 0,
    current_version: data.currentVersion ?? undefined,
    mcpServerName: data.mcpServerName ?? undefined,
    isGlobal: data.isGlobal ?? undefined,
    createdAt: data.createdAt ?? "",
    updatedAt: data.updatedAt ?? "",
  }
}
