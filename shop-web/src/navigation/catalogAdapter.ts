import { BackendCatalogCategory, BackendCatalogProduct } from '../api/adminApi';
import { Product, ProductCategory } from '../types';

const categoryCodeMap: Record<string, ProductCategory> = {
  ELECTRONICS: 'electronics',
  CLOTHING: 'clothing',
  HOME: 'home',
  BEAUTY: 'beauty',
  FOOD: 'food'
};

const splitTags = (value?: string): string[] => value ? value.split(',').map((tag) => tag.trim()).filter(Boolean) : [];

export function backendCategoriesToMap(categories: BackendCatalogCategory[]): Map<number, { code: ProductCategory; name: string }> {
  return new Map(categories.map((category) => [category.id, {
    code: categoryCodeMap[category.categoryCode.toUpperCase()] || 'electronics',
    name: category.categoryName
  }]));
}

export function backendCategoriesToOptions(categories: BackendCatalogCategory[]): Array<{ id: number; code: ProductCategory; name: string }> {
  return categories.map((category) => ({
    id: category.id,
    code: categoryCodeMap[category.categoryCode.toUpperCase()] || 'electronics',
    name: category.categoryName
  }));
}

export function backendProductsToFrontend(source: BackendCatalogProduct[], categoryMap: Map<number, { code: ProductCategory; name: string }>): Product[] {
  return source.map((product) => {
    const category = product.categoryId ? categoryMap.get(product.categoryId) : undefined;
    return {
      id: String(product.id),
      name: product.productName,
      productCode: product.productCode,
      categoryId: product.categoryId,
      category: category?.code || 'electronics',
      categoryName: product.categoryName || category?.name || '未分类',
      price: Number(product.price || 0),
      originalPrice: Number(product.marketPrice || product.price || 0),
      costPrice: Number(product.costPrice || 0),
      stock: Number(product.currentStock || 0),
      safetyStock: Number(product.safetyStock || 0),
      status: product.status === 1 ? 'active' : 'inactive',
      imageUrl: product.mainImageUrl || '',
      sku: product.defaultSkuCode || product.productCode,
      salesCount: Number(product.salesCount || 0),
      createdAt: product.createdAt?.slice(0, 10) || '',
      description: product.description || product.shortDescription || '',
      tags: splitTags(product.tagsCsv)
    };
  });
}
