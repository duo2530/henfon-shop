import React, { useState, useMemo } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Product, ProductCategory, ProductStatus } from '../../types';
import { 
  Plus, 
  Search, 
  Edit3, 
  Trash2, 
  X, 
  RotateCcw, 
  Download,
  AlertTriangle,
  PackagePlus,
  ArrowUpDown,
  CheckSquare,
  Square,
  SlidersHorizontal,
  TrendingUp,
  AlertCircle,
  Eye,
  Layers,
  ArrowUpRight,
  ShieldAlert,
  Percent
} from 'lucide-react';

const fallbackCategoryOptions: Array<{ id: number; code: ProductCategory; name: string }> = [
  { id: 1, code: 'electronics', name: '数码数控 (Electronics)' },
  { id: 2, code: 'clothing', name: '服饰鞋包 (Clothing)' },
  { id: 3, code: 'home', name: '家居生活 (Home)' },
  { id: 4, code: 'beauty', name: '美妆护肤 (Beauty)' },
  { id: 5, code: 'food', name: '食品生鲜 (Food)' }
];

export const ProductManagementView: React.FC = () => {
  const { 
    products, 
    catalogCategories,
    addProduct, 
    updateProduct, 
    deleteProduct, 
    toggleProductStatus,
    batchUpdateProductStatus,
    batchDeleteProducts,
    batchUpdateProductCategory,
    adjustProductStock,
    showToast 
  } = useAdmin();

  // 类目名称和ID由后端提供；接口暂不可用时保留本地选项，避免页面无法录入商品。
  const categoryOptions = catalogCategories.length > 0 ? catalogCategories : fallbackCategoryOptions;
  const categoryName = (code: ProductCategory) =>
    categoryOptions.find((option) => option.code === code)?.name || code;
  const categoryId = (code: ProductCategory) =>
    categoryOptions.find((option) => option.code === code)?.id;

  // Search & Filter states
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [selectedStatus, setSelectedStatus] = useState<string>('all');
  const [stockFilter, setStockFilter] = useState<'all' | 'out_of_stock' | 'low_stock' | 'normal'>('all');
  const [tagFilter, setTagFilter] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'createdAt' | 'price' | 'stock' | 'salesCount'>('createdAt');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  // Multi-Selection State
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // Pagination states
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 6;

  // Modal / Drawer states
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const [detailProduct, setDetailProduct] = useState<Product | null>(null);
  
  // Batch category modal state
  const [batchCategoryOpen, setBatchCategoryOpen] = useState(false);
  const [targetBatchCategory, setTargetBatchCategory] = useState<ProductCategory>('electronics');

  // Quick Stock Adjustment modal
  const [stockAdjustProduct, setStockAdjustProduct] = useState<Product | null>(null);
  const [adjustedStockValue, setAdjustedStockValue] = useState<number>(0);
  const [stockAdjustReason, setStockAdjustReason] = useState<string>('日常盘点入库');

  // Form states for Add/Edit
  const [formData, setFormData] = useState({
    name: '',
    category: 'electronics' as ProductCategory,
    price: 0,
    costPrice: 0,
    safetyStock: 10,
    originalPrice: 0,
    stock: 10,
    status: 'active' as ProductStatus,
    imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop&q=80',
    sku: '',
    tags: [] as string[],
    newTagInput: '',
    description: ''
  });

  // All unique tags across all products for filter
  const allAvailableTags = useMemo(() => {
    const set = new Set<string>();
    products.forEach((p) => {
      p.tags?.forEach((t) => set.add(t));
    });
    return Array.from(set);
  }, [products]);

  // Filtered & Sorted Products
  const filteredProducts = useMemo(() => {
    return products
      .filter((item) => {
        const matchesSearch =
          searchTerm === '' ||
          item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
          item.sku.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesCategory =
          selectedCategory === 'all' || item.category === selectedCategory;
        const matchesStatus =
          selectedStatus === 'all' || item.status === selectedStatus;
        
        let matchesStock = true;
        const safety = item.safetyStock ?? 10;
        if (stockFilter === 'out_of_stock') matchesStock = item.stock === 0;
        else if (stockFilter === 'low_stock') matchesStock = item.stock > 0 && item.stock <= safety;
        else if (stockFilter === 'normal') matchesStock = item.stock > safety;

        const matchesTag =
          tagFilter === 'all' || (item.tags && item.tags.includes(tagFilter));

        return matchesSearch && matchesCategory && matchesStatus && matchesStock && matchesTag;
      })
      .sort((a, b) => {
        const valA = (a as any)[sortBy] ?? 0;
        const valB = (b as any)[sortBy] ?? 0;
        if (sortOrder === 'asc') {
          return valA > valB ? 1 : -1;
        } else {
          return valA < valB ? 1 : -1;
        }
      });
  }, [products, searchTerm, selectedCategory, selectedStatus, stockFilter, tagFilter, sortBy, sortOrder]);

  // Paginated records
  const totalEntries = filteredProducts.length;
  const totalPages = Math.ceil(totalEntries / pageSize) || 1;
  const paginatedProducts = filteredProducts.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );

  // Selection helpers
  const isAllCurrentPageSelected = 
    paginatedProducts.length > 0 && 
    paginatedProducts.every((p) => selectedIds.includes(p.id));

  const handleToggleSelectAll = () => {
    if (isAllCurrentPageSelected) {
      const pageIds = paginatedProducts.map((p) => p.id);
      setSelectedIds((prev) => prev.filter((id) => !pageIds.includes(id)));
    } else {
      const pageIds = paginatedProducts.map((p) => p.id);
      setSelectedIds((prev) => Array.from(new Set([...prev, ...pageIds])));
    }
  };

  const handleToggleSelectRow = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleClearFilters = () => {
    setSearchTerm('');
    setSelectedCategory('all');
    setSelectedStatus('all');
    setStockFilter('all');
    setTagFilter('all');
    setCurrentPage(1);
    showToast('已重置所有筛选条件', 'info');
  };

  const handleOpenAddModal = () => {
    setEditingProduct(null);
    setFormData({
      name: '',
      category: 'electronics',
      price: 299.00,
      costPrice: 160.00,
      safetyStock: 15,
      originalPrice: 399.00,
      stock: 50,
      status: 'active',
      imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&auto=format&fit=crop&q=80',
      sku: `SKU-${Date.now().toString().slice(-6)}`,
      tags: ['新品上市', '核心爆款'],
      newTagInput: '',
      description: ''
    });
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (product: Product) => {
    setEditingProduct(product);
    setFormData({
      name: product.name,
      category: product.category,
      price: product.price,
      costPrice: product.costPrice || Math.round(product.price * 0.55),
      safetyStock: product.safetyStock || 10,
      originalPrice: product.originalPrice || product.price * 1.2,
      stock: product.stock,
      status: product.status,
      imageUrl: product.imageUrl,
      sku: product.sku,
      tags: product.tags ? [...product.tags] : [],
      newTagInput: '',
      description: product.description || ''
    });
    setIsModalOpen(true);
  };

  const handleAddTag = () => {
    const tag = formData.newTagInput.trim();
    if (tag && !formData.tags.includes(tag)) {
      setFormData({
        ...formData,
        tags: [...formData.tags, tag],
        newTagInput: ''
      });
    }
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setFormData({
      ...formData,
      tags: formData.tags.filter((t) => t !== tagToRemove)
    });
  };

  const handleSubmitForm = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      showToast('请输入商品名称', 'error');
      return;
    }

    if (editingProduct) {
      updateProduct(editingProduct.id, {
        name: formData.name,
        categoryId: categoryId(formData.category),
        category: formData.category,
        price: formData.price,
        costPrice: formData.costPrice,
        safetyStock: formData.safetyStock,
        originalPrice: formData.originalPrice,
        stock: formData.stock,
        status: formData.status,
        imageUrl: formData.imageUrl,
        sku: formData.sku,
        tags: formData.tags,
        description: formData.description,
        categoryName: categoryName(formData.category)
      });
    } else {
      addProduct({
        name: formData.name,
        categoryId: categoryId(formData.category),
        category: formData.category,
        price: formData.price,
        costPrice: formData.costPrice,
        safetyStock: formData.safetyStock,
        originalPrice: formData.originalPrice,
        stock: formData.stock,
        status: formData.status,
        imageUrl: formData.imageUrl,
        sku: formData.sku,
        tags: formData.tags,
        description: formData.description,
        categoryName: categoryName(formData.category)
      });
    }
    setIsModalOpen(false);
  };

  const handleExportData = () => {
    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      '商品编号,商品名称,分类,售价,成本价,毛利率,库存,安全库存,销量,标签,状态\n' +
      filteredProducts
        .map((p) => {
          const cost = p.costPrice || p.price * 0.6;
          const margin = (((p.price - cost) / p.price) * 100).toFixed(1) + '%';
          const tagStr = (p.tags || []).join(';');
          return `"${p.sku}","${p.name}","${p.categoryName}",${p.price},${cost},"${margin}",${p.stock},${p.safetyStock || 10},${p.salesCount || 0},"${tagStr}","${p.status === 'active' ? '上架中' : '已下架'}"`;
        })
        .join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `商品库数据导出_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('商品库数据已成功导出为 CSV', 'success');
  };

  const handleConfirmBatchCategory = () => {
    batchUpdateProductCategory(selectedIds, targetBatchCategory, categoryName(targetBatchCategory));
    setBatchCategoryOpen(false);
    setSelectedIds([]);
  };

  const handleConfirmStockAdjust = () => {
    if (!stockAdjustProduct) return;
    adjustProductStock(stockAdjustProduct.id, adjustedStockValue, stockAdjustReason);
    setStockAdjustProduct(null);
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              商品与库存管理 (Product & Inventory)
            </h2>
            <span className="text-xs bg-blue-50 text-blue-700 font-semibold px-2 py-0.5 rounded-full border border-blue-200">
              {products.length} 款商品在线
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            精细化维护SKU类目、毛利核算、安全库存阈值报警与批量上架运维。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            id="btn-export-products"
            onClick={handleExportData}
            className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
          >
            <Download className="w-4 h-4 text-gray-500" />
            <span>导出报表</span>
          </button>

          <button
            id="btn-add-product"
            onClick={handleOpenAddModal}
            className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>新建商品 (New SKU)</span>
          </button>
        </div>
      </div>

      {/* Filter Bar (Enhanced multi-dimensional filtering) */}
      <div className="bg-white border border-[#E2E8F0] rounded-xl p-5 shadow-xs">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 items-end">
          {/* Search */}
          <div className="lg:col-span-2">
            <label className="block text-xs font-medium text-gray-600 mb-1.5">
              商品名称 / SKU编码 (Keywords)
            </label>
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setCurrentPage(1);
                }}
                placeholder="搜索商品名称、型号、SKU编号..."
                className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] focus:bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
              />
            </div>
          </div>

          {/* Category Dropdown */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1.5">
              商品类目 (Category)
            </label>
            <select
              value={selectedCategory}
              onChange={(e) => {
                setSelectedCategory(e.target.value);
                setCurrentPage(1);
              }}
              className="w-full h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] focus:bg-white focus:border-blue-500 outline-none transition-all text-gray-700"
            >
              <option value="all">全部商品分类</option>
              {categoryOptions.map((option) => (
                <option key={option.id} value={option.code}>{option.name}</option>
              ))}
            </select>
          </div>

          {/* Stock Condition */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1.5">
              库存状态 (Inventory)
            </label>
            <select
              value={stockFilter}
              onChange={(e) => {
                setStockFilter(e.target.value as any);
                setCurrentPage(1);
              }}
              className="w-full h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] focus:bg-white focus:border-blue-500 outline-none transition-all text-gray-700"
            >
              <option value="all">全部库存状态</option>
              <option value="normal">库存充裕 (安全线以上)</option>
              <option value="low_stock">⚠️ 库存预警 (低于安全线)</option>
              <option value="out_of_stock">🚫 缺货售罄 (0件)</option>
            </select>
          </div>

          {/* Status Dropdown */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1.5">
              上架状态 (Status)
            </label>
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value);
                setCurrentPage(1);
              }}
              className="w-full h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] focus:bg-white focus:border-blue-500 outline-none transition-all text-gray-700"
            >
              <option value="all">全部状态</option>
              <option value="active">上架在售 (Active)</option>
              <option value="inactive">已下架封存 (Inactive)</option>
            </select>
          </div>
        </div>

        {/* Second row: Tag chips & Reset */}
        <div className="flex flex-wrap items-center justify-between gap-3 mt-4 pt-3 border-t border-gray-100 text-xs">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-gray-500 font-medium">标签筛选:</span>
            <button
              onClick={() => setTagFilter('all')}
              className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                tagFilter === 'all'
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              全部
            </button>
            {allAvailableTags.map((t) => (
              <button
                key={t}
                onClick={() => setTagFilter(t)}
                className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                  tagFilter === t
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                #{t}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-4 text-gray-500">
            <div>
              匹配结果: <span className="font-semibold text-gray-900">{filteredProducts.length}</span> 项
            </div>
            <button
              onClick={handleClearFilters}
              className="flex items-center gap-1 text-gray-500 hover:text-gray-900 font-medium px-2 py-1 rounded hover:bg-gray-100 transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>重置条件</span>
            </button>
          </div>
        </div>
      </div>

      {/* Multi-Selection Batch Actions Floating Bar */}
      {selectedIds.length > 0 && (
        <div className="bg-blue-900 text-white rounded-xl px-5 py-3.5 flex flex-wrap items-center justify-between gap-3 shadow-md animate-in slide-in-from-top-2 duration-200">
          <div className="flex items-center gap-3">
            <div className="w-7 h-7 rounded-lg bg-blue-700 flex items-center justify-center font-bold text-xs">
              {selectedIds.length}
            </div>
            <span className="text-sm font-medium">
              已选中 <strong className="text-white font-bold">{selectedIds.length}</strong> 件商品，支持批量指令：
            </span>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <button
              onClick={() => {
                batchUpdateProductStatus(selectedIds, 'active');
                setSelectedIds([]);
              }}
              className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-xs transition-colors"
            >
              批量上架
            </button>
            <button
              onClick={() => {
                batchUpdateProductStatus(selectedIds, 'inactive');
                setSelectedIds([]);
              }}
              className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-lg text-xs font-semibold shadow-xs transition-colors"
            >
              批量下架
            </button>
            <button
              onClick={() => setBatchCategoryOpen(true)}
              className="px-3 py-1.5 bg-blue-700 hover:bg-blue-600 text-white rounded-lg text-xs font-semibold shadow-xs transition-colors flex items-center gap-1.5"
            >
              <Layers className="w-3.5 h-3.5" />
              <span>变更类目</span>
            </button>
            <button
              onClick={() => {
                if (window.confirm(`确认删除选中的 ${selectedIds.length} 件商品吗？此操作无法撤销。`)) {
                  batchDeleteProducts(selectedIds);
                  setSelectedIds([]);
                }
              }}
              className="px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold shadow-xs transition-colors flex items-center gap-1.5"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>批量删除</span>
            </button>
            <button
              onClick={() => setSelectedIds([])}
              className="px-2.5 py-1.5 text-blue-200 hover:text-white text-xs transition-colors"
            >
              取消选择
            </button>
          </div>
        </div>
      )}

      {/* Main Data Table */}
      <div className="bg-white border border-[#E2E8F0] rounded-xl shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase tracking-wider">
              <tr>
                <th className="py-3 px-4 w-12 text-center">
                  <button
                    onClick={handleToggleSelectAll}
                    className="text-gray-400 hover:text-blue-600 transition-colors flex items-center justify-center mx-auto"
                  >
                    {isAllCurrentPageSelected ? (
                      <CheckSquare className="w-4 h-4 text-blue-600" />
                    ) : (
                      <Square className="w-4 h-4 text-gray-400" />
                    )}
                  </button>
                </th>
                <th className="py-3 px-3 w-16 text-center">主图</th>
                <th className="py-3 px-4">商品基础信息 / SKU / 标签</th>
                <th
                  onClick={() => {
                    setSortBy('price');
                    setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
                  }}
                  className="py-3 px-4 cursor-pointer hover:text-blue-600 transition-colors"
                >
                  <div className="flex items-center gap-1">
                    <span>售价 / 成本毛利</span>
                    <ArrowUpDown className="w-3.5 h-3.5 opacity-60" />
                  </div>
                </th>
                <th
                  onClick={() => {
                    setSortBy('stock');
                    setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
                  }}
                  className="py-3 px-4 cursor-pointer hover:text-blue-600 transition-colors"
                >
                  <div className="flex items-center gap-1">
                    <span>当前库存 / 预警</span>
                    <ArrowUpDown className="w-3.5 h-3.5 opacity-60" />
                  </div>
                </th>
                <th
                  onClick={() => {
                    setSortBy('salesCount');
                    setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
                  }}
                  className="py-3 px-4 cursor-pointer hover:text-blue-600 transition-colors text-center"
                >
                  <div className="flex items-center justify-center gap-1">
                    <span>累计销量</span>
                    <ArrowUpDown className="w-3.5 h-3.5 opacity-60" />
                  </div>
                </th>
                <th className="py-3 px-4 text-center">状态</th>
                <th className="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-100 text-sm text-gray-800">
              {paginatedProducts.length === 0 ? (
                <tr>
                  <td colSpan={8} className="text-center py-16 text-gray-400">
                    <PackagePlus className="w-12 h-12 mx-auto mb-2 opacity-40" />
                    <p className="text-sm font-medium">未检索到匹配的商品记录</p>
                    <p className="text-xs text-gray-400 mt-1">请尝试放宽筛选条件或添加新商品</p>
                  </td>
                </tr>
              ) : (
                paginatedProducts.map((product, idx) => {
                  const isSelected = selectedIds.includes(product.id);
                  const cost = product.costPrice || Math.round(product.price * 0.55);
                  const grossMargin = Math.max(0, ((product.price - cost) / product.price) * 100).toFixed(0);
                  const safety = product.safetyStock ?? 10;
                  const isOutOfStock = product.stock === 0;
                  const isLowStock = product.stock > 0 && product.stock <= safety;

                  return (
                    <tr
                      key={product.id}
                      className={`hover:bg-[#F8FAFC] transition-colors group ${
                        isSelected ? 'bg-blue-50/50' : idx % 2 === 1 ? 'bg-[#FCFDFF]' : 'bg-white'
                      }`}
                    >
                      {/* Checkbox */}
                      <td className="py-3 px-4 text-center">
                        <button
                          onClick={() => handleToggleSelectRow(product.id)}
                          className="text-gray-400 hover:text-blue-600 transition-colors flex items-center justify-center mx-auto"
                        >
                          {isSelected ? (
                            <CheckSquare className="w-4 h-4 text-blue-600" />
                          ) : (
                            <Square className="w-4 h-4 text-gray-300" />
                          )}
                        </button>
                      </td>

                      {/* Product Thumbnail */}
                      <td className="py-3 px-3 text-center">
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          className="w-12 h-12 rounded-lg object-cover border border-gray-200 bg-gray-100 mx-auto shadow-2xs"
                        />
                      </td>

                      {/* Product Info & Tags */}
                      <td className="py-3 px-4 max-w-xs">
                        <div className="font-semibold text-gray-900 line-clamp-1 hover:text-blue-600 cursor-pointer" onClick={() => setDetailProduct(product)}>
                          {product.name}
                        </div>
                        <div className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                          <span className="text-blue-600 font-medium">{product.categoryName}</span>
                          <span className="text-gray-300">•</span>
                          <span className="font-mono text-gray-400 text-[11px]">{product.sku}</span>
                        </div>
                        {product.tags && product.tags.length > 0 && (
                          <div className="flex flex-wrap gap-1 mt-1.5">
                            {product.tags.map((tag) => (
                              <span
                                key={tag}
                                className="inline-block px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-600 border border-gray-200"
                              >
                                {tag}
                              </span>
                            ))}
                          </div>
                        )}
                      </td>

                      {/* Price & Gross Profit */}
                      <td className="py-3 px-4">
                        <div className="font-semibold text-gray-900 text-sm">
                          ¥{product.price.toFixed(2)}
                        </div>
                        <div className="flex items-center gap-1.5 text-xs text-gray-500 mt-0.5">
                          <span>成本 ¥{cost.toFixed(2)}</span>
                          <span className="text-emerald-700 bg-emerald-50 px-1 py-0.2 rounded font-medium text-[11px]">
                            毛利 {grossMargin}%
                          </span>
                        </div>
                      </td>

                      {/* Stock & Warning */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <span
                            className={`font-bold text-sm ${
                              isOutOfStock
                                ? 'text-red-600'
                                : isLowStock
                                ? 'text-amber-600'
                                : 'text-gray-800'
                            }`}
                          >
                            {product.stock} 件
                          </span>
                          
                          {/* Stock adjustment trigger */}
                          <button
                            onClick={() => {
                              setStockAdjustProduct(product);
                              setAdjustedStockValue(product.stock);
                            }}
                            className="text-xs text-blue-600 hover:underline hover:text-blue-800 font-medium"
                            title="快捷调整库存"
                          >
                            调库
                          </button>
                        </div>

                        <div className="text-[11px] text-gray-400 mt-0.5">
                          {isOutOfStock ? (
                            <span className="text-red-600 font-medium flex items-center gap-0.5">
                              <ShieldAlert className="w-3 h-3" /> 已售罄缺货
                            </span>
                          ) : isLowStock ? (
                            <span className="text-amber-600 font-medium flex items-center gap-0.5">
                              <AlertCircle className="w-3 h-3" /> 警戒线 ({safety}件)
                            </span>
                          ) : (
                            <span>安全线: {safety}件</span>
                          )}
                        </div>
                      </td>

                      {/* Sales Count */}
                      <td className="py-3 px-4 text-center">
                        <div className="font-semibold text-gray-800">
                          {product.salesCount ?? 0}
                        </div>
                        <span className="text-[11px] text-gray-400">已付款成交</span>
                      </td>

                      {/* Status */}
                      <td className="py-3 px-4 text-center">
                        {product.status === 'active' ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#E6F4EA] text-[#137333]">
                            <span className="w-1.5 h-1.5 rounded-full bg-[#137333] mr-1.5"></span>
                            上架中
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#FCE8E6] text-[#C5221F]">
                            <span className="w-1.5 h-1.5 rounded-full bg-[#C5221F] mr-1.5"></span>
                            已下架
                          </span>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            onClick={() => setDetailProduct(product)}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                            title="查看详情"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => toggleProductStatus(product.id)}
                            className="px-2 py-1 text-xs font-medium rounded border border-gray-200 hover:bg-gray-100 text-gray-700 transition-colors"
                            title={product.status === 'active' ? '下架商品' : '上架商品'}
                          >
                            {product.status === 'active' ? '下架' : '上架'}
                          </button>
                          <button
                            onClick={() => handleOpenEditModal(product)}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                            title="编辑商品"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setDeleteConfirmId(product.id)}
                            className="p-1.5 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                            title="删除商品"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="bg-[#F8FAFC] border-t border-[#E2E8F0] px-4 py-3 flex items-center justify-between text-xs text-gray-500">
          <div>
            显示第 {(currentPage - 1) * pageSize + 1} 到{' '}
            {Math.min(currentPage * pageSize, totalEntries)} 条，共计 {totalEntries} 条商品
          </div>

          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="px-2.5 py-1 rounded border border-[#E2E8F0] hover:bg-white disabled:opacity-40 disabled:pointer-events-none transition-colors"
            >
              上一页
            </button>

            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <button
                key={page}
                onClick={() => setCurrentPage(page)}
                className={`w-7 h-7 rounded text-xs font-medium transition-all ${
                  currentPage === page
                    ? 'bg-[#2563EB] text-white font-bold'
                    : 'border border-[#E2E8F0] hover:bg-white text-gray-700'
                }`}
              >
                {page}
              </button>
            ))}

            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="px-2.5 py-1 rounded border border-[#E2E8F0] hover:bg-white disabled:opacity-40 disabled:pointer-events-none transition-colors"
            >
              下一页
            </button>
          </div>
        </div>
      </div>

      {/* Product Detail Modal */}
      {detailProduct && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-xl w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <div className="flex items-center gap-2">
                <span className="px-2 py-0.5 text-xs font-semibold rounded bg-blue-100 text-blue-700">
                  {detailProduct.categoryName}
                </span>
                <h3 className="text-base font-bold text-gray-900">
                  商品档案详情
                </h3>
              </div>
              <button
                onClick={() => setDetailProduct(null)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-4 text-sm">
              <div className="flex items-start gap-4">
                <img
                  src={detailProduct.imageUrl}
                  alt={detailProduct.name}
                  className="w-24 h-24 rounded-lg object-cover border border-gray-200 shadow-sm"
                />
                <div className="space-y-1">
                  <h4 className="font-bold text-gray-900 text-base">{detailProduct.name}</h4>
                  <p className="text-xs text-gray-500 font-mono">SKU 编码: {detailProduct.sku}</p>
                  <p className="text-xs text-gray-600 mt-1 leading-relaxed">
                    {detailProduct.description || '暂无详细描述信息。'}
                  </p>
                  {detailProduct.tags && detailProduct.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {detailProduct.tags.map((t) => (
                        <span key={t} className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full font-medium">
                          #{t}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Financial & Stock Matrix */}
              <div className="grid grid-cols-3 gap-3 p-3 bg-gray-50 rounded-xl border border-gray-200 text-center">
                <div>
                  <div className="text-xs text-gray-500">零售标价</div>
                  <div className="text-base font-bold text-gray-900 mt-0.5">¥{detailProduct.price.toFixed(2)}</div>
                  <div className="text-[11px] text-gray-400 line-through">原价 ¥{(detailProduct.originalPrice || detailProduct.price * 1.2).toFixed(2)}</div>
                </div>
                <div>
                  <div className="text-xs text-gray-500">采购成本 / 毛利率</div>
                  <div className="text-base font-bold text-emerald-700 mt-0.5">
                    ¥{(detailProduct.costPrice || detailProduct.price * 0.55).toFixed(2)}
                  </div>
                  <div className="text-[11px] text-emerald-600 font-medium">
                    毛利率 {Math.max(0, (((detailProduct.price - (detailProduct.costPrice || detailProduct.price * 0.55)) / detailProduct.price) * 100)).toFixed(1)}%
                  </div>
                </div>
                <div>
                  <div className="text-xs text-gray-500">现存可用库存</div>
                  <div className="text-base font-bold text-blue-600 mt-0.5">{detailProduct.stock} 件</div>
                  <div className="text-[11px] text-gray-500">安全警戒: {detailProduct.safetyStock || 10} 件</div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs text-gray-600">
                <div className="p-2.5 bg-[#F8FAFC] rounded-lg border border-gray-200">
                  <span className="text-gray-400 block mb-1">上架及创建时间</span>
                  <span className="font-semibold text-gray-800">{detailProduct.createdAt}</span>
                </div>
                <div className="p-2.5 bg-[#F8FAFC] rounded-lg border border-gray-200">
                  <span className="text-gray-400 block mb-1">累计成交出库</span>
                  <span className="font-semibold text-gray-800">{detailProduct.salesCount ?? 0} 件商品</span>
                </div>
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => {
                  setDetailProduct(null);
                  handleOpenEditModal(detailProduct);
                }}
                className="px-4 py-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700 text-xs font-semibold"
              >
                编辑此商品
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Quick Stock Adjustment Modal */}
      {stockAdjustProduct && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                库存调整与盘点录入
              </h3>
              <button
                onClick={() => setStockAdjustProduct(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3.5 text-sm">
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-200">
                <div className="font-semibold text-gray-800">{stockAdjustProduct.name}</div>
                <div className="text-xs text-gray-500 mt-0.5">SKU: {stockAdjustProduct.sku} | 原库存: <strong className="text-gray-900">{stockAdjustProduct.stock}</strong> 件</div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  修正后新库存数量 *
                </label>
                <input
                  type="number"
                  min="0"
                  value={adjustedStockValue}
                  onChange={(e) => setAdjustedStockValue(parseInt(e.target.value, 10) || 0)}
                  className="w-full h-[38px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-semibold"
                />
                <div className="text-xs text-gray-400 mt-1">
                  变动差额: {adjustedStockValue - stockAdjustProduct.stock >= 0 ? `+${adjustedStockValue - stockAdjustProduct.stock}` : adjustedStockValue - stockAdjustProduct.stock} 件
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  调库原由 / 盘点凭据备注
                </label>
                <input
                  type="text"
                  value={stockAdjustReason}
                  onChange={(e) => setStockAdjustReason(e.target.value)}
                  placeholder="例如: 仓库补货到货、破损报废报损、定期实物盘点..."
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-xs"
                />
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setStockAdjustProduct(null)}
                className="px-3.5 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleConfirmStockAdjust}
                className="px-4 py-1.5 rounded-lg bg-blue-600 text-white hover:bg-blue-700 text-xs font-semibold shadow-xs"
              >
                确认调库
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Batch Change Category Modal */}
      {batchCategoryOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-sm w-full p-6 border border-gray-200 shadow-xl animate-in zoom-in-95">
            <h3 className="text-base font-bold text-gray-900 mb-2">批量变更商品类目</h3>
            <p className="text-xs text-gray-500 mb-4">
              选中的 {selectedIds.length} 款商品将被批量归属到下列分类：
            </p>
            <div className="mb-4">
              <select
                value={targetBatchCategory}
                onChange={(e) => setTargetBatchCategory(e.target.value as ProductCategory)}
                className="w-full h-[38px] px-3 text-sm rounded-lg border border-gray-300 bg-white focus:border-blue-500 outline-none"
              >
                {categoryOptions.map((option) => (
                  <option key={option.id} value={option.code}>{option.name}</option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setBatchCategoryOpen(false)}
                className="px-3.5 py-1.5 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleConfirmBatchCategory}
                className="px-4 py-1.5 rounded-lg bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700"
              >
                确认转移
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add / Edit Product Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs overflow-y-auto">
          <div className="bg-white rounded-xl max-w-xl w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95 duration-150 my-8">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                {editingProduct ? '编辑商品档案与定价' : '创建新商品 (New Product SKU)'}
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmitForm} className="py-4 space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  商品名称 *
                </label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="例如: 旗舰主动降噪无线耳机 Pro"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    所属分类 *
                  </label>
                  <select
                    value={formData.category}
                    onChange={(e) =>
                      setFormData({ ...formData, category: e.target.value as ProductCategory })
                    }
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm bg-white"
                  >
                    {categoryOptions.map((option) => (
                      <option key={option.id} value={option.code}>{option.name}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    SKU 编号 / 货号
                  </label>
                  <input
                    type="text"
                    value={formData.sku}
                    onChange={(e) => setFormData({ ...formData, sku: e.target.value })}
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-mono"
                  />
                </div>
              </div>

              {/* Price & Cost Matrix */}
              <div className="p-3 bg-gray-50 rounded-xl border border-gray-200">
                <div className="text-xs font-bold text-gray-700 mb-2 flex items-center gap-1">
                  <Percent className="w-3.5 h-3.5 text-blue-600" />
                  <span>价格核算与毛利预估</span>
                </div>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="block text-[11px] font-medium text-gray-600 mb-1">
                      零售售价 (¥) *
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      required
                      value={formData.price}
                      onChange={(e) =>
                        setFormData({ ...formData, price: parseFloat(e.target.value) || 0 })
                      }
                      className="w-full h-[34px] px-2.5 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-semibold bg-white"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] font-medium text-gray-600 mb-1">
                      采购成本 (¥)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={formData.costPrice}
                      onChange={(e) =>
                        setFormData({ ...formData, costPrice: parseFloat(e.target.value) || 0 })
                      }
                      className="w-full h-[34px] px-2.5 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-semibold bg-white"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] font-medium text-gray-600 mb-1">
                      预估单件毛利
                    </label>
                    <div className="h-[34px] px-2.5 rounded-lg border border-gray-200 bg-white flex items-center text-xs font-bold text-emerald-700">
                      ¥{(formData.price - formData.costPrice).toFixed(2)} ({formData.price > 0 ? (((formData.price - formData.costPrice) / formData.price) * 100).toFixed(0) : 0}%)
                    </div>
                  </div>
                </div>
              </div>

              {/* Stock & Safety Stock */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    初始库存数量 (件) *
                  </label>
                  <input
                    type="number"
                    min="0"
                    required
                    value={formData.stock}
                    onChange={(e) =>
                      setFormData({ ...formData, stock: parseInt(e.target.value, 10) || 0 })
                    }
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-semibold"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    安全库存警戒线 (件)
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={formData.safetyStock}
                    onChange={(e) =>
                      setFormData({ ...formData, safetyStock: parseInt(e.target.value, 10) || 0 })
                    }
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-semibold"
                  />
                </div>
              </div>

              {/* Tags Editor */}
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  业务特征标签 (Tags)
                </label>
                <div className="flex gap-2 mb-2">
                  <input
                    type="text"
                    value={formData.newTagInput}
                    onChange={(e) => setFormData({ ...formData, newTagInput: e.target.value })}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddTag();
                      }
                    }}
                    placeholder="输入标签名并回车，如：爆款、高毛利、免邮"
                    className="flex-1 h-[34px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-xs"
                  />
                  <button
                    type="button"
                    onClick={handleAddTag}
                    className="px-3 h-[34px] bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-xs font-medium"
                  >
                    添加
                  </button>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {formData.tags.map((t) => (
                    <span
                      key={t}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs bg-blue-50 text-blue-700 border border-blue-200"
                    >
                      {t}
                      <button
                        type="button"
                        onClick={() => handleRemoveTag(t)}
                        className="hover:text-red-600"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  商品主图 URL
                </label>
                <input
                  type="url"
                  value={formData.imageUrl}
                  onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
                  placeholder="https://..."
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  商品规格与卖点描述
                </label>
                <textarea
                  rows={2}
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder="商品卖点、包装清单、质保说明..."
                  className="w-full p-2.5 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-xs"
                />
              </div>

              <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-xs font-medium cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 text-xs font-semibold shadow-xs cursor-pointer"
                >
                  {editingProduct ? '保存修改' : '确认添加'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteConfirmId && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-sm w-full p-6 border border-gray-200 shadow-xl animate-in zoom-in-95">
            <div className="flex items-center gap-3 text-red-600 mb-3">
              <AlertTriangle className="w-6 h-6" />
              <h3 className="text-base font-bold text-gray-900">确认删除该商品？</h3>
            </div>
            <p className="text-xs text-gray-500 mb-5 leading-relaxed">
              此操作将永久移除该商品目录及关联历史库存记录，不可撤回。
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeleteConfirmId(null)}
                className="px-3.5 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-xs font-medium cursor-pointer"
              >
                取消
              </button>
              <button
                onClick={() => {
                  deleteProduct(deleteConfirmId);
                  setDeleteConfirmId(null);
                }}
                className="px-4 py-1.5 rounded-lg bg-red-600 text-white hover:bg-red-700 text-xs font-semibold cursor-pointer"
              >
                确认删除
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
