import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "@/hooks/use-toast";
import {
  deleteToolWithToast,
  getInstalledTools,
  getInstalledToolsWithToast,
  getUserTools,
  getUserToolsWithToast,
  uninstallToolWithToast,
} from "@/lib/tool-service";
import type { PortalToolDTO, ToolVersionDTO } from "@/types/tool";
import { ToolStatus, UserTool } from "../utils/types";

function serializeInstallCommand(command: PortalToolDTO["installCommand"]): string | undefined {
  if (typeof command === "string") {
    return command
  }
  return command && typeof command === "object" ? JSON.stringify(command) : undefined
}

function normalizeToolStatus(status: string | null | undefined): ToolStatus {
  return Object.values(ToolStatus).includes(status as ToolStatus)
    ? status as ToolStatus
    : ToolStatus.PENDING;
}

function normalizeOwnedTool(tool: PortalToolDTO): UserTool {
  return {
    id: tool.id,
    toolId: tool.id,
    name: tool.name,
    icon: tool.icon,
    subtitle: tool.subtitle || "",
    description: tool.description || "",
    labels: tool.labels || [],
    status: normalizeToolStatus(tool.status),
    createdAt: tool.createdAt || "",
    updatedAt: tool.updatedAt || "",
    author: tool.userName || "",
    userId: tool.userId || undefined,
    userName: tool.userName || undefined,
    toolType: tool.toolType || undefined,
    uploadType: tool.uploadType || undefined,
    uploadUrl: tool.uploadUrl || undefined,
    installCommand: serializeInstallCommand(tool.installCommand),
    tool_list: tool.toolList || [],
    toolList: tool.toolList || [],
    usageCount: 0,
    isOwner: true,
  };
}

function normalizeInstalledTool(tool: ToolVersionDTO): UserTool {
  return {
    id: tool.id,
    toolId: tool.toolId,
    name: tool.name,
    icon: tool.icon,
    subtitle: tool.subtitle || "",
    description: tool.description || "",
    labels: tool.labels || [],
    author: tool.userName || "",
    userId: tool.userId || undefined,
    userName: tool.userName || undefined,
    uploadType: tool.uploadType || undefined,
    uploadUrl: tool.uploadUrl || undefined,
    tool_list: tool.toolList || [],
    toolList: tool.toolList || [],
    usageCount: 0,
    current_version: tool.version || "0.0.1",
    isOwner: false,
    status: ToolStatus.APPROVED,
    deleted: tool.delete || false,
    createdAt: tool.createdAt || "",
    updatedAt: tool.updatedAt || "",
  };
}

export function useUserTools() {
  const [userToolsLoading, setUserToolsLoading] = useState(true);
  const [userTools, setUserTools] = useState<UserTool[]>([]);
  const [ownedTools, setOwnedTools] = useState<UserTool[]>([]);
  const [installedTools, setInstalledTools] = useState<UserTool[]>([]);
  const [isDeletingTool, setIsDeletingTool] = useState(false);
  const latestRequestIdRef = useRef(0);
  const pollingRequestInFlightRef = useRef(false);

  const fetchUserTools = useCallback(async (silent = false) => {
    if (silent && pollingRequestInFlightRef.current) {
      return;
    }

    const requestId = ++latestRequestIdRef.current;
    if (silent) {
      pollingRequestInFlightRef.current = true;
    } else {
      setUserToolsLoading(true);
    }

    try {
      const [createdToolsResponse, installedToolsResponse] = await Promise.all([
        silent ? getUserTools() : getUserToolsWithToast(),
        silent
          ? getInstalledTools({ page: 1, pageSize: 50 })
          : getInstalledToolsWithToast({ page: 1, pageSize: 50 }),
      ]);

      if (requestId !== latestRequestIdRef.current) {
        return;
      }

      if (createdToolsResponse.code !== 200 || installedToolsResponse.code !== 200) {
        if (!silent) {
          const failedResponse = createdToolsResponse.code !== 200
            ? createdToolsResponse
            : installedToolsResponse;
          toast({
            title: "获取工具列表失败",
            description: failedResponse.message,
            variant: "destructive",
          });
          setOwnedTools([]);
          setInstalledTools([]);
          setUserTools([]);
        }
        return;
      }

      const nextOwnedTools = (Array.isArray(createdToolsResponse.data) ? createdToolsResponse.data : [])
        .map(normalizeOwnedTool);
      const nextInstalledTools = (Array.isArray(installedToolsResponse.data?.records)
        ? installedToolsResponse.data.records
        : [])
        .map(normalizeInstalledTool);

      setOwnedTools(nextOwnedTools);
      setInstalledTools(nextInstalledTools);
      setUserTools([...nextOwnedTools, ...nextInstalledTools]);
    } catch {
      if (requestId !== latestRequestIdRef.current || silent) {
        return;
      }

      toast({
        title: "获取工具列表失败",
        description: "请稍后重试",
        variant: "destructive",
      });
      setOwnedTools([]);
      setInstalledTools([]);
      setUserTools([]);
    } finally {
      if (silent) {
        pollingRequestInFlightRef.current = false;
      }
      if (!silent && requestId === latestRequestIdRef.current) {
        setUserToolsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    fetchUserTools();
  }, [fetchUserTools]);

  useEffect(() => {
    const hasPendingOwnedTool = ownedTools.some(
      (tool) => tool.status !== ToolStatus.APPROVED && tool.status !== ToolStatus.FAILED
    );

    if (!hasPendingOwnedTool) {
      return;
    }

    let cancelled = false;
    let timer: number | undefined;

    const poll = async () => {
      await fetchUserTools(true);
      if (!cancelled) {
        timer = window.setTimeout(poll, 3000);
      }
    };

    timer = window.setTimeout(poll, 3000);

    return () => {
      cancelled = true;
      if (timer) {
        window.clearTimeout(timer);
      }
    };
  }, [fetchUserTools, ownedTools]);

  const handleDeleteTool = async (toolToDelete: UserTool) => {
    if (!toolToDelete) {
      return false;
    }

    try {
      setIsDeletingTool(true);

      const response = toolToDelete.isOwner
        ? await deleteToolWithToast(toolToDelete.id)
        : await uninstallToolWithToast(toolToDelete.toolId || toolToDelete.id);

      if (response.code !== 200) {
        return false;
      }

      setUserTools((prev) => prev.filter((tool) => tool.id !== toolToDelete.id));
      if (toolToDelete.isOwner) {
        setOwnedTools((prev) => prev.filter((tool) => tool.id !== toolToDelete.id));
      } else {
        setInstalledTools((prev) => prev.filter((tool) => tool.id !== toolToDelete.id));
      }

      return true;
    } catch (error) {
      toast({
        title: toolToDelete.isOwner ? "删除失败" : "卸载失败",
        description: error instanceof Error ? error.message : "操作失败，请稍后重试",
        variant: "destructive",
      });
      return false;
    } finally {
      setIsDeletingTool(false);
    }
  };

  return {
    userTools,
    ownedTools,
    installedTools,
    userToolsLoading,
    isDeletingTool,
    handleDeleteTool,
    fetchUserTools,
  };
}
