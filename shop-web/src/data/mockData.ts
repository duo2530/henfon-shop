import { Product, Order, User, TodoItem, DailySalesData, NotificationItem } from '../types';

export const initialProducts: Product[] = [
  {
    id: 'prod-1',
    sku: 'SKU-ELEC-901',
    name: 'Premium Smartphone X (旗舰智能手机)',
    category: 'electronics',
    categoryName: '电子产品 (Electronics)',
    price: 5999.00,
    originalPrice: 6499.00,
    costPrice: 4200.00,
    stock: 124,
    safetyStock: 30,
    status: 'active',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAlvD14m9-XrH9KRs4h-JEDe4h7KIfFiItwTjcohx8NqxWTi2pXFF_6se1R9UsajR8zccs1ul8qznLKFiA9Anr-nAyJ41LafHljcdjlV37mKzTwAvSLesd4f-rJjY6H7NGXH8c6vvhFz645dh566Oj1kfSEQdKdKUQ-7i9ZEbc1dHJmhLXRoHS5pjQHYM16DPTn3Lxd-a86sB3WICRz6hwWR7rVV6lO8XyADWSgCkI7bj4xlIYYS-SZ',
    salesCount: 842,
    createdAt: '2023-08-15',
    description: '采用高通骁龙旗舰芯片，具备超视网膜XDR显示屏及顶级摄影系统。',
    tags: ['新品爆款', '高毛利', '官方标配']
  },
  {
    id: 'prod-2',
    sku: 'SKU-HOME-402',
    name: 'Minimalist Desk Lamp (极简护眼台灯)',
    category: 'home',
    categoryName: '家居 (Home)',
    price: 299.00,
    originalPrice: 359.00,
    costPrice: 135.00,
    stock: 45,
    safetyStock: 20,
    status: 'active',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDHdmIGIRxecKTzQHIybT2nAvDKHZ2tfHnc1hM5Pr5Q75-GGTK_j2cjGf4R1PmBvW36_W4vHcCkhW82BynLaZSlCLon0IzYGVNGoMXtcS4rX2mX3zgHvZHj0lb_54M5zUnz63PPVSKbL48y3Cc_osuZgKSS51SkS-qZU2a1TIRMidmrLZ2y5vD3-IJ5rd9rqDf9kvZHO5xhM0G-9Bv5qUpLsLAq0gIbPLyEEfbXRftTCwBbhrOSUR6A',
    salesCount: 1205,
    createdAt: '2023-09-02',
    description: '无频闪无蓝光危害，多色温触控调光，极简铝合金拉丝工艺。',
    tags: ['热销推荐', '德国红点奖']
  },
  {
    id: 'prod-3',
    sku: 'SKU-CLOTH-108',
    name: 'Essential Cotton T-Shirt (纯棉重磅短袖)',
    category: 'clothing',
    categoryName: '服装 (Clothing)',
    price: 99.00,
    originalPrice: 129.00,
    costPrice: 38.00,
    stock: 0,
    safetyStock: 50,
    status: 'inactive',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBWB_HIM3S148Mn5BfjPpE3GHU3Sgq7_ei__4v2DCQdrIJq-9UojwnKzBTjULtgcFGTl5BweuOzT19_DUN4w8vki5fVDnQIevXxEKV8nu-I8SJtzLniJl1X41nNKNBTdFHZrdsfJFl1QXJPgsZE5yUaGhUluHOeJoyF09bqdxO1pw5EksIQY6004ATons_nrw-IfLMvt20kmhS-UAUCMv4IjX273iMSmfiG7T9WkdROYSc2XNNhCXPd',
    salesCount: 3410,
    createdAt: '2023-06-10',
    description: '240g精梳双纱纯棉，亲肤透气不易变形，基础百搭版型。',
    tags: ['断货预警', '夏季清仓']
  },
  {
    id: 'prod-4',
    sku: 'SKU-ELEC-330',
    name: 'Noise Cancelling Headphones (头戴式无线降噪耳机)',
    category: 'electronics',
    categoryName: '电子产品 (Electronics)',
    price: 1299.00,
    originalPrice: 1499.00,
    costPrice: 750.00,
    stock: 8,
    safetyStock: 15,
    status: 'active',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBJYLLmYa5iCc0jPM-ccskbGablu2pUW0rosoHNzDhlc_BUrKO7i198_PqKTc4_vEQdLiXWUR3p8l5tOH_EYl9b9VA7uJtN90IMDbn5UKrucqgsp54DB3KITGSoZwsImrojQ8eOiY2q7vu23XWIcF6F7UQyBH0M3uJJ9lPtlyJJ-1ryD0rBNn5Po75tzkSK9q5aB9nX0NCRgi7BvTy0nxX1kIeXeDCZieuXJcFs7qzRyrpupvhz5h2f',
    salesCount: 529,
    createdAt: '2023-10-01',
    description: '主动深度降噪，40mm镀钛驱动单元，40小时超长续航。',
    tags: ['库存紧张', 'Hi-Res金标']
  },
  {
    id: 'prod-5',
    sku: 'SKU-HOME-661',
    name: 'Ergonomic Office Chair (人体工学办公椅)',
    category: 'home',
    categoryName: '家居 (Home)',
    price: 2450.00,
    originalPrice: 2899.00,
    costPrice: 1380.00,
    stock: 32,
    safetyStock: 10,
    status: 'active',
    imageUrl: 'https://images.unsplash.com/photo-1580481077197-2a62886f4a8b?w=400&auto=format&fit=crop&q=80',
    salesCount: 280,
    createdAt: '2023-09-18',
    description: '三维动态腰托调节，高透气网布，经过BIFMA认证气压棒。',
    tags: ['大件物流', '企业直采']
  },
  {
    id: 'prod-6',
    sku: 'SKU-ELEC-112',
    name: 'Mechanical Keyboard (客制化机械键盘)',
    category: 'electronics',
    categoryName: '电子产品 (Electronics)',
    price: 650.00,
    originalPrice: 799.00,
    costPrice: 320.00,
    stock: 64,
    safetyStock: 25,
    status: 'active',
    imageUrl: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=400&auto=format&fit=crop&q=80',
    salesCount: 910,
    createdAt: '2023-07-22',
    description: '三模热插拔轴体，全键无冲RGB背光，Gasket结构降噪。',
    tags: ['极客外设', '客制化']
  },
  {
    id: 'prod-7',
    sku: 'SKU-ELEC-550',
    name: 'Smart Watch Series 5 (智能运动手表)',
    category: 'electronics',
    categoryName: '电子产品 (Electronics)',
    price: 1800.00,
    originalPrice: 1999.00,
    costPrice: 980.00,
    stock: 88,
    safetyStock: 20,
    status: 'active',
    imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&auto=format&fit=crop&q=80',
    salesCount: 640,
    createdAt: '2023-11-05',
    description: '心率血氧连续监测，GPS双频独立定位，50米防水等级。',
    tags: ['健康监测', '畅销榜TOP3']
  }
];

