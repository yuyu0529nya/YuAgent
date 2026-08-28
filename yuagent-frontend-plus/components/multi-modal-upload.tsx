"use client"

import React from 'react'
import { Button } from '@/components/ui/button'
import { Loader2, Paperclip, X } from 'lucide-react'
import { useChatFileUpload, type ChatFile } from '@/hooks/use-chat-file-upload'

export type { ChatFile } from '@/hooks/use-chat-file-upload'

interface MultiModalUploadProps {
  multiModal?: boolean // 是否启用多模态功能
  uploadedFiles: ChatFile[] // 已上传的文件列表
  setUploadedFiles: React.Dispatch<React.SetStateAction<ChatFile[]>> // 设置文件列表的函数
  disabled?: boolean // 是否禁用
  className?: string // 额外的样式类
  showFileList?: boolean // 是否显示文件列表，默认为true
}

export default function MultiModalUpload({
  multiModal = false,
  uploadedFiles,
  setUploadedFiles,
  disabled = false,
  className = "",
  showFileList = true
}: MultiModalUploadProps) {
  const { fileInputRef, handleFileUpload, isUploadingFiles, removeFile, triggerFileSelect } = useChatFileUpload({
    multiModal,
    setUploadedFiles,
  })

  return (
    <div className={`${className}`}>
      {/* 上传按钮 - 更紧凑的设计 */}
      {multiModal && (
        <div className="flex flex-col items-start gap-1">
          {/* 已上传文件列表 - 紧凑显示 */}
          {showFileList && uploadedFiles.length > 0 && (
            <div className="flex flex-wrap gap-1 max-w-xs">
              {uploadedFiles.map((file) => (
                <div
                  key={file.id}
                  className="flex items-center gap-1 px-2 py-1 bg-blue-50 rounded text-xs border border-blue-200"
                >
                  <div className="flex-shrink-0 w-4 h-4 bg-blue-100 rounded flex items-center justify-center">
                    {file.type.startsWith('image/') ? (
                      <span className="text-xs">🖼️</span>
                    ) : (
                      <span className="text-xs">📄</span>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-medium text-gray-900 truncate max-w-20">
                      {file.name}
                    </p>
                    {/* 上传进度条 */}
                    {file.uploadProgress !== undefined && file.uploadProgress < 100 && (
                      <div className="w-full bg-gray-200 rounded-full h-0.5 mt-0.5">
                        <div
                          className="bg-blue-600 h-0.5 rounded-full transition-all duration-300"
                          style={{ width: `${file.uploadProgress}%` }}
                        />
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => removeFile(file.id)}
                    className="flex-shrink-0 hover:bg-blue-200 rounded p-0.5"
                    disabled={disabled}
                  >
                    <X className="h-2.5 w-2.5 text-gray-500" />
                  </button>
                </div>
              ))}
            </div>
          )}
          
          {/* 上传按钮 */}
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/*,application/pdf,.doc,.docx,.txt"
            onChange={handleFileUpload}
            className="hidden"
            disabled={disabled}
          />
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={triggerFileSelect}
            disabled={disabled || isUploadingFiles}
            className="h-10 w-10 rounded-xl p-0 hover:bg-gray-100"
          >
            {isUploadingFiles ? (
              <Loader2 className="h-5 w-5 animate-spin text-gray-500" />
            ) : (
              <Paperclip className="h-5 w-5 text-gray-500" />
            )}
          </Button>
        </div>
      )}
    </div>
  )
} 
