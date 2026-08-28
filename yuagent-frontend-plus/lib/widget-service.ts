import { type ApiResponse } from './http-client';
import { publicHttpClient, type RequestConfig } from './public-http-client';
import { withToast } from './toast-utils';
import type { PublicWidgetInfo } from '@/types/widget';

/**
 * 公开小组件接口无需携带登录态。管理端小组件接口统一由
 * agent-widget-service 提供，避免两套相同端点与类型定义漂移。
 */
export async function getWidgetInfo(
  publicId: string,
  config?: Pick<RequestConfig, "signal">,
): Promise<ApiResponse<PublicWidgetInfo>> {
  return publicHttpClient.get<ApiResponse<PublicWidgetInfo>>(`/widget/${publicId}/info`, config);
}

export const getWidgetInfoWithToast = withToast(getWidgetInfo, {
  showSuccessToast: false,
  showErrorToast: true,
  errorTitle: "获取Widget信息失败",
});
