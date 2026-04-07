"use client";

import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Copy, ExternalLink } from "lucide-react";
import { AgentWidget } from "@/types/widget";

interface WidgetCodeDialogProps {
  open: boolean;
  onClose: () => void;
  widget: AgentWidget;
  onCopy: (code: string) => void;
}

export default function WidgetCodeDialog({ open, onClose, widget, onCopy }: WidgetCodeDialogProps) {
  const [activeTab, setActiveTab] = useState("iframe");

  const widgetUrl = `${typeof window !== "undefined" ? window.location.origin : ""}/widget/${widget.publicId}`;

  const generateEmbedCode = (type: string) => {
    switch (type) {
      case "iframe":
        return `<!-- YuAgent 智能助手小组件 - iframe 模式 -->
<iframe
  src="${widgetUrl}"
  width="400"
  height="600"
  frameborder="0"
  style="border: 1px solid #334155; border-radius: 12px;"
  allow="microphone">
</iframe>`;

      case "floating":
        return `<!-- YuAgent 智能助手小组件 - 悬浮窗模式 -->
<script>
  (function() {
    const agentButton = document.createElement('div');
    agentButton.innerHTML = 'AI 助手';
    agentButton.style.cssText = 'position:fixed;bottom:20px;right:20px;z-index:9999;' +
      'background:#6d62ff;color:#fff;padding:12px 20px;border-radius:999px;' +
      'cursor:pointer;box-shadow:0 10px 24px rgba(0,0,0,0.35);font-family:sans-serif;';

    agentButton.onclick = function() {
      const iframe = document.createElement('iframe');
      iframe.src = '${widgetUrl}';
      iframe.style.cssText = 'position:fixed;bottom:80px;right:20px;width:400px;' +
        'height:600px;border:none;border-radius:12px;z-index:10000;' +
        'box-shadow:0 16px 40px rgba(0,0,0,0.45);';

      const closeBtn = document.createElement('div');
      closeBtn.innerHTML = '×';
      closeBtn.style.cssText = 'position:fixed;bottom:685px;right:25px;width:24px;' +
        'height:24px;background:#ef4444;color:white;border-radius:50%;' +
        'text-align:center;line-height:24px;cursor:pointer;z-index:10001;' +
        'font-family:sans-serif;';

      closeBtn.onclick = function() {
        document.body.removeChild(iframe);
        document.body.removeChild(closeBtn);
        agentButton.style.display = 'block';
      };

      document.body.appendChild(iframe);
      document.body.appendChild(closeBtn);
      agentButton.style.display = 'none';
    };

    document.body.appendChild(agentButton);
  })();
</script>`;

      case "responsive":
        return `<!-- YuAgent 智能助手小组件 - 响应式模式 -->
<div style="width: 100%; max-width: 520px; margin: 0 auto;">
  <iframe
    src="${widgetUrl}"
    width="100%"
    height="600"
    frameborder="0"
    style="border: 1px solid #334155; border-radius: 12px; min-width: 300px;"
    allow="microphone">
  </iframe>
</div>`;

      default:
        return widget.widgetCode;
    }
  };

  const widgetCodes = {
    iframe: generateEmbedCode("iframe"),
    floating: generateEmbedCode("floating"),
    responsive: generateEmbedCode("responsive"),
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-h-[80vh] max-w-4xl overflow-auto">
        <DialogHeader>
          <DialogTitle>小组件嵌入代码</DialogTitle>
          <DialogDescription>复制以下代码到你的网站中，即可嵌入 "{widget.name}" 小组件。</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4 rounded-lg border border-white/10 bg-black/30 p-4">
            <div>
              <Label className="text-sm text-muted-foreground">小组件名称</Label>
              <p className="font-medium text-white">{widget.name}</p>
            </div>
            <div>
              <Label className="text-sm text-muted-foreground">状态</Label>
              <p className={`font-medium ${widget.enabled ? "text-emerald-400" : "text-rose-400"}`}>
                {widget.enabled ? "已启用" : "已禁用"}
              </p>
            </div>
            <div className="col-span-2">
              <Label className="text-sm text-muted-foreground">访问链接</Label>
              <div className="mt-1 flex items-center gap-2">
                <code className="flex-1 break-all rounded border border-white/10 bg-black/40 px-2 py-1 text-xs text-gray-200">
                  {widgetUrl}
                </code>
                <Button size="sm" variant="outline" onClick={() => window.open(widgetUrl, "_blank")}>
                  <ExternalLink className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>

          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList className="grid w-full grid-cols-3">
              <TabsTrigger value="iframe">固定 iframe</TabsTrigger>
              <TabsTrigger value="floating">悬浮窗口</TabsTrigger>
              <TabsTrigger value="responsive">响应式</TabsTrigger>
            </TabsList>

            {["iframe", "floating", "responsive"].map((tab) => (
              <TabsContent key={tab} value={tab} className="space-y-2">
                <Label>{tab === "iframe" ? "固定大小嵌入" : tab === "floating" ? "悬浮交互嵌入" : "响应式嵌入"}</Label>
                <div className="relative">
                  <pre className="max-h-48 overflow-auto rounded-lg border border-white/10 bg-black/40 p-4 text-xs">
                    <code className="block overflow-x-auto break-words whitespace-pre-wrap">
                      {widgetCodes[tab as keyof typeof widgetCodes]}
                    </code>
                  </pre>
                  <Button
                    size="sm"
                    className="absolute right-2 top-2"
                    onClick={() => onCopy(widgetCodes[tab as keyof typeof widgetCodes])}
                  >
                    <Copy className="mr-1 h-4 w-4" />
                    复制
                  </Button>
                </div>
              </TabsContent>
            ))}
          </Tabs>

          <div className="rounded-lg border border-violet-500/30 bg-violet-500/10 p-4">
            <h4 className="mb-2 font-medium text-violet-200">使用说明</h4>
            <ul className="space-y-1 text-sm text-violet-100/85">
              <li>1. 复制代码并粘贴到你的网页 HTML 中。</li>
              <li>2. 建议放在 <code>&lt;/body&gt;</code> 之前。</li>
              <li>3. 如需限制来源域名，请在组件配置里设置允许域名。</li>
            </ul>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            关闭
          </Button>
          <Button onClick={() => onCopy(widgetCodes[activeTab as keyof typeof widgetCodes])}>
            <Copy className="mr-2 h-4 w-4" />
            复制当前代码
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
