import { useEffect, useState } from "react";
import { toast } from "@/hooks/use-toast";
import {
  deleteToolWithToast,
  getInstalledTools,
  getInstalledToolsWithToast,
  getUserTools,
  getUserToolsWithToast,
  uninstallToolWithToast,
} from "@/lib/tool-service";
import { ToolStatus, UserTool } from "../utils/types";

function normalizeOwnedTool(tool: any): UserTool {
  return {
    ...tool,
    id: tool.id,
    toolId: tool.toolId || tool.id,
    name: tool.name,
    icon: tool.icon,
    subtitle: tool.subtitle || "",
    description: tool.description || "",
    labels: tool.labels || [],
    status: tool.status,
    createdAt: tool.createdAt,
    updatedAt: tool.updatedAt,
    author: tool.author || tool.userName || "",
    tool_list: tool.toolList || tool.tool_list || [],
    toolList: tool.toolList || tool.tool_list || [],
    usageCount: tool.usageCount || 0,
    isOwner: true,
  } as UserTool;
}

function normalizeInstalledTool(tool: any): UserTool {
  return {
    ...tool,
    id: tool.id,
    toolId: tool.toolId || tool.id,
    name: tool.name,
    icon: tool.icon,
    subtitle: tool.subtitle || "",
    description: tool.description || "",
    labels: tool.labels || [],
    author: tool.userName || tool.author || "",
    tool_list: tool.toolList || tool.tool_list || [],
    toolList: tool.toolList || tool.tool_list || [],
    usageCount: tool.usageCount || 0,
    current_version: tool.version || "0.0.1",
    isOwner: false,
    status: tool.status || "active",
    deleted: tool.delete || tool.deleted || false,
    createdAt: tool.createdAt,
    updatedAt: tool.updatedAt,
  } as UserTool;
}

export function useUserTools() {
  const [userToolsLoading, setUserToolsLoading] = useState(true);
  const [userTools, setUserTools] = useState<UserTool[]>([]);
  const [ownedTools, setOwnedTools] = useState<UserTool[]>([]);
  const [installedTools, setInstalledTools] = useState<UserTool[]>([]);
  const [isDeletingTool, setIsDeletingTool] = useState(false);

  useEffect(() => {
    fetchUserTools();
  }, []);

  useEffect(() => {
    const hasPendingOwnedTool = ownedTools.some(
      (tool) => tool.status !== ToolStatus.APPROVED && tool.status !== ToolStatus.FAILED
    );

    if (!hasPendingOwnedTool) {
      return;
    }

    const timer = window.setInterval(() => {
      fetchUserTools(true);
    }, 3000);

    return () => window.clearInterval(timer);
  }, [ownedTools]);

  async function fetchUserTools(silent = false) {
    try {
      setUserToolsLoading(true);

      const createdToolsResponse = silent ? await getUserTools() : await getUserToolsWithToast();
      const installedToolsResponse = silent
        ? await getInstalledTools({ page: 1, pageSize: 50 })
        : await getInstalledToolsWithToast({ page: 1, pageSize: 50 });

      let nextOwnedTools: UserTool[] = [];
      if (createdToolsResponse.code === 200) {
        const toolsList = Array.isArray(createdToolsResponse.data) ? createdToolsResponse.data : [];
        nextOwnedTools = toolsList.map(normalizeOwnedTool);
        setOwnedTools(nextOwnedTools);
      } else {
        if (!silent) {
          toast({
            title: "获取我的工具失败",
            description: createdToolsResponse.message,
            variant: "destructive",
          });
        }
        setOwnedTools([]);
      }

      let nextInstalledTools: UserTool[] = [];
      if (installedToolsResponse.code === 200) {
        const toolsList = Array.isArray(installedToolsResponse.data?.records)
          ? installedToolsResponse.data.records
          : [];
        nextInstalledTools = toolsList.map(normalizeInstalledTool);
        setInstalledTools(nextInstalledTools);
      } else {
        if (!silent) {
          toast({
            title: "获取已安装工具失败",
            description: installedToolsResponse.message,
            variant: "destructive",
          });
        }
        setInstalledTools([]);
      }

      setUserTools([...nextOwnedTools, ...nextInstalledTools]);
    } catch (error) {
      if (!silent) {
        toast({
          title: "获取工具列表失败",
          description: "请稍后重试",
          variant: "destructive",
        });
      }
      setOwnedTools([]);
      setInstalledTools([]);
      setUserTools([]);
    } finally {
      setUserToolsLoading(false);
    }
  }

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

      toast({
        title: toolToDelete.isOwner ? "删除成功" : "卸载成功",
        description: `工具 "${toolToDelete.name}" 已${toolToDelete.isOwner ? "删除" : "卸载"}`,
      });

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
