"use client";

import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { toast } from "@/hooks/use-toast";
import { getAllAuthSettingsWithToast, toggleAuthSettingWithToast } from "@/lib/auth-config-service";
import type { AuthSetting } from "@/lib/types/auth-config";
import { AUTH_FEATURE_KEY, FEATURE_TYPE } from "@/lib/types/auth-config";
import { ChevronRight, Github, Key, Settings, Shield, Users } from "lucide-react";
import SSOConfigDialog from "./components/SSOConfigDialog";

export default function AuthSettingsPage() {
  const [authSettings, setAuthSettings] = useState<AuthSetting[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAuthSettings();
  }, []);

  const loadAuthSettings = async () => {
    setLoading(true);
    try {
      const response = await getAllAuthSettingsWithToast();
      if (response.code === 200) {
        setAuthSettings(response.data);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async (setting: AuthSetting) => {
    try {
      const response = await toggleAuthSettingWithToast(setting.id);
      if (response.code === 200) {
        const newEnabled = !setting.enabled;
        setAuthSettings((prev) =>
          prev.map((item) => (item.id === setting.id ? { ...item, enabled: newEnabled } : item))
        );
        toast({
          title: "设置更新成功",
          description: `${setting.featureName} 已${newEnabled ? "启用" : "禁用"}`,
        });
      }
    } catch {
      toast({
        title: "设置更新失败",
        description: "请稍后重试",
        variant: "destructive",
      });
    }
  };

  const getFeatureIcon = (featureKey: string) => {
    switch (featureKey) {
      case AUTH_FEATURE_KEY.NORMAL_LOGIN:
        return <Key className="h-5 w-5" />;
      case AUTH_FEATURE_KEY.GITHUB_LOGIN:
        return <Github className="h-5 w-5" />;
      case AUTH_FEATURE_KEY.USER_REGISTER:
        return <Users className="h-5 w-5" />;
      default:
        return <Settings className="h-5 w-5" />;
    }
  };

  const visibleSettings = authSettings.filter(
    (setting) => setting.featureKey !== AUTH_FEATURE_KEY.COMMUNITY_LOGIN
  );

  const groupedSettings = visibleSettings.reduce((acc, setting) => {
    if (!acc[setting.featureType]) {
      acc[setting.featureType] = [];
    }
    acc[setting.featureType].push(setting);
    return acc;
  }, {} as Record<string, AuthSetting[]>);

  if (loading) {
    return (
      <div className="container mx-auto py-6">
        <div className="space-y-6">
          <div className="h-8 bg-gray-200 rounded animate-pulse" />
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-32 bg-gray-200 rounded animate-pulse" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">认证设置</h1>
          <p className="text-gray-600 mt-1">管理用户登录和注册方式的配置</p>
        </div>
      </div>

      <div className="space-y-6">
        {Object.entries(groupedSettings)
          .sort(([a], [b]) => (a === FEATURE_TYPE.LOGIN ? -1 : b === FEATURE_TYPE.LOGIN ? 1 : 0))
          .map(([featureType, settings]) => (
            <Card key={featureType}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Settings className="h-5 w-5" />
                  {featureType === FEATURE_TYPE.LOGIN ? "登录功能" : "注册功能"}
                </CardTitle>
                <CardDescription>
                  {featureType === FEATURE_TYPE.LOGIN ? "配置可用的登录方式" : "配置用户注册功能"}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {settings
                  .sort((a, b) => a.displayOrder - b.displayOrder)
                  .map((setting, index) => (
                    <div key={setting.id}>
                      {index > 0 && <Separator className="my-4" />}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="flex-shrink-0">{getFeatureIcon(setting.featureKey)}</div>
                          <div className="flex-1">
                            <div className="flex items-center gap-2">
                              <h3 className="font-medium text-gray-900">{setting.featureName}</h3>
                              <Badge
                                variant={setting.enabled ? "default" : "secondary"}
                                className={setting.enabled ? "bg-green-100 text-green-800" : ""}
                              >
                                {setting.enabled ? "已启用" : "已禁用"}
                              </Badge>
                            </div>
                            <p className="text-sm text-gray-600 mt-1">{setting.description}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          {setting.featureKey === AUTH_FEATURE_KEY.GITHUB_LOGIN && (
                            <SSOConfigDialog setting={setting} onConfigUpdate={loadAuthSettings} />
                          )}
                          <Switch
                            checked={setting.enabled}
                            onCheckedChange={() => handleToggle(setting)}
                          />
                        </div>
                      </div>
                    </div>
                  ))}
              </CardContent>
            </Card>
          ))}
      </div>

      <Card className="bg-blue-50 border-blue-200">
        <CardHeader>
          <CardTitle className="text-blue-900 text-base flex items-center gap-2">
            <Shield className="h-4 w-4" />
            配置说明
          </CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-blue-800 space-y-2">
          <div className="flex items-start gap-2">
            <ChevronRight className="h-4 w-4 mt-0.5 flex-shrink-0" />
            <span>普通登录：用户使用邮箱、手机号和密码进行登录</span>
          </div>
          <div className="flex items-start gap-2">
            <ChevronRight className="h-4 w-4 mt-0.5 flex-shrink-0" />
            <span>GitHub 登录：用户通过 GitHub 账号进行 OAuth 登录</span>
          </div>
          <div className="flex items-start gap-2">
            <ChevronRight className="h-4 w-4 mt-0.5 flex-shrink-0" />
            <span>用户注册：控制是否允许新用户注册账号</span>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