export const initialOrders: Order[] = [
  {
    id: 'ord-1',
    orderNumber: 'ORD-20231024-001',
    createdAt: '2023-10-24 14:30:22',
    customerName: '张伟',
    customerPhone: '138****5678',
    customerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBcGhpF2uSGRNcEQkEHNMSQ_mvwACea2F7XnrNO_xDKkJx8ZJDFG_Py31OKiqOBQAHwo-pLvAdiDqfFnVikYXfmGqvbX3Ewc_TSfCFNH_YnLvpkaVwHUslFKBzlkkw6M4d_KWbX-JtTHe5XL0E0Q0ItiyKxadRhkYvXMANkWTbuWQJuBot1e0P5wbOTF3neIQd7KTiNyNuK2ukeE4csKKBCByTrQr1iVtoJWq1fW2G4UmPoXWS8_Sg1',
    amount: 1299.00,
    discountAmount: 100.00,
    freightAmount: 0.00,
    paymentMethod: 'wechat',
    status: 'pending_shipment',
    flagColor: 'red',
    sellerNote: '加急订单：客户催促明天发顺丰特快',
    buyerMessage: '请工作日送达，送前电话联系，谢谢！',
    invoiceTitle: '北京智联科创科技有限公司',
    taxId: '91110108MA01XXXXXX',
    items: [
      {
        productId: 'prod-4',
        productName: 'Noise Cancelling Headphones (头戴式无线降噪耳机)',
        price: 1299.00,
        quantity: 1,
        sku: 'SKU-ELEC-330',
        imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBJYLLmYa5iCc0jPM-ccskbGablu2pUW0rosoHNzDhlc_BUrKO7i198_PqKTc4_vEQdLiXWUR3p8l5tOH_EYl9b9VA7uJtN90IMDbn5UKrucqgsp54DB3KITGSoZwsImrojQ8eOiY2q7vu23XWIcF6F7UQyBH0M3uJJ9lPtlyJJ-1ryD0rBNn5Po75tzkSK9q5aB9nX0NCRgi7BvTy0nxX1kIeXeDCZieuXJcFs7qzRyrpupvhz5h2f'
      }
    ],
    shippingAddress: '北京市海淀区中关村南大街1号科技大厦A座1802室',
    notes: '请工作日送达，送前电话联系'
  },
  {
    id: 'ord-2',
    orderNumber: 'ORD-20231024-002',
    createdAt: '2023-10-24 13:15:05',
    customerName: '李娜',
    customerPhone: '139****1234',
    customerAvatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuB0bbCzyjAQaoeWhcvxMChP_6QAod2AMszGK97t2NU0KDpVp1qB8skATyWXtTlWAid4Pq84EqWDYbjHQBXMDBBAml924P3Ozm1daBONP_0EhihkDTHcva-UsgXPkO--C_CDZLXtxHrXhD0S30aGnWkjH_YsUmsRJqvntn67ydN16NO2c0rRZUeuqiWTFKOlEcCC4mpn9BOelao-MYrDdnHz-LGoNxSoeyWNqicx4NvsOFfMDIUvekF4',
    amount: 450.00,
    discountAmount: 0.00,
    freightAmount: 12.00,
    paymentMethod: 'alipay',
    status: 'shipped',
    flagColor: 'blue',
    sellerNote: '已于14:00顺丰揽件出库',
    items: [
      {
        productId: 'prod-2',
        productName: 'Minimalist Desk Lamp (极简护眼台灯)',
        price: 299.00,
        quantity: 1,
        sku: 'SKU-HOME-402',
        imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDHdmIGIRxecKTzQHIybT2nAvDKHZ2tfHnc1hM5Pr5Q75-GGTK_j2cjGf4R1PmBvW36_W4vHcCkhW82BynLaZSlCLon0IzYGVNGoMXtcS4rX2mX3zgHvZHj0lb_54M5zUnz63PPVSKbL48y3Cc_osuZgKSS51SkS-qZU2a1TIRMidmrLZ2y5vD3-IJ5rd9rqDf9kvZHO5xhM0G-9Bv5qUpLsLAq0gIbPLyEEfbXRftTCwBbhrOSUR6A'
      }
    ],
    shippingAddress: '上海市浦东新区张江高科技园区碧波路888号',
    trackingNumber: 'SF14285799201',
    shippingCarrier: '顺丰速运',
    logisticsSteps: [
      { time: '2023-10-24 16:30', title: '运输中', desc: '快件到达【上海浦东中转场】正发往目的地网点', status: 'current' },
      { time: '2023-10-24 14:10', title: '已揽收', desc: '顺丰速运已收取快件 (快递员: 王师傅 13918239921)', status: 'completed' },
      { time: '2023-10-24 13:20', title: '包裹出库', desc: '华东1号自动化中央仓分拣完成已打包', status: 'completed' },
      { time: '2023-10-24 13:15', title: '已支付', desc: '买家已通过支付宝成功付款 ¥450.00', status: 'completed' }
    ]
  },
  {
    id: 'ord-3',
    orderNumber: 'ORD-20231024-003',
    createdAt: '2023-10-24 11:05:40',
    customerName: '王强',
    customerPhone: '137****9988',
    amount: 3100.00,
    discountAmount: 0.00,
    freightAmount: 0.00,
    paymentMethod: 'wechat',
    status: 'pending_payment',
    flagColor: 'yellow',
    sellerNote: '待付款，若超24小时系统将自动关闭并释放库存',
    items: [
      {
        productId: 'prod-5',
        productName: 'Ergonomic Office Chair (人体工学办公椅)',
        price: 2450.00,
        quantity: 1,
        sku: 'SKU-HOME-661',
        imageUrl: 'https://images.unsplash.com/photo-1580481077197-2a62886f4a8b?w=400&auto=format&fit=crop&q=80'
      },
      {
        productId: 'prod-6',
        productName: 'Mechanical Keyboard (客制化机械键盘)',
        price: 650.00,
        quantity: 1,
        sku: 'SKU-ELEC-112',
        imageUrl: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=400&auto=format&fit=crop&q=80'
      }
    ],
    shippingAddress: '广州市天河区珠江新城花城大道50号'
  },
  {
    id: 'ord-4',
    orderNumber: 'ORD-20231023-009',
    createdAt: '2023-10-23 16:40:12',
    customerName: '陈秀',
    customerPhone: '186****3345',
    amount: 1800.00,
    discountAmount: 0.00,
    freightAmount: 0.00,
    paymentMethod: 'alipay',
    status: 'cancelled',
    notes: '用户超时未支付系统自动取消',
    items: [
      {
        productId: 'prod-7',
        productName: 'Smart Watch Series 5 (智能运动手表)',
        price: 1800.00,
        quantity: 1,
        sku: 'SKU-ELEC-550',
        imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&auto=format&fit=crop&q=80'
      }
    ],
    shippingAddress: '深圳市南山区高新南九道科技园'
  },
  {
    id: 'ord-5',
    orderNumber: 'ORD-20231023-004',
    createdAt: '2023-10-23 09:20:00',
    customerName: '赵敏',
    customerPhone: '159****8890',
    amount: 5999.00,
    discountAmount: 200.00,
    freightAmount: 0.00,
    paymentMethod: 'wechat',
    status: 'completed',
    flagColor: 'green',
    sellerNote: '五星好评客户，已发放50元复购优惠券',
    items: [
      {
        productId: 'prod-1',
        productName: 'Premium Smartphone X (旗舰智能手机)',
        price: 5999.00,
        quantity: 1,
        sku: 'SKU-ELEC-901',
        imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAlvD14m9-XrH9KRs4h-JEDe4h7KIfFiItwTjcohx8NqxWTi2pXFF_6se1R9UsajR8zccs1ul8qznLKFiA9Anr-nAyJ41LafHljcdjlV37mKzTwAvSLesd4f-rJjY6H7NGXH8c6vvhFz645dh566Oj1kfSEQdKdKUQ-7i9ZEbc1dHJmhLXRoHS5pjQHYM16DPTn3Lxd-a86sB3WICRz6hwWR7rVV6lO8XyADWSgCkI7bj4xlIYYS-SZ'
      }
    ],
    shippingAddress: '杭州市西湖区文三路华星科技大厦5楼',
    trackingNumber: 'SF99382100234',
    shippingCarrier: '顺丰速运',
    logisticsSteps: [
      { time: '2023-10-24 10:15', title: '已签收', desc: '快件已由本人签收，感谢使用顺丰速运', status: 'completed' },
      { time: '2023-10-24 08:30', title: '派送中', desc: '派件员已出发进行末端配送', status: 'completed' },
      { time: '2023-10-23 20:00', title: '干线运输', desc: '快件已发往杭州转运中心', status: 'completed' },
      { time: '2023-10-23 11:30', title: '已揽收', desc: '仓库打包完成已交接顺丰', status: 'completed' }
    ]
  },
  {
    id: 'ord-6',
    orderNumber: 'ORD-20231022-018',
    createdAt: '2023-10-22 18:10:30',
    customerName: '周建军',
    customerPhone: '133****4567',
    amount: 198.00,
    discountAmount: 0.00,
    freightAmount: 0.00,
    paymentMethod: 'wechat',
    status: 'refunded',
    flagColor: 'purple',
    sellerNote: '客户申请尺码不合退货退款，已验收退回商品并原路退回金额',
    refundReason: '商品尺码/规格不符',
    refundAmount: 198.00,
    refundStatus: 'approved',
    items: [
      {
        productId: 'prod-3',
        productName: 'Essential Cotton T-Shirt (纯棉重磅短袖)',
        price: 99.00,
        quantity: 2,
        sku: 'SKU-CLOTH-108',
        imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBWB_HIM3S148Mn5BfjPpE3GHU3Sgq7_ei__4v2DCQdrIJq-9UojwnKzBTjULtgcFGTl5BweuOzT19_DUN4w8vki5fVDnQIevXxEKV8nu-I8SJtzLniJl1X41nNKNBTdFHZrdsfJFl1QXJPgsZE5yUaGhUluHOeJoyF09bqdxO1pw5EksIQY6004ATons_nrw-IfLMvt20kmhS-UAUCMv4IjX273iMSmfiG7T9WkdROYSc2XNNhCXPd'
      }
    ],
    shippingAddress: '成都市高新区天府大道北段1700号新世纪环球中心'
  }
];

