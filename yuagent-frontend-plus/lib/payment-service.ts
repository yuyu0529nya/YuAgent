// 支付API服务

import { httpClient, ApiResponse } from '@/lib/http-client';
import { RechargeRequest } from '@/types/account';
import { 
  PaymentResponse, 
  OrderStatusResponse, 
  PaymentMethodDTO 
} from '@/types/payment';

// API端点
const API_ENDPOINTS = {
  CREATE_RECHARGE_PAYMENT: '/payments/recharge',
  QUERY_ORDER_STATUS: '/payments/orders',
  GET_PAYMENT_METHODS: '/payments/methods'
} as const;

export class PaymentService {
  
  /** 创建充值支付 */
  static async createRechargePayment(data: RechargeRequest): Promise<ApiResponse<PaymentResponse>> {
    try {
      return await httpClient.post(API_ENDPOINTS.CREATE_RECHARGE_PAYMENT, data);
    } catch (error) {
      return {
        code: 500,
        message: '创建支付失败',
        data: {} as PaymentResponse,
        timestamp: Date.now()
      };
    }
  }

  /** 查询订单状态 */
  static async queryOrderStatus(orderNo: string): Promise<ApiResponse<OrderStatusResponse>> {
    try {
      return await httpClient.get(`${API_ENDPOINTS.QUERY_ORDER_STATUS}/${orderNo}/status`);
    } catch (error) {
      return {
        code: 500,
        message: '查询订单状态失败',
        data: {} as OrderStatusResponse,
        timestamp: Date.now()
      };
    }
  }

  /** 获取可用的支付方法列表 */
  static async getAvailablePaymentMethods(): Promise<ApiResponse<PaymentMethodDTO[]>> {
    try {
      return await httpClient.get(API_ENDPOINTS.GET_PAYMENT_METHODS);
    } catch (error) {
      return {
        code: 500,
        message: '获取支付方法失败',
        data: [],
        timestamp: Date.now()
      };
    }
  }

  /** 轮询订单状态 */
  static async pollOrderStatus(
    orderNo: string,
    callbacks: {
      onStatusChange?: (status: OrderStatusResponse) => void;
      onSuccess?: (orderNo: string) => void;
      onFailed?: (reason: string) => void;
      onExpired?: () => void;
      onError?: (error: string) => void;
    },
    config: {
      maxDuration?: number; // 最大轮询时间（毫秒）
      interval?: number; // 轮询间隔（毫秒）
    } = {}
  ): Promise<() => void> {
    const defaultConfig = {
      maxDuration: 300000,
      interval: 3000,
    };
    const finalConfig = { ...defaultConfig, ...config };
    let timeoutHandle: ReturnType<typeof setTimeout> | null = null;
    let isPolling = true;
    const startedAt = Date.now();

    const stopPolling = () => {
      isPolling = false;
      if (timeoutHandle) {
        clearTimeout(timeoutHandle);
        timeoutHandle = null;
      }
    };

    const isTerminalStatus = (status: OrderStatusResponse["status"]) =>
      status === "PAID" || status === "CANCELLED" || status === "EXPIRED";

    const handleTerminalStatus = (status: OrderStatusResponse["status"]) => {
      switch (status) {
        case "PAID":
          callbacks.onSuccess?.(orderNo);
          break;
        case "CANCELLED":
          callbacks.onFailed?.("订单已取消");
          break;
        case "EXPIRED":
          callbacks.onExpired?.();
          break;
      }
      stopPolling();
    };

    const poll = async (): Promise<void> => {
      if (!isPolling) {
        return;
      }

      try {
        const response = await PaymentService.queryOrderStatus(orderNo);
        if (!isPolling) {
          return;
        }

        if (response.code === 200) {
          const orderStatus = response.data;
          callbacks.onStatusChange?.(orderStatus);

          if (isTerminalStatus(orderStatus.status)) {
            handleTerminalStatus(orderStatus.status);
            return;
          }
        } else {
          callbacks.onError?.(response.message || "查询订单状态失败");
        }
      } catch {
        if (isPolling) {
          callbacks.onError?.("网络错误，请检查网络连接");
        }
      }

      if (!isPolling) {
        return;
      }
      if (Date.now() - startedAt >= finalConfig.maxDuration) {
        callbacks.onExpired?.();
        stopPolling();
        return;
      }

      timeoutHandle = setTimeout(() => {
        void poll();
      }, finalConfig.interval);
    };

    await poll();
    return stopPolling;
  }
}

// 带Toast提示的API服务方法
export const PaymentServiceWithToast = {
  async createRechargePayment(data: RechargeRequest) {
    return httpClient.post(API_ENDPOINTS.CREATE_RECHARGE_PAYMENT, data, {}, { showToast: true });
  }
};

export default PaymentService;
