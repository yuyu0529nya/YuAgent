// 商品相关类型定义

import { BillingType, ProductStatus } from './billing';

// 商品接口
export interface Product {
  id: string;
  name: string;
  type: string; // BillingType枚举值的字符串形式
  serviceId: string;
  ruleId: string;
  pricingConfig: Record<string, any>;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
  // 模型相关字段（仅MODEL_USAGE类型）
  modelName?: string;
  modelId?: string;
  providerName?: string;
}

// 创建商品请求
export interface CreateProductRequest {
  name: string;
  type: string;
  serviceId: string;
  ruleId: string;
  pricingConfig: Record<string, any>;
  status?: ProductStatus;
}

// 更新商品请求
export interface UpdateProductRequest {
  name?: string;
  type?: string;
  serviceId?: string;
  ruleId?: string;
  pricingConfig?: Record<string, any>;
  status?: ProductStatus;
}

// 查询商品请求
export interface QueryProductRequest {
  keyword?: string;
  type?: string;
  status?: ProductStatus;
  page?: number;
  pageSize?: number;
}

// 价格配置接口（针对不同的计费策略）
export interface ModelTokenPricingConfig {
  input_cost_per_million: number;
  output_cost_per_million: number;
}

export interface PerUnitPricingConfig {
  cost_per_unit: number;
}

export interface TieredPricingConfig {
  tiers: Array<{
    min_quantity: number;
    max_quantity?: number;
    unit_price: number;
  }>;
}

// 商品表单数据接口
export interface ProductFormData {
  name: string;
  type: BillingType;
  serviceId: string;
  ruleId: string;
  pricingConfig: Record<string, any>;
  status: ProductStatus;
}

// 用户端商品展示接口
export interface ProductDisplayInfo {
  id: string;
  name: string;
  type: string;
  description: string;
  icon: string;
  pricingDisplay: string;
  usageExample: string;
  status: ProductStatus;
}

// 价格展示配置
export interface PricingDisplayConfig {
  type: 'token' | 'per_unit' | 'tiered';
  displayText: string;
  examples: PricingExample[];
}

// 价格示例
export interface PricingExample {
  usage: string;
  cost: string;
  description: string;
}

// 商品分类信息
export interface ProductCategory {
  type: string;
  name: string;
  description: string;
  icon: string;
  products: Product[];
}

// 价格格式化工具函数
export const formatPricing = (pricingConfig: Record<string, any>, type: string): PricingDisplayConfig => {
  switch (type) {
    case 'MODEL_USAGE':
      const modelConfig = pricingConfig as ModelTokenPricingConfig;
      return {
        type: 'token',
        displayText: `输入: ¥${(modelConfig.input_cost_per_million || 0).toFixed(4)}/万tokens, 输出: ¥${(modelConfig.output_cost_per_million || 0).toFixed(4)}/万tokens`,
        examples: [
          { usage: '1万tokens输入 + 1万tokens输出', cost: `¥${((modelConfig.input_cost_per_million || 0) + (modelConfig.output_cost_per_million || 0)).toFixed(4)}`, description: '典型对话费用' },
          { usage: '10万tokens输入 + 10万tokens输出', cost: `¥${(((modelConfig.input_cost_per_million || 0) + (modelConfig.output_cost_per_million || 0)) * 10).toFixed(2)}`, description: '长文本处理费用' }
        ]
      };

    case 'AGENT_CREATION':
      const agentConfig = pricingConfig as PerUnitPricingConfig;
      return {
        type: 'per_unit',
        displayText: `¥${(agentConfig.cost_per_unit || 0).toFixed(2)}/次`,
        examples: [
          { usage: '创建1个Agent', cost: `¥${(agentConfig.cost_per_unit || 0).toFixed(2)}`, description: '基础创建费用' },
          { usage: '创建10个Agent', cost: `¥${((agentConfig.cost_per_unit || 0) * 10).toFixed(2)}`, description: '批量创建费用' }
        ]
      };

    case 'AGENT_USAGE':
      const usageConfig = pricingConfig as PerUnitPricingConfig;
      return {
        type: 'per_unit',
        displayText: `¥${(usageConfig.cost_per_unit || 0).toFixed(4)}/次`,
        examples: [
          { usage: '100次调用', cost: `¥${((usageConfig.cost_per_unit || 0) * 100).toFixed(2)}`, description: '日常使用费用' },
          { usage: '1000次调用', cost: `¥${((usageConfig.cost_per_unit || 0) * 1000).toFixed(2)}`, description: '高频使用费用' }
        ]
      };

    case 'API_CALL':
      const apiConfig = pricingConfig as PerUnitPricingConfig;
      return {
        type: 'per_unit',
        displayText: `¥${(apiConfig.cost_per_unit || 0).toFixed(4)}/次`,
        examples: [
          { usage: '1000次API调用', cost: `¥${((apiConfig.cost_per_unit || 0) * 1000).toFixed(2)}`, description: '标准API使用' },
          { usage: '10000次API调用', cost: `¥${((apiConfig.cost_per_unit || 0) * 10000).toFixed(2)}`, description: '企业级API使用' }
        ]
      };

    case 'STORAGE_USAGE':
      const storageConfig = pricingConfig as TieredPricingConfig;
      const firstTier = storageConfig.tiers?.[0];
      return {
        type: 'tiered',
        displayText: firstTier ? `起步价: ¥${firstTier.unit_price.toFixed(4)}/GB` : '阶梯定价',
        examples: [
          { usage: '1GB存储', cost: firstTier ? `¥${firstTier.unit_price.toFixed(2)}` : 'N/A', description: '基础存储费用' },
          { usage: '100GB存储', cost: firstTier ? `¥${(firstTier.unit_price * 100).toFixed(2)}` : 'N/A', description: '大容量存储费用' }
        ]
      };

    default:
      return {
        type: 'per_unit',
        displayText: '按使用量计费',
        examples: []
      };
  }
};

// 获取商品图标
export const getProductIcon = (type: string): string => {
  switch (type) {
    case 'MODEL_USAGE':
      return '🤖';
    case 'AGENT_CREATION':
      return '🎯';
    case 'AGENT_USAGE':
      return '⚡';
    case 'API_CALL':
      return '🔌';
    case 'STORAGE_USAGE':
      return '💾';
    default:
      return '📦';
  }
};

// 获取商品描述
export const getProductDescription = (type: string): string => {
  switch (type) {
    case 'MODEL_USAGE':
      return '使用各种AI模型进行对话、文本生成等功能的计费';
    case 'AGENT_CREATION':
      return '创建智能助理Agent时的一次性收费';
    case 'AGENT_USAGE':
      return '使用已创建的Agent进行对话和任务处理的计费';
    case 'API_CALL':
      return '通过API接口调用平台服务的计费';
    case 'STORAGE_USAGE':
      return '存储文件、数据等资源的计费';
    default:
      return '平台服务计费';
  }
};

// 转换商品为展示信息
export const toProductDisplayInfo = (product: Product): ProductDisplayInfo => {
  const pricingDisplay = formatPricing(product.pricingConfig, product.type);
  
  return {
    id: product.id,
    name: product.name,
    type: product.type,
    description: getProductDescription(product.type),
    icon: getProductIcon(product.type),
    pricingDisplay: pricingDisplay.displayText,
    usageExample: pricingDisplay.examples[0]?.description || '',
    status: product.status
  };
};