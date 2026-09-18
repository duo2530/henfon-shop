import { BackendCatalogCategory, BackendCatalogProduct } from '../api/adminApi';
import { Product, ProductCategory } from '../types';
import { formatDate } from '../utils/datetime';

const splitTags = (value?: string): string[] => value ? value.split(',').map((tag) => tag.trim()).filter(Boolean) : [];

// 后端类目编码即前端选项值，避免自建映射表漏掉新类目后静默落到默认分类。
const normalizeCategoryCode = (value?: string): ProductCategory => (value || '').trim().toUpperCase();

export function backendCategoriesToMap(categories: BackendCatalogCategory[]): Map<number, { code: ProductCategory; name: string }> {
  return new Map(categories.map((category) => [category.id, {
    code: normalizeCategoryCode(category.categoryCode),
    name: category.categoryName
  }]));
}

export function backendCategoriesToOptions(categories: BackendCatalogCategory[]): Array<{ id: number; code: ProductCategory; name: string }> {
  return categories.map((category) => ({
    id: category.id,
    code: normalizeCategoryCode(category.categoryCode),
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
      createdAt: formatDate(product.createdAt, ''),
      description: product.description || product.shortDescription || '',
      tags: splitTags(product.tagsCsv)
    };
  });
}