export const initialUsers: User[] = [
  {
    id: 'usr-1',
    userCode: 'USR-9921',
    name: 'Li Wei (李薇)',
    phone: '+86 138 **** 5521',
    avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuB0bbCzyjAQaoeWhcvxMChP_6QAod2AMszGK97t2NU0KDpVp1qB8skATyWXtTlWAid4Pq84EqWDYbjHQBXMDBBAml924P3Ozm1daBONP_0EhihkDTHcva-UsgXPkO--C_CDZLXtxHrXhD0S30aGnWkjH_YsUmsRJqvntn67ydN16NO2c0rRZUeuqiWTFKOlEcCC4mpn9BOelao-MYrDdnHz-LGoNxSoeyWNqicx4NvsOFfMDIUvekF4',
    email: 'liwei.ops@example.com',
    registeredAt: '2023-10-15 14:30',
    totalSpent: 12500.00,
    orderCount: 14,
    status: 'active',
    tier: 'platinum',
    lastActive: '10分钟前',
    balance: 850.00,
    points: 1250,
    growthValue: 9800,
    tags: ['黑金VIP', '数码极客', '高复购率'],
    notes: '核心高价值会员，多次参与新品首发内测预售'
  },
  {
    id: 'usr-2',
    userCode: 'USR-8832',
    name: 'Zhang Qiang (张强)',
    phone: '+86 159 **** 8890',
    email: 'zhang.qiang@example.com',
    registeredAt: '2023-11-02 09:15',
    totalSpent: 3240.50,
    orderCount: 5,
    status: 'active',
    tier: 'gold',
    lastActive: '1小时前',
    balance: 120.00,
    points: 320,
    growthValue: 3240,
    tags: ['家居达人', '注重品质'],
    notes: '办公居家用品忠实买家'
  },
  {
    id: 'usr-3',
    userCode: 'USR-7719',
    name: 'Chen Ming (陈明)',
    phone: '+86 186 **** 3345',
    avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBcGhpF2uSGRNcEQkEHNMSQ_mvwACea2F7XnrNO_xDKkJx8ZJDFG_Py31OKiqOBQAHwo-pLvAdiDqfFnVikYXfmGqvbX3Ewc_TSfCFNH_YnLvpkaVwHUslFKBzlkkw6M4d_KWbX-JtTHe5XL0E0Q0ItiyKxadRhkYvXMANkWTbuWQJuBot1e0P5wbOTF3neIQd7KTiNyNuK2ukeE4csKKBCByTrQr1iVtoJWq1fW2G4UmPoXWS8_Sg1',
    email: 'chenming_99@example.com',
    registeredAt: '2024-01-20 16:45',
    totalSpent: 0.00,
    orderCount: 0,
    status: 'suspended',
    tier: 'regular',
    lastActive: '3天前',
    balance: 0.00,
    points: 0,
    growthValue: 0,
    tags: ['风险账户', '待核实'],
    notes: '多次尝试虚假支付，风控系统自动冻结权限'
  },
  {
    id: 'usr-4',
    userCode: 'USR-6623',
    name: 'Wang Fang (王芳)',
    phone: '+86 135 **** 6789',
    email: 'wangfang.sz@example.com',
    registeredAt: '2023-12-05 10:12',
    totalSpent: 8920.00,
    orderCount: 9,
    status: 'active',
    tier: 'gold',
    lastActive: '2小时前',
    balance: 450.00,
    points: 890,
    growthValue: 8920,
    tags: ['高客单价', '企业买家'],
    notes: '经常批量采购办公设备与工位灯具'
  },
  {
    id: 'usr-5',
    userCode: 'USR-5541',
    name: 'Liu Yang (刘洋)',
    phone: '+86 177 **** 2341',
    email: 'liuyang.dev@example.com',
    registeredAt: '2024-02-11 18:20',
    totalSpent: 1450.00,
    orderCount: 2,
    status: 'active',
    tier: 'silver',
    lastActive: '5分钟前',
    balance: 50.00,
    points: 145,
    growthValue: 1450,
    tags: ['新晋银卡', '活跃买家'],
    notes: '对优惠券敏感度较高，可推送促销触达'
  }
];

