import { httpClient } from './http-client';
import { publicHttpClient } from './public-http-client';
import { withToast } from './toast-utils';

export interface Widget {
  id: string;
  publicId: string;
  name: string;
  description?: string;
  enabled: boolean;
  dailyLimit: number;
  dailyCalls: number;
  allowedDomains: string[];
  widgetCode: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWidgetRequest {
  name: string;
  description?: string;
  dailyLimit: number;
  allowedDomains: string[];
  modelId: string;
  providerId?: string;
}

export interface UpdateWidgetRequest {
  name?: string;
  description?: string;
  dailyLimit?: number;
  allowedDomains?: string[];
  modelId?: string;
  providerId?: string;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

const API_BASE = '/agents';

function normalizeWidget(widget: Widget): Widget {
  return {
    ...widget,
    dailyCalls: widget?.dailyCalls ?? 0,
  };
}

function normalizeWidgetList(widgets: Widget[] | undefined): Widget[] {
  return (widgets || []).map(normalizeWidget);
}

/**
 * 获取Agent的所有Widget
 */
export async function getAgentWidgets(agentId: string): Promise<ApiResponse<Widget[]>> {
  try {
    const response = await httpClient.get<ApiResponse<Widget[]>>(`${API_BASE}/${agentId}/widgets`);
    return {
      ...response,
      data: normalizeWidgetList(response.data),
    };
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "获取Widget列表失败",
      data: [],
      timestamp: Date.now(),
    };
  }
}

/**
 * 创建Widget
 */
export async function createWidget(agentId: string, request: CreateWidgetRequest): Promise<ApiResponse<Widget>> {
  try {
    const response = await httpClient.post<ApiResponse<Widget>>(`${API_BASE}/${agentId}/widgets`, request);
    return {
      ...response,
      data: normalizeWidget(response.data),
    };
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "创建Widget失败",
      data: {} as Widget,
      timestamp: Date.now(),
    };
  }
}

/**
 * 获取Widget详情
 */
export async function getWidgetDetail(agentId: string, widgetId: string): Promise<ApiResponse<Widget>> {
  try {
    const response = await httpClient.get<ApiResponse<Widget>>(`${API_BASE}/${agentId}/widgets/${widgetId}`);
    return {
      ...response,
      data: normalizeWidget(response.data),
    };
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "获取Widget详情失败",
      data: {} as Widget,
      timestamp: Date.now(),
    };
  }
}

/**
 * 更新Widget
 */
export async function updateWidget(agentId: string, widgetId: string, request: UpdateWidgetRequest): Promise<ApiResponse<Widget>> {
  try {
    const response = await httpClient.put<ApiResponse<Widget>>(`${API_BASE}/${agentId}/widgets/${widgetId}`, request);
    return {
      ...response,
      data: normalizeWidget(response.data),
    };
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "更新Widget失败",
      data: {} as Widget,
      timestamp: Date.now(),
    };
  }
}

/**
 * 切换Widget状态
 */
export async function toggleWidgetStatus(agentId: string, widgetId: string): Promise<ApiResponse<Widget>> {
  try {
    const response = await httpClient.post<ApiResponse<Widget>>(`${API_BASE}/${agentId}/widgets/${widgetId}/toggle-status`);
    return {
      ...response,
      data: normalizeWidget(response.data),
    };
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "切换Widget状态失败",
      data: {} as Widget,
      timestamp: Date.now(),
    };
  }
}

/**
 * 删除Widget
 */
export async function deleteWidget(agentId: string, widgetId: string): Promise<ApiResponse<void>> {
  try {
    return await httpClient.delete<ApiResponse<void>>(`${API_BASE}/${agentId}/widgets/${widgetId}`);
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "删除Widget失败",
      data: undefined,
      timestamp: Date.now(),
    };
  }
}

/**
 * 获取用户所有Widget
 */
export async function getUserWidgets(): Promise<ApiResponse<Widget[]>> {
  try {
    const response = await httpClient.get<ApiResponse<Widget[]>>('/widgets/user');
    return {
      ...response,
      data: normalizeWidgetList(response.data),
    };
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "获取用户Widget列表失败",
      data: [],
      timestamp: Date.now(),
    };
  }
}

// 带Toast提示的API调用
export const getAgentWidgetsWithToast = withToast(getAgentWidgets, {
  showErrorToast: true,
  errorTitle: "获取Widget列表失败"
});

export const createWidgetWithToast = withToast(createWidget, {
  showSuccessToast: true,
  showErrorToast: true,
  successTitle: "创建Widget成功",
  errorTitle: "创建Widget失败"
});

export const updateWidgetWithToast = withToast(updateWidget, {
  showSuccessToast: true,
  showErrorToast: true,
  successTitle: "更新Widget成功",
  errorTitle: "更新Widget失败"
});

export const toggleWidgetStatusWithToast = withToast(toggleWidgetStatus, {
  showSuccessToast: true,
  showErrorToast: true,
  successTitle: "Widget状态已更新",
  errorTitle: "切换Widget状态失败"
});

export const deleteWidgetWithToast = withToast(deleteWidget, {
  showSuccessToast: true,
  showErrorToast: true,
  successTitle: "删除Widget成功",
  errorTitle: "删除Widget失败"
});

/**
 * 获取Widget公开信息（通过publicId）- 使用公开客户端，无需认证
 */
export async function getWidgetInfo(publicId: string): Promise<ApiResponse<any>> {
  try {
    return await publicHttpClient.get<ApiResponse<any>>(`/widget/${publicId}/info`);
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "获取Widget信息失败",
      data: null,
      timestamp: Date.now(),
    };
  }
}

export const getUserWidgetsWithToast = withToast(getUserWidgets, {
  showErrorToast: true,
  errorTitle: "获取Widget列表失败"
});

export const getWidgetInfoWithToast = withToast(getWidgetInfo, {
  showErrorToast: true,
  errorTitle: "获取Widget信息失败"
});
