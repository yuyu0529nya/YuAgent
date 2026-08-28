"use client"

import { useState } from "react"
import dynamic from "next/dynamic"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Plus } from "lucide-react"
import { Button } from "@/components/ui/button"

// 自定义Hooks
import { useUserTools } from "./hooks/useUserTools"
import { useToolDialogs } from "./hooks/useToolDialogs"
import { useRecommendTools } from "./hooks/useRecommendTools"

// 页面部分组件
import { CreatedToolsSection } from "./components/sections/CreatedToolsSection"
import { InstalledToolsSection } from "./components/sections/InstalledToolsSection"
import { RecommendedToolsSection } from "./components/sections/RecommendedToolsSection"

import { UserTool } from "./utils/types"

const UserToolDetailDialog = dynamic(() => import("./components/dialogs/UserToolDetailDialog")
  .then(module => module.UserToolDetailDialog))
const DeleteToolDialog = dynamic(() => import("./components/dialogs/DeleteToolDialog")
  .then(module => module.DeleteToolDialog))
const GlobalInstallToolDialog = dynamic(() => import("@/components/tool/install-tool-dialog")
  .then(module => module.InstallToolDialog))
const PublishToolDialog = dynamic(() => import("./components/dialogs/PublishToolDialog")
  .then(module => module.PublishToolDialog))

export default function ToolsPage() {
  const router = useRouter()
  // 获取推荐工具数据
  const {
    tools,
    loading: marketToolsLoading,
    error: marketToolsError,
    fetchRecommendTools,
  } = useRecommendTools(10);
  
  // 获取用户工具数据
  const {
    ownedTools,
    installedTools,
    userToolsLoading,
    isDeletingTool,
    handleDeleteTool,
    fetchUserTools
  } = useUserTools();
  
  // 对话框状态管理
  const {
    selectedTool,
    // 安装确认
    isInstallDialogOpen,
    openInstallDialog,
    closeInstallDialog,
    
    // 用户工具详情
    isUserToolDetailOpen,
    selectedUserTool,
    openUserToolDetail,
    closeUserToolDetail,
    
    // 删除确认
    isDeleteDialogOpen,
    toolToDelete,
    openDeleteConfirm,
    closeDeleteDialog
  } = useToolDialogs();

  // 2. 添加状态 isPublishDialogOpen 和 toolToPublish
  const [isPublishDialogOpen, setIsPublishDialogOpen] = useState(false);
  const [toolToPublish, setToolToPublish] = useState<UserTool | null>(null);

  // 处理编辑工具
  const handleEditTool = (tool: UserTool, event?: React.MouseEvent) => {
    if (event) {
      event.stopPropagation();
    }
    router.push(`/tools/edit/${tool.id}`)
  };
  
  // 处理删除工具确认
  const handleConfirmDelete = async (): Promise<boolean> => {
    if (!toolToDelete) return false;
    
    const success = await handleDeleteTool(toolToDelete);
    
    if (success) {
      closeDeleteDialog();
    }
    
    return success || false;
  };

  // 3. 创建 handleOpenPublishDialog 函数
  const handleOpenPublishDialog = (tool: UserTool, event?: React.MouseEvent) => {
    if (event) {
      event.stopPropagation();
    }
    setToolToPublish(tool);
    setIsPublishDialogOpen(true);
  };

  // 处理工具安装成功
  const handleToolInstallSuccess = () => {
    // 刷新用户工具列表，确保新安装的工具会显示在"我安装的工具"中
    fetchUserTools();
  };

  return (
    <div className="py-6 min-h-screen bg-gray-50">
      <div className="container max-w-7xl mx-auto px-2">
        {/* 页面头部 */}
        <div className="flex items-center justify-between mb-8 bg-white p-6 rounded-lg shadow-sm">
          <div>
            <h1 className="text-3xl font-bold tracking-tight bg-gradient-to-r from-primary to-blue-600 bg-clip-text text-transparent">工具中心</h1>
            <p className="text-muted-foreground mt-1">探索和管理AI助手的扩展能力</p>
          </div>
          
          <Button asChild className="shadow-sm">
            <Link href="/tools/upload">
              <Plus className="mr-2 h-4 w-4" />
              上传工具
            </Link>
          </Button>
        </div>
        
        {/* 用户创建的工具部分 */}
        <CreatedToolsSection
          ownedTools={ownedTools}
          loading={userToolsLoading}
          onToolClick={openUserToolDetail}
          onEditClick={handleEditTool}
          onDeleteClick={openDeleteConfirm}
          onPublishClick={handleOpenPublishDialog}
        />
        
        {/* 用户安装的工具部分 */}
        <InstalledToolsSection
          installedTools={installedTools}
          loading={userToolsLoading}
          onToolClick={openUserToolDetail}
          onDeleteClick={openDeleteConfirm}
        />
        
        {/* 工具市场推荐部分 */}
        <RecommendedToolsSection
          tools={tools}
          loading={marketToolsLoading}
          error={marketToolsError}
          onInstallClick={openInstallDialog}
          onRetry={fetchRecommendTools}
        />
        
        {/* 用户工具详情对话框 */}
        <UserToolDetailDialog
          open={isUserToolDetailOpen}
          onOpenChange={closeUserToolDetail}
          tool={selectedUserTool}
          onDelete={handleDeleteTool}
        />
        
        {/* 工具安装确认对话框 */}
        <GlobalInstallToolDialog 
          open={isInstallDialogOpen}
          onOpenChange={closeInstallDialog}
          tool={selectedTool}
          version={selectedTool?.current_version}
          onSuccess={handleToolInstallSuccess}
        />

        {/* 删除工具确认对话框 */}
        <DeleteToolDialog
          open={isDeleteDialogOpen}
          onOpenChange={closeDeleteDialog}
          tool={toolToDelete}
          isDeleting={isDeletingTool}
          onConfirm={handleConfirmDelete}
        />

        {/* 6. 渲染 PublishToolDialog */}
        {toolToPublish && (
          <PublishToolDialog
            open={isPublishDialogOpen}
            onOpenChange={setIsPublishDialogOpen}
            tool={toolToPublish}
            onPublishSuccess={() => {
              setIsPublishDialogOpen(false);
              fetchUserTools(true);
            }}
          />
        )}
      </div>
    </div>
  )
}