export const initialTodos: TodoItem[] = [
  {
    id: 'todo-1',
    title: '5个退款申请待处理',
    subtitle: 'Requires immediate attention (需尽快审核退款凭证)',
    type: 'refund',
    count: 5,
    urgent: true,
    linkTab: 'orders'
  },
  {
    id: 'todo-2',
    title: '2个商品缺货预警',
    subtitle: 'Restock needed (纯棉重磅短袖等已售罄)',
    type: 'stock_alert',
    count: 2,
    urgent: true,
    linkTab: 'products'
  },
  {
    id: 'todo-3',
    title: '12条未读客服消息',
    subtitle: 'Customer inquiries (来自商品详情页咨询)',
    type: 'message',
    count: 12,
    urgent: false
  }
];

export const salesTrends30Days: DailySalesData[] = [
  { date: '10-01', fullDate: '2023-10-01', sales: 28400, volume: 198, orders: 85, avgOrderValue: 334.1 },
  { date: '10-04', fullDate: '2023-10-04', sales: 34200, volume: 245, orders: 98, avgOrderValue: 348.9 },
  { date: '10-08', fullDate: '2023-10-08', sales: 29800, volume: 210, orders: 89, avgOrderValue: 334.8 },
  { date: '10-12', fullDate: '2023-10-12', sales: 41200, volume: 292, orders: 115, avgOrderValue: 358.2 },
  { date: '10-16', fullDate: '2023-10-16', sales: 38900, volume: 268, orders: 104, avgOrderValue: 374.0 },
  { date: '10-20', fullDate: '2023-10-20', sales: 49500, volume: 348, orders: 136, avgOrderValue: 363.9 },
  { date: '10-24', fullDate: '2023-10-24', sales: 45290, volume: 312, orders: 128, avgOrderValue: 353.8 },
  { date: '10-28', fullDate: '2023-10-28', sales: 52100, volume: 370, orders: 145, avgOrderValue: 359.3 }
];

export const salesTrends7Days: DailySalesData[] = [
  { date: '08-23 周一', fullDate: '2026-08-23', sales: 38200, volume: 268, orders: 102, avgOrderValue: 374.5 },
  { date: '08-24 周二', fullDate: '2026-08-24', sales: 41500, volume: 295, orders: 118, avgOrderValue: 351.6 },
  { date: '08-25 周三', fullDate: '2026-08-25', sales: 39800, volume: 279, orders: 110, avgOrderValue: 361.8 },
  { date: '08-26 周四', fullDate: '2026-08-26', sales: 45290, volume: 326, orders: 128, avgOrderValue: 353.8 },
  { date: '08-27 周五', fullDate: '2026-08-27', sales: 48900, volume: 354, orders: 135, avgOrderValue: 362.2 },
  { date: '08-28 周六', fullDate: '2026-08-28', sales: 54100, volume: 396, orders: 152, avgOrderValue: 355.9 },
  { date: '08-29 周日', fullDate: '2026-08-29', sales: 51200, volume: 368, orders: 144, avgOrderValue: 355.5 }
];

export const initialNotifications: NotificationItem[] = [
  {
    id: 'notif-1',
    title: '新订单提交通知',
    content: '用户 张伟 刚刚完成了订单 #ORD-20231024-001 的支付，请及时处理发货。',
    time: '5分钟前',
    read: false,
    type: 'order'
  },
  {
    id: 'notif-2',
    title: '库存告警提示',
    content: '商品 [Essential Cotton T-Shirt] 库存降至 0，已自动变更为下架状态。',
    time: '25分钟前',
    read: false,
    type: 'stock'
  },
  {
    id: 'notif-3',
    title: '系统维护完成',
    content: '双十一运营大促服务器弹性扩容已完成，系统运行平稳。',
    time: '2小时前',
    read: true,
    type: 'system'
  }
];
