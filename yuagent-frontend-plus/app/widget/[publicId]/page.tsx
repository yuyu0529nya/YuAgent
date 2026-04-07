"use client";

import React, { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { AlertCircle, MessageCircle } from "lucide-react";
import { WidgetChatInterface } from "./components/WidgetChatInterface";
import { getWidgetInfoWithToast } from "@/lib/widget-service";

interface WidgetInfo {
  name: string;
  description?: string;
  agentName: string;
  agentAvatar?: string;
  welcomeMessage?: string;
  enabled: boolean;
  dailyLimit: number;
  dailyCalls: number;
  systemPrompt?: string;
  toolIds?: string[];
  knowledgeBaseIds?: string[];
}

export default function WidgetChatPage() {
  const params = useParams();
  const publicId = params.publicId as string;

  const [widgetInfo, setWidgetInfo] = useState<WidgetInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchWidgetInfo = useCallback(
    async (showLoading = true) => {
      try {
        if (showLoading) {
          setLoading(true);
        }
        setError(null);
        const response = await getWidgetInfoWithToast(publicId);
        if (response.code === 200) {
          setWidgetInfo(response.data);
        } else {
          setError(response.message || "获取小组件信息失败");
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "获取小组件信息失败");
      } finally {
        if (showLoading) {
          setLoading(false);
        }
      }
    },
    [publicId]
  );

  useEffect(() => {
    if (publicId) {
      fetchWidgetInfo(true);
    }
  }, [publicId, fetchWidgetInfo]);

  if (loading) {
    return (
        <div className="min-h-screen bg-[#0b1020] p-4 text-white">
          <div className="max-w-4xl mx-auto">
            <Card className="bg-[#11182d] border-[#29314a]">
              <CardHeader>
                <div className="flex items-center space-x-3">
                  <Skeleton className="h-12 w-12 rounded-full" />
                  <div>
                    <Skeleton className="h-6 w-32 mb-2" />
                    <Skeleton className="h-4 w-48" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <Skeleton className="h-[500px] w-full" />
              </CardContent>
            </Card>
          </div>
        </div>
    );
  }

  if (error || !widgetInfo) {
    return (
        <div className="min-h-screen bg-[#0b1020] flex items-center justify-center p-4 text-white">
          <Card className="max-w-md w-full bg-[#11182d] border-[#29314a]">
            <CardContent className="pt-6">
              <div className="flex items-center space-x-3 mb-4">
                <AlertCircle className="h-8 w-8 text-destructive" />
                <div>
                  <h3 className="text-lg font-semibold">无法访问</h3>
                  <p className="text-sm text-slate-300">{error || "小组件配置不存在或已被禁用"}</p>
                </div>
              </div>
              <Alert className="bg-[#0f172a] border-[#334155]">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>请检查链接是否正确，或联系网站管理员。</AlertDescription>
              </Alert>
            </CardContent>
          </Card>
        </div>
    );
  }

  if (!widgetInfo.enabled) {
    return (
        <div className="min-h-screen bg-[#0b1020] flex items-center justify-center p-4 text-white">
          <Card className="max-w-md w-full bg-[#11182d] border-[#29314a]">
            <CardContent className="pt-6">
              <div className="flex items-center space-x-3 mb-4">
                <AlertCircle className="h-8 w-8 text-amber-500" />
                <div>
                  <h3 className="text-lg font-semibold">暂不可用</h3>
                  <p className="text-sm text-slate-300">此聊天服务暂时不可用</p>
                </div>
              </div>
              <Alert className="bg-[#0f172a] border-[#334155]">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>服务已被禁用，请稍后再试或联系网站管理员。</AlertDescription>
              </Alert>
            </CardContent>
          </Card>
        </div>
    );
  }

  const hasReachedLimit = widgetInfo.dailyLimit !== -1 && widgetInfo.dailyCalls >= widgetInfo.dailyLimit;
  if (hasReachedLimit) {
    return (
        <div className="min-h-screen bg-[#0b1020] flex items-center justify-center p-4 text-white">
          <Card className="max-w-md w-full bg-[#11182d] border-[#29314a]">
            <CardContent className="pt-6">
              <div className="flex items-center space-x-3 mb-4">
                <AlertCircle className="h-8 w-8 text-amber-500" />
                <div>
                  <h3 className="text-lg font-semibold">今日调用已达上限</h3>
                  <p className="text-sm text-slate-300">今日调用次数已达到 {widgetInfo.dailyLimit} 次上限</p>
                </div>
              </div>
              <Alert className="bg-[#0f172a] border-[#334155]">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>请明天再来，或联系网站管理员增加调用额度。</AlertDescription>
              </Alert>
            </CardContent>
          </Card>
        </div>
    );
  }

  return (
      <div className="min-h-screen bg-[#0b1020] p-3 text-white">
        <div className="max-w-4xl mx-auto">
          <Card className="bg-[#11182d] border-[#29314a] shadow-xl">
            <CardHeader>
              <div className="flex items-center space-x-3">
                {widgetInfo.agentAvatar ? (
                    <img
                        src={widgetInfo.agentAvatar}
                        alt={widgetInfo.agentName}
                        className="h-12 w-12 rounded-full object-cover ring-2 ring-[#7c6af2]/40"
                    />
                ) : (
                    <div className="h-12 w-12 rounded-full bg-[#1e293b] flex items-center justify-center">
                      <MessageCircle className="h-6 w-6 text-[#a78bfa]" />
                    </div>
                )}
                <div>
                  <CardTitle className="text-xl text-white">{widgetInfo.name}</CardTitle>
                  {widgetInfo.description && <p className="text-sm text-slate-300 mt-1">{widgetInfo.description}</p>}
                </div>
              </div>

              {widgetInfo.dailyLimit !== -1 && (
                  <div className="mt-2 text-xs text-slate-300">
                    今日调用: {widgetInfo.dailyCalls} / {widgetInfo.dailyLimit}
                  </div>
              )}
            </CardHeader>

            <CardContent>
              <WidgetChatInterface
                  publicId={publicId}
                  agentName={widgetInfo.agentName}
                  agentAvatar={widgetInfo.agentAvatar}
                  welcomeMessage={widgetInfo.welcomeMessage}
                  systemPrompt={widgetInfo.systemPrompt}
                  toolIds={widgetInfo.toolIds}
                  knowledgeBaseIds={widgetInfo.knowledgeBaseIds}
                  onUsageUpdate={() => fetchWidgetInfo(false)}
              />
            </CardContent>
          </Card>
        </div>
      </div>
  );
}
