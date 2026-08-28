import { useState } from 'react';
import { UserTool } from '../utils/types';
import type { Tool } from '@/types/tool';

interface UseToolDialogsResult {
  selectedTool: Tool | null;
  
  // 安装确认对话框
  isInstallDialogOpen: boolean;
  openInstallDialog: (tool: Tool) => void;
  closeInstallDialog: () => void;
  
  // 用户工具详情对话框
  isUserToolDetailOpen: boolean;
  selectedUserTool: UserTool | null;
  openUserToolDetail: (tool: UserTool) => void;
  closeUserToolDetail: () => void;
  
  // 删除确认对话框
  isDeleteDialogOpen: boolean;
  toolToDelete: UserTool | null;
  openDeleteConfirm: (tool: UserTool, e?: React.MouseEvent) => void;
  closeDeleteDialog: () => void;
}

export function useToolDialogs(): UseToolDialogsResult {
  const [selectedTool, setSelectedTool] = useState<Tool | null>(null);
  
  // 安装确认对话框
  const [isInstallDialogOpen, setIsInstallDialogOpen] = useState(false);
  
  // 用户工具详情对话框
  const [isUserToolDetailOpen, setIsUserToolDetailOpen] = useState(false);
  const [selectedUserTool, setSelectedUserTool] = useState<UserTool | null>(null);
  
  // 删除确认对话框
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
  const [toolToDelete, setToolToDelete] = useState<UserTool | null>(null);
  
  // 打开安装确认对话框
  const openInstallDialog = (tool: Tool) => {
    setSelectedTool(tool);
    setIsInstallDialogOpen(true);
  };
  
  // 关闭安装确认对话框
  const closeInstallDialog = () => {
    setIsInstallDialogOpen(false);
  };
  
  // 打开用户工具详情
  const openUserToolDetail = (tool: UserTool) => {
    setSelectedUserTool(tool);
    setIsUserToolDetailOpen(true);
  };
  
  // 关闭用户工具详情
  const closeUserToolDetail = () => {
    setIsUserToolDetailOpen(false);
  };
  
  // 打开删除确认对话框
  const openDeleteConfirm = (tool: UserTool, e?: React.MouseEvent) => {
    if (e) {
      e.stopPropagation(); // 防止触发卡片点击事件
    }
    setToolToDelete(tool);
    setIsDeleteDialogOpen(true);
  };
  
  // 关闭删除确认对话框
  const closeDeleteDialog = () => {
    setIsDeleteDialogOpen(false);
  };

  return {
    selectedTool,
    // 安装确认对话框
    isInstallDialogOpen,
    openInstallDialog,
    closeInstallDialog,
    
    // 用户工具详情对话框
    isUserToolDetailOpen,
    selectedUserTool,
    openUserToolDetail,
    closeUserToolDetail,
    
    // 删除确认对话框
    isDeleteDialogOpen,
    toolToDelete,
    openDeleteConfirm,
    closeDeleteDialog
  };
} 
