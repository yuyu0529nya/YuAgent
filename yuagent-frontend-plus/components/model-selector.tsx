"use client"

import React, { useEffect, useState } from "react"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { getDefaultModel } from "@/lib/api-services"
import { getAllModels, getUserSettings, updateUserSettings, type Model } from "@/lib/user-settings-service"
import { useToast } from "@/hooks/use-toast"

interface ModelSelectorProps {
  // 显示模式
  mode?: "preview" | "summary"
  // 自定义样式
  className?: string
  // 触发器样式
  triggerClassName?: string
  // 是否显示标签
  showLabel?: boolean
  // 标签文本
  labelText?: string
  // 描述文本
  descriptionText?: string
  // 禁用状态
  disabled?: boolean
}

export default function ModelSelector({
  mode = "preview",
  className = "",
  triggerClassName = "",
  showLabel = true,
  labelText = "当前使用模型",
  descriptionText = "切换模型将应用到预览对话",
  disabled = false,
}: ModelSelectorProps) {
  // 默认模型状态
  const [defaultModel, setDefaultModel] = useState<Model | null>(null)
  
  // 模型列表状态
  const [models, setModels] = useState<Model[]>([])
  const [isLoadingModels, setIsLoadingModels] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isUpdatingModel, setIsUpdatingModel] = useState(false)

  const { toast } = useToast()

  useEffect(() => {
    let cancelled = false

    async function loadModels() {
      setIsLoadingModels(true)
      setLoadError(null)

      try {
        const [defaultModelResponse, modelsResponse] = await Promise.all([getDefaultModel(), getAllModels()])
        if (cancelled) {
          return
        }

        if (defaultModelResponse.code === 200) {
          setDefaultModel(defaultModelResponse.data)
        }
        if (modelsResponse.code === 200 && Array.isArray(modelsResponse.data)) {
          setModels(modelsResponse.data)
        }

        const failedResponse = defaultModelResponse.code !== 200
          ? defaultModelResponse
          : modelsResponse.code !== 200
            ? modelsResponse
            : null
        if (failedResponse) {
          setLoadError(failedResponse.message || "模型信息加载失败")
        }
      } catch (error) {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : "模型信息加载失败")
        }
      } finally {
        if (!cancelled) {
          setIsLoadingModels(false)
        }
      }
    }

    void loadModels()
    return () => {
      cancelled = true
    }
  }, [])

  // 切换默认模型
  const handleChangeDefaultModel = async (modelId: string) => {
    if (disabled) return
    
    try {
      setIsUpdatingModel(true)
      
      // 更新用户设置
      const settingsResponse = await getUserSettings()
      const existingConfig = settingsResponse.code === 200 && settingsResponse.data?.settingConfig
        ? settingsResponse.data.settingConfig
        : { defaultModel: null, defaultOcrModel: null, defaultEmbeddingModel: null }
      const response = await updateUserSettings({
        settingConfig: {
          ...existingConfig,
          defaultModel: modelId
        }
      })
      
      if (response.code === 200) {
        // 更新本地状态
        const selectedModel = models.find(model => model.id === modelId)
        if (selectedModel) {
          setDefaultModel(selectedModel)
        }
        
        toast({
          title: "默认模型更新成功",
          description: `默认模型已成功更新为 ${selectedModel?.name || "未设置"}`,
        })
      } else {
 
        toast({
          title: "默认模型更新失败",
          description: response.message,
          variant: "destructive",
        })
      }
    } catch (error) {
 
      toast({
        title: "默认模型更新失败",
        description: "更新默认模型时发生错误",
        variant: "destructive",
      })
    } finally {
      setIsUpdatingModel(false)
    }
  }

  // 渲染预览模式
  if (mode === "preview") {
    return (
      <div className={`p-3 bg-blue-50 rounded-lg border border-blue-200 ${className}`}>
        <div className="flex items-center justify-between">
          {showLabel && (
            <div className="flex flex-col">
              <span className="text-sm font-medium text-blue-900">{labelText}</span>
              {descriptionText && (
                <span className="text-xs text-blue-700">{descriptionText}</span>
              )}
            </div>
          )}
          <div className="flex items-center gap-2">
            {isLoadingModels ? (
              <Skeleton className="h-8 w-40" />
            ) : defaultModel ? (
              <Select 
                value={defaultModel.id} 
                onValueChange={handleChangeDefaultModel}
                disabled={isUpdatingModel || disabled}
              >
                <SelectTrigger className={`w-40 h-8 bg-white ${triggerClassName}`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {models.filter(model => model.status && model.type === 'CHAT').map((model) => (
                    <SelectItem key={model.id} value={model.id}>
                      <div className="flex flex-col">
                        <span className="font-medium">{model.name}</span>
                        {model.providerName && (
                          <span className="text-xs text-muted-foreground">
                            {model.providerName}
                          </span>
                        )}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <div className="flex items-center gap-2">
                <span className="text-sm text-blue-700">{loadError ? "模型信息暂不可用" : "未设置默认模型"}</span>
                {models.length > 0 && (
                  <Select 
                    onValueChange={handleChangeDefaultModel}
                    disabled={isUpdatingModel || disabled}
                  >
                    <SelectTrigger className={`w-40 h-8 bg-white ${triggerClassName}`}>
                      <SelectValue placeholder="选择模型" />
                    </SelectTrigger>
                    <SelectContent>
                      {models.filter(model => model.status && model.type === 'CHAT').map((model) => (
                        <SelectItem key={model.id} value={model.id}>
                          <div className="flex flex-col">
                            <span className="font-medium">{model.name}</span>
                            {model.providerName && (
                              <span className="text-xs text-muted-foreground">
                                {model.providerName}
                              </span>
                            )}
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </div>
            )}
            {isUpdatingModel && (
              <div className="text-xs text-blue-600">更新中...</div>
            )}
          </div>
        </div>
      </div>
    )
  }

  // 渲染摘要模式
  return (
    <div className={`flex justify-between items-center ${className}`}>
      {showLabel && (
        <span className="text-sm text-muted-foreground">{labelText}</span>
      )}
      <div className="flex items-center gap-2">
        {isLoadingModels ? (
          <Skeleton className="h-4 w-20" />
        ) : defaultModel ? (
          <Select 
            value={defaultModel.id} 
            onValueChange={handleChangeDefaultModel}
            disabled={isUpdatingModel || disabled}
          >
            <SelectTrigger className={`w-40 h-8 text-xs ${triggerClassName}`}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {models.filter(model => model.status && model.type === 'CHAT').map((model) => (
                <SelectItem key={model.id} value={model.id}>
                  <div className="flex flex-col">
                    <span className="font-medium">{model.name}</span>
                    {model.providerName && (
                      <span className="text-xs text-muted-foreground">
                        {model.providerName}
                      </span>
                    )}
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <div className="flex items-center gap-1">
            <span className="text-sm text-muted-foreground">{loadError ? "暂不可用" : "未设置"}</span>
            {models.length > 0 && (
              <Select 
                onValueChange={handleChangeDefaultModel}
                disabled={isUpdatingModel || disabled}
              >
                <SelectTrigger className={`w-32 h-6 text-xs ${triggerClassName}`}>
                  <SelectValue placeholder="选择" />
                </SelectTrigger>
                <SelectContent>
                  {models.filter(model => model.status && model.type === 'CHAT').map((model) => (
                    <SelectItem key={model.id} value={model.id}>
                      <div className="flex flex-col">
                        <span className="font-medium">{model.name}</span>
                        {model.providerName && (
                          <span className="text-xs text-muted-foreground">
                            {model.providerName}
                          </span>
                        )}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>
        )}
        {isUpdatingModel && (
          <div className="text-xs text-muted-foreground">更新中...</div>
        )}
      </div>
    </div>
  )
} 
