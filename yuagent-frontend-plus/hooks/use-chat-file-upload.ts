"use client"

import { useCallback, useRef, useState, type ChangeEvent, type Dispatch, type SetStateAction } from "react"

import { toast } from "@/hooks/use-toast"
import { uploadMultipleFiles, type UploadFileInfo, type UploadResult } from "@/lib/file-upload-service"

export interface ChatFile {
  id: string
  name: string
  type: string
  size: number
  url: string
  uploadProgress?: number
}

interface UseChatFileUploadOptions {
  multiModal: boolean
  setUploadedFiles: Dispatch<SetStateAction<ChatFile[]>>
}

function createTemporaryFileId(index: number) {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID()
  }

  return `${Date.now()}-${index}-${Math.random().toString(36).slice(2)}`
}

export function useChatFileUpload({ multiModal, setUploadedFiles }: UseChatFileUploadOptions) {
  const [isUploadingFiles, setIsUploadingFiles] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const removeFile = useCallback((fileId: string) => {
    setUploadedFiles((files) => files.filter((file) => file.id !== fileId))
  }, [setUploadedFiles])

  const triggerFileSelect = useCallback(() => {
    if (!multiModal) {
      toast({
        title: "多模态功能未启用",
        description: "请在 Agent 配置中启用多模态功能",
        variant: "destructive",
      })
      return
    }

    fileInputRef.current?.click()
  }, [multiModal])

  const handleFileUpload = useCallback(async (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files
    if (!files || files.length === 0) {
      return
    }

    if (!multiModal) {
      toast({
        title: "多模态功能未启用",
        description: "请在 Agent 配置中启用多模态功能",
        variant: "destructive",
      })
      return
    }

    const uploadFiles: UploadFileInfo[] = Array.from(files).map((file) => ({
      file,
      fileName: file.name,
      fileType: file.type,
      fileSize: file.size,
    }))
    const temporaryFiles: ChatFile[] = uploadFiles.map((file, index) => ({
      id: createTemporaryFileId(index),
      name: file.fileName,
      type: file.fileType,
      size: file.fileSize,
      url: "",
      uploadProgress: 0,
    }))
    const completedFileIDs = new Set<string>()
    let hasShownUploadError = false

    setIsUploadingFiles(true)
    setUploadedFiles((currentFiles) => [...currentFiles, ...temporaryFiles])

    try {
      const uploadResults = await uploadMultipleFiles(
        uploadFiles,
        (fileIndex, progress) => {
          const temporaryFileID = temporaryFiles[fileIndex].id
          setUploadedFiles((currentFiles) => currentFiles.map((file) => (
            file.id === temporaryFileID ? { ...file, uploadProgress: progress } : file
          )))
        },
        (fileIndex, result: UploadResult) => {
          const temporaryFileID = temporaryFiles[fileIndex].id
          completedFileIDs.add(temporaryFileID)
          setUploadedFiles((currentFiles) => currentFiles.map((file) => (
            file.id === temporaryFileID
              ? {
                  ...file,
                  url: result.url,
                  uploadProgress: 100,
                  name: result.fileName,
                  type: result.fileType,
                  size: result.fileSize,
                }
              : file
          )))
        },
        (fileIndex, error) => {
          hasShownUploadError = true
          const temporaryFileID = temporaryFiles[fileIndex].id
          removeFile(temporaryFileID)
          toast({
            title: "文件上传失败",
            description: `${uploadFiles[fileIndex].fileName}: ${error.message}`,
            variant: "destructive",
          })
        },
      )

      if (uploadResults.length > 0) {
        toast({
          title: "文件上传成功",
          description: `已上传 ${uploadResults.length} 个文件`,
        })
      }
    } catch (error) {
      setUploadedFiles((currentFiles) => currentFiles.filter((file) => !temporaryFiles.some((temporaryFile) => (
        temporaryFile.id === file.id && !completedFileIDs.has(temporaryFile.id)
      ))))

      if (!hasShownUploadError) {
        toast({
          title: "文件上传失败",
          description: error instanceof Error ? error.message : "请重试",
          variant: "destructive",
        })
      }
    } finally {
      setIsUploadingFiles(false)
      if (fileInputRef.current) {
        fileInputRef.current.value = ""
      }
    }
  }, [multiModal, removeFile, setUploadedFiles])

  return {
    fileInputRef,
    handleFileUpload,
    isUploadingFiles,
    removeFile,
    triggerFileSelect,
  }
}
