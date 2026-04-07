"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "@/hooks/use-toast";
import type { AuthSetting } from "@/lib/types/auth-config";
import { AUTH_FEATURE_KEY } from "@/lib/types/auth-config";
import { updateAuthSettingWithToast } from "@/lib/auth-config-service";
import { Edit } from "lucide-react";

interface SSOConfigDialogProps {
  setting: AuthSetting;
  onConfigUpdate?: (setting: AuthSetting) => void;
}

interface SSOConfig {
  clientId?: string;
  clientSecret?: string;
  redirectUri?: string;
}

export default function SSOConfigDialog({ setting, onConfigUpdate }: SSOConfigDialogProps) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [config, setConfig] = useState<SSOConfig>(() => {
    try {
      return setting.configData || {};
    } catch {
      return {};
    }
  });

  const isGitHub = setting.featureKey === AUTH_FEATURE_KEY.GITHUB_LOGIN;

  const handleSave = async () => {
    if (!isGitHub) {
      return;
    }

    setLoading(true);
    try {
      if (!config.clientId || !config.clientSecret || !config.redirectUri) {
        toast({
          title: "配置错误",
          description: "请完整填写 GitHub Client ID、Client Secret 和 Redirect URI",
          variant: "destructive",
        });
        return;
      }

      const response = await updateAuthSettingWithToast(setting.id, {
        configData: config,
      });

      if (response.code === 200) {
        toast({
          title: "配置保存成功",
          description: `${setting.featureName} 配置已更新`,
        });
        setOpen(false);
        onConfigUpdate?.(response.data);
        return;
      }

      toast({
        title: "配置保存失败",
        description: response.message || "请稍后重试",
        variant: "destructive",
      });
    } catch {
      toast({
        title: "配置保存失败",
        description: "请检查输入信息后重试",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  if (!isGitHub) {
    return null;
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="text-xs">
          <Edit className="h-3 w-3 mr-1" />
          配置
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{setting.featureName} 配置</DialogTitle>
          <DialogDescription>配置 GitHub OAuth 参数信息</DialogDescription>
        </DialogHeader>

        <Separator />

        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="clientId">Client ID *</Label>
              <Input
                id="clientId"
                placeholder="GitHub 应用的 Client ID"
                value={config.clientId || ""}
                onChange={(e) => setConfig((prev) => ({ ...prev, clientId: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="clientSecret">Client Secret *</Label>
              <Input
                id="clientSecret"
                type="password"
                placeholder="GitHub 应用的 Client Secret"
                value={config.clientSecret || ""}
                onChange={(e) => setConfig((prev) => ({ ...prev, clientSecret: e.target.value }))}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="redirectUri">Redirect URI *</Label>
            <Input
              id="redirectUri"
              placeholder="例如: http://localhost:3000/oauth/github/callback"
              value={config.redirectUri || ""}
              onChange={(e) => setConfig((prev) => ({ ...prev, redirectUri: e.target.value }))}
            />
            <p className="text-xs text-muted-foreground">
              这里需要和 GitHub OAuth 应用里的回调地址保持一致。
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">描述</Label>
            <Textarea id="description" value={setting.description || ""} readOnly />
          </div>
        </div>

        <Separator />

        <div className="flex justify-end space-x-2">
          <Button variant="outline" onClick={() => setOpen(false)} disabled={loading}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={loading}>
            {loading ? "保存中..." : "保存配置"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
