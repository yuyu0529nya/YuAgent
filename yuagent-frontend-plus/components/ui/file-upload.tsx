"use client"

import React, { useState, useRef, useCallback, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Upload, Loader2, Trash, Image as ImageIcon } from 'lucide-react'
import { toast } from '@/hooks/use-toast'
import { uploadSingleFile, type UploadResult } from '@/lib/file-upload-service'
import { resolveAssetUrl } from '@/lib/asset-url'

export interface FileUploadProps {
  value?: string | null
  onChange?: (url: string | null) => void
  onPreviewChange?: (previewUrl: string | null) => void
  variant?: 'avatar' | 'square' | 'rectangle'
  size?: 'sm' | 'md' | 'lg' | 'xl'
  placeholder?: React.ReactNode
  accept?: string
  maxSize?: number
  disabled?: boolean
  uploadText?: string
  removeText?: string
  changeText?: string
  className?: string
  containerClassName?: string
  onUploadStart?: () => void
  onUploadComplete?: (result: UploadResult) => void
  onUploadError?: (error: Error) => void
  onRemove?: () => void
}

const VARIANT_STYLES = {
  avatar: {
    container: 'rounded-full overflow-hidden',
    sizes: {
      sm: 'h-8 w-8',
      md: 'h-12 w-12',
      lg: 'h-16 w-16',
      xl: 'h-20 w-20',
    },
  },
  square: {
    container: 'rounded-md overflow-hidden',
    sizes: {
      sm: 'h-12 w-12',
      md: 'h-16 w-16',
      lg: 'h-20 w-20',
      xl: 'h-24 w-24',
    },
  },
  rectangle: {
    container: 'rounded-md overflow-hidden',
    sizes: {
      sm: 'h-12 w-20',
      md: 'h-16 w-24',
      lg: 'h-20 w-32',
      xl: 'h-24 w-40',
    },
  },
}

export default function FileUpload({
  value,
  onChange,
  onPreviewChange,
  variant = 'square',
  size = 'md',
  placeholder,
  accept = 'image/*',
  maxSize = 2 * 1024 * 1024,
  disabled = false,
  uploadText = '上传',
  removeText = '移除',
  changeText = '更换',
  className = '',
  containerClassName = '',
  onUploadStart,
  onUploadComplete,
  onUploadError,
  onRemove,
}: FileUploadProps) {
  const [isUploading, setIsUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string | null>(null)
  const [remoteImageFailed, setRemoteImageFailed] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const variantConfig = VARIANT_STYLES[variant]
  const sizeClass = variantConfig.sizes[size]
  const containerClass = variantConfig.container

  const defaultPlaceholder =
    variant === 'avatar' ? (
      <Upload className="h-4 w-4 text-muted-foreground" />
    ) : (
      <div className="flex flex-col items-center justify-center text-muted-foreground">
        <ImageIcon className="h-6 w-6 mb-1" />
        <span className="text-xs">上传图片</span>
      </div>
    )

  useEffect(() => {
    setRemoteImageFailed(false)
  }, [value])

  const triggerFileSelect = useCallback(() => {
    if (disabled || isUploading) return
    fileInputRef.current?.click()
  }, [disabled, isUploading])

  const handleFileUpload = useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return

      event.target.value = ''

      if (accept && !file.type.match(accept.replace('*', '.*'))) {
        const errorMsg = '不支持的文件类型'
        toast({ variant: 'destructive', title: '文件类型错误', description: errorMsg })
        onUploadError?.(new Error(errorMsg))
        return
      }

      if (file.size > maxSize) {
        const errorMsg = `文件大小不能超过 ${(maxSize / 1024 / 1024).toFixed(1)}MB`
        toast({ variant: 'destructive', title: '文件过大', description: errorMsg })
        onUploadError?.(new Error(errorMsg))
        return
      }

      const previousValue = value ?? null

      try {
        const previewUrl = URL.createObjectURL(file)
        setLocalPreviewUrl(previewUrl)
        onPreviewChange?.(previewUrl)
      } catch {
        setLocalPreviewUrl(null)
        onPreviewChange?.(null)
      }
      setRemoteImageFailed(false)

      try {
        setIsUploading(true)
        setUploadProgress(0)
        onUploadStart?.()

        const result = await uploadSingleFile(file, (progress) => {
          setUploadProgress(progress)
        })

        onChange?.(result.url)
        onUploadComplete?.(result)
        toast({ title: '上传成功', description: '文件已成功上传' })
      } catch (error) {
        const errorMsg = error instanceof Error ? error.message : '上传失败'
        toast({ variant: 'destructive', title: '上传失败', description: errorMsg })
        onChange?.(previousValue)
        onUploadError?.(error instanceof Error ? error : new Error(errorMsg))
      } finally {
        setIsUploading(false)
        setUploadProgress(0)
      }
    },
    [accept, maxSize, onChange, onPreviewChange, onUploadStart, onUploadComplete, onUploadError, value]
  )

  const handleRemove = useCallback(() => {
    if (disabled || isUploading) return

    onChange?.(null)
    onPreviewChange?.(null)
    onRemove?.()
    setLocalPreviewUrl(null)
    setRemoteImageFailed(false)

    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }, [disabled, isUploading, onChange, onPreviewChange, onRemove])

  const remoteImage = typeof value === 'string' && value.trim() ? resolveAssetUrl(value) || value : null
  const hasValidImage = !!(localPreviewUrl || remoteImage)
  const displaySrc = localPreviewUrl || (remoteImageFailed ? null : remoteImage)

  return (
    <div className={`flex items-center gap-3 ${containerClassName}`}>
      <div className={`${containerClass} ${sizeClass} border flex items-center justify-center bg-muted/20 relative ${className}`}>
        {displaySrc ? (
          <img
            src={displaySrc}
            alt=""
            className="h-full w-full object-cover"
            onLoad={() => setRemoteImageFailed(false)}
            onError={() => {
              if (displaySrc === remoteImage) {
                setRemoteImageFailed(true)
              }
            }}
          />
        ) : (
          <div className="flex items-center justify-center h-full w-full">{placeholder || defaultPlaceholder}</div>
        )}

        {isUploading && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
            <div className="text-white text-center">
              <Loader2 className="h-4 w-4 animate-spin mx-auto mb-1" />
              <div className="text-xs">{uploadProgress}%</div>
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Button type="button" variant="outline" size="sm" onClick={triggerFileSelect} disabled={disabled || isUploading} className="text-xs">
          {isUploading ? (
            <>
              <Loader2 className="h-3 w-3 mr-1 animate-spin" />
              上传中...
            </>
          ) : (
            <>
              <Upload className="h-3 w-3 mr-1" />
              {hasValidImage ? changeText : uploadText}
            </>
          )}
        </Button>

        {hasValidImage && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRemove}
            disabled={disabled || isUploading}
            className="text-xs hover:bg-red-50 hover:text-red-600 hover:border-red-200"
          >
            <Trash className="h-3 w-3 mr-1" />
            {removeText}
          </Button>
        )}
      </div>

      <input ref={fileInputRef} type="file" accept={accept} onChange={handleFileUpload} className="hidden" />
    </div>
  )
}
