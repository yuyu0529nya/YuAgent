"use client";

import { useState, useEffect } from "react";
import { RefreshCw, Calculator, TrendingUp, Info, HelpCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { 
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { toast } from "@/hooks/use-toast";

import { Product, formatPricing, getProductIcon, getProductDescription, toProductDisplayInfo, ProductDisplayInfo } from "@/types/product";
import { BillingTypeNames } from "@/types/billing";
import { ProductServiceWithToast } from "@/lib/product-service";
import { ProductCard, PricingCalculator } from "./components";

export default function PricingPage() {
  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState<Product[]>([]);
  const [productsByType, setProductsByType] = useState<Record<string, Product[]>>({});
  const [selectedType, setSelectedType] = useState<string>('all');
  const [activeTab, setActiveTab] = useState<string>('products');
  const [helpDialogOpen, setHelpDialogOpen] = useState(false);

  // 加载商品数据
  const loadProducts = async () => {
    setLoading(true);
    try {
      const response = await ProductServiceWithToast.getActiveProducts();
      
      if (response.code === 200 && response.data) {
        setProducts(response.data);
        
        // 按类型分组
        const grouped = response.data.reduce((acc, product) => {
          if (!acc[product.type]) {
            acc[product.type] = [];
          }
          acc[product.type].push(product);
          return acc;
        }, {} as Record<string, Product[]>);
        
        setProductsByType(grouped);
      } else {
        toast({
          title: "获取价格信息失败",
          description: response.message || "请稍后重试",
          variant: "destructive"
        });
      }
    } catch (error) {
      toast({
        title: "加载失败",
        description: "网络错误，请稍后重试",
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  };

  // 获取要显示的商品列表
  const getDisplayProducts = (): Product[] => {
    if (selectedType === 'all') {
      return products;
    }
    return productsByType[selectedType] || [];
  };

  // 获取商品类型标签
  const getTypeOptions = () => {
    const types = Object.keys(productsByType);
    return [
      { value: 'all', label: '全部', count: products.length },
      ...types.map(type => ({
        value: type,
        label: BillingTypeNames[type as keyof typeof BillingTypeNames] || type,
        count: productsByType[type].length
      }))
    ];
  };

  useEffect(() => {
    loadProducts();
  }, []);

  return (
    <div className="container py-6">
      <div className="mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">价格说明</h1>
            <p className="text-muted-foreground">了解各项服务的详细价格和计费方式</p>
          </div>
          <Button onClick={loadProducts} disabled={loading} variant="outline" size="sm">
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </Button>
        </div>
      </div>

      {/* 价格信息提醒 */}
      <Alert className="mb-6">
        <Info className="h-4 w-4" />
        <AlertDescription className="text-sm">
          以下价格为当前有效的计费标准，实际费用将根据您的使用量按照相应规则进行计算。价格可能会根据服务升级而调整，届时会提前通知用户。
        </AlertDescription>
      </Alert>

      <div className="flex items-center justify-between mb-6">
        <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1">
          <TabsList className="grid w-full max-w-md grid-cols-2">
            <TabsTrigger value="products">商品目录</TabsTrigger>
            <TabsTrigger value="calculator">价格计算器</TabsTrigger>
          </TabsList>
        </Tabs>
        
        <Button
          variant="outline"
          onClick={() => setHelpDialogOpen(true)}
          className="ml-4"
        >
          <HelpCircle className="h-4 w-4 mr-2" />
          计费说明
        </Button>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-6">

        <TabsContent value="products">
          <div className="space-y-6">
            {/* 商品类型筛选 */}
            <Tabs value={selectedType} onValueChange={setSelectedType}>
              <TabsList className="grid w-full grid-cols-2 lg:grid-cols-6">
                {getTypeOptions().map((option) => (
                  <TabsTrigger key={option.value} value={option.value} className="text-sm">
                    {option.label}
                    <Badge variant="secondary" className="ml-2 text-xs">
                      {option.count}
                    </Badge>
                  </TabsTrigger>
                ))}
              </TabsList>

              <TabsContent value={selectedType}>
                {loading ? (
                  <div className="flex items-center justify-center py-12">
                    <div className="text-sm text-muted-foreground">加载中...</div>
                  </div>
                ) : getDisplayProducts().length === 0 ? (
                  <div className="text-center py-12">
                    <div className="text-muted-foreground">暂无{selectedType === 'all' ? '' : '该类型'}商品</div>
                  </div>
                ) : (
                  <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                    {getDisplayProducts().map((product) => (
                      <ProductCard
                        key={product.id}
                        product={product}
                        modelName={product.type === 'MODEL_USAGE' ? product.modelName : undefined}
                      />
                    ))}
                  </div>
                )}
              </TabsContent>
            </Tabs>
          </div>
        </TabsContent>

        <TabsContent value="calculator">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="text-sm text-muted-foreground">加载中...</div>
            </div>
          ) : products.length === 0 ? (
            <div className="text-center py-12">
              <div className="text-muted-foreground">暂无商品数据</div>
            </div>
          ) : (
            <PricingCalculator products={products} />
          )}
        </TabsContent>

      </Tabs>

      {/* 计费说明弹窗 */}
      <Dialog open={helpDialogOpen} onOpenChange={setHelpDialogOpen}>
        <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <HelpCircle className="h-5 w-5" />
              计费说明
            </DialogTitle>
            <DialogDescription>
              了解各项服务的计费规则和扣费方式
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-6">
            <Accordion type="single" collapsible className="w-full">
              <AccordionItem value="billing-rules">
                <AccordionTrigger className="text-left">
                  <div className="flex items-center gap-2">
                    <Info className="h-4 w-4" />
                    计费规则与扣费方式
                  </div>
                </AccordionTrigger>
                <AccordionContent className="space-y-4 text-sm">
                  <div className="space-y-3">
                    <div>
                      <h4 className="font-medium text-base mb-2">📊 模型调用计费 (MODEL_USAGE)</h4>
                      <ul className="list-disc list-inside space-y-1 text-muted-foreground ml-4">
                        <li>按输入和输出token数量分别计费</li>
                        <li>输入token和输出token价格可能不同</li>
                        <li>计费精确到每个token，实际使用时按万token为单位显示</li>
                        <li>使用完成后立即扣费</li>
                      </ul>
                    </div>
                    
                    <div>
                      <h4 className="font-medium text-base mb-2">🎯 Agent创建计费 (AGENT_CREATION)</h4>
                      <ul className="list-disc list-inside space-y-1 text-muted-foreground ml-4">
                        <li>创建Agent时一次性收费</li>
                        <li>价格固定，不受Agent复杂度影响</li>
                        <li>创建前会先检查余额是否充足</li>
                        <li>创建成功后立即扣费</li>
                      </ul>
                    </div>

                    <div>
                      <h4 className="font-medium text-base mb-2">⚡ Agent使用计费 (AGENT_USAGE)</h4>
                      <ul className="list-disc list-inside space-y-1 text-muted-foreground ml-4">
                        <li>每次调用Agent按固定价格计费</li>
                        <li>无论对话长短，每次调用收费相同</li>
                        <li>调用完成后立即扣费</li>
                      </ul>
                    </div>

                    <div>
                      <h4 className="font-medium text-base mb-2">🔌 API调用计费 (API_CALL)</h4>
                      <ul className="list-disc list-inside space-y-1 text-muted-foreground ml-4">
                        <li>按API调用次数计费</li>
                        <li>不同类型的API可能有不同价格</li>
                        <li>调用成功后扣费，失败不扣费</li>
                      </ul>
                    </div>

                    <div>
                      <h4 className="font-medium text-base mb-2">💾 存储使用计费 (STORAGE_USAGE)</h4>
                      <ul className="list-disc list-inside space-y-1 text-muted-foreground ml-4">
                        <li>按存储容量和时间计费</li>
                        <li>支持阶梯定价，用量越大单价越低</li>
                        <li>按实际存储时间计费</li>
                      </ul>
                    </div>
                  </div>

                  <Separator />

                  <div>
                    <h4 className="font-medium text-base mb-2">💳 扣费与余额</h4>
                    <ul className="list-disc list-inside space-y-1 text-muted-foreground ml-4">
                      <li>所有费用从账户余额中自动扣除</li>
                      <li>余额不足时会阻止相应服务的使用</li>
                      <li>支持信用额度，可在余额为负的情况下继续使用</li>
                      <li>所有消费记录都会详细记录在用量明细中</li>
                    </ul>
                  </div>
                </AccordionContent>
              </AccordionItem>

              <AccordionItem value="pricing-updates">
                <AccordionTrigger>价格调整与通知</AccordionTrigger>
                <AccordionContent className="space-y-3 text-sm">
                  <ul className="list-disc list-inside space-y-1 text-muted-foreground">
                    <li>价格调整会提前至少7天通过邮件和站内消息通知</li>
                    <li>现有账户余额不受价格调整影响</li>
                    <li>新价格只对调整生效后的使用生效</li>
                    <li>用户可以在设置中选择接收价格变动通知</li>
                  </ul>
                </AccordionContent>
              </AccordionItem>

              <AccordionItem value="refund-policy">
                <AccordionTrigger>退费政策</AccordionTrigger>
                <AccordionContent className="space-y-3 text-sm">
                  <ul className="list-disc list-inside space-y-1 text-muted-foreground">
                    <li>充值的余额支持退款，需要联系客服处理</li>
                    <li>已使用的服务费用不支持退款</li>
                    <li>退款时会扣除相应的手续费</li>
                    <li>特殊情况下的退款需要根据具体情况处理</li>
                  </ul>
                </AccordionContent>
              </AccordionItem>
            </Accordion>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}