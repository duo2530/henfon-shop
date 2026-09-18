-- 商品类目树种子数据：为 8 个一级类目补齐二级与三级类目，并把商品下沉到三级叶子类目。
--
-- 幂等说明：类目插入依赖 uk_catalog_category_code(category_code, is_deleted)，重复执行不会产生副本；
-- 商品归属更新以「当前 category_id = 一级类目」为前提，重复执行不会重复改写。
--
-- 用法：mysql --default-character-set=utf8mb4 -u root -p henfon-shop < category-tree.sql

-- ---------------------------------------------------------------------------
-- 二级类目（27 个）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO catalog_category
    (parent_id, category_name, category_code, level_no, sort_no, status, is_deleted, version)
SELECT p.id, v.category_name, v.category_code, 2, v.sort_no, 1, 0, 0
FROM (
              SELECT 'ELECTRONICS' AS parent_code, '智能穿戴'         AS category_name, 'ELEC_WEAR'         AS category_code, 1 AS sort_no
    UNION ALL SELECT 'ELECTRONICS',           '电脑办公外设',                       'ELEC_PC',                        2
    UNION ALL SELECT 'ELECTRONICS',           '数码配件',                           'ELEC_ACC',                       3
    UNION ALL SELECT 'ELECTRONICS',           '智能家电',                           'ELEC_SMART_HOME',                4

    UNION ALL SELECT 'CLOTHING',              '上装',                               'CLOTH_TOP',                      1
    UNION ALL SELECT 'CLOTHING',              '下装',                               'CLOTH_BOTTOM',                   2
    UNION ALL SELECT 'CLOTHING',              '鞋靴箱包',                           'CLOTH_SHOES_BAG',                3
    UNION ALL SELECT 'CLOTHING',              '服饰配饰',                           'CLOTH_ACC',                      4

    UNION ALL SELECT 'HOME',                  '家纺布艺',                           'HOME_TEXTILE',                   1
    UNION ALL SELECT 'HOME',                  '生活电器',                           'HOME_APPLIANCE',                 2
    UNION ALL SELECT 'HOME',                  '收纳与照明',                         'HOME_STORAGE',                   3
    UNION ALL SELECT 'HOME',                  '餐厨水具',                           'HOME_DINING',                    4

    UNION ALL SELECT 'BEAUTY',                '护肤',                               'BEAUTY_SKIN',                    1
    UNION ALL SELECT 'BEAUTY',                '彩妆',                               'BEAUTY_MAKEUP',                  2
    UNION ALL SELECT 'BEAUTY',                '身体与洗护',                         'BEAUTY_BODY',                    3

    UNION ALL SELECT 'FOOD',                  '休闲零食',                           'FOOD_SNACK',                     1
    UNION ALL SELECT 'FOOD',                  '冲调饮品',                           'FOOD_DRINK',                     2
    UNION ALL SELECT 'FOOD',                  '粮油乳品',                           'FOOD_STAPLE',                    3

    UNION ALL SELECT 'AUDIO',                 '耳机',                               'AUDIO_HEADSET',                  1
    UNION ALL SELECT 'AUDIO',                 '音箱',                               'AUDIO_SPEAKER',                  2
    UNION ALL SELECT 'AUDIO',                 '播放与录音',                         'AUDIO_PLAYER',                   3

    UNION ALL SELECT 'LIFESTYLE',             '咖啡豆',                             'LIFE_BEAN',                      1
    UNION ALL SELECT 'LIFESTYLE',             '便捷咖啡',                           'LIFE_INSTANT',                   2
    UNION ALL SELECT 'LIFESTYLE',             '咖啡器具与伴手礼',                   'LIFE_TOOL',                      3

    UNION ALL SELECT 'OUTDOOR',               '户外服装',                           'OUTDOOR_APPAREL',                1
    UNION ALL SELECT 'OUTDOOR',               '露营装备',                           'OUTDOOR_CAMP',                   2
    UNION ALL SELECT 'OUTDOOR',               '徒步出行',                           'OUTDOOR_HIKING',                 3
) v
JOIN catalog_category p ON p.category_code = v.parent_code AND p.is_deleted = 0;

-- ---------------------------------------------------------------------------
-- 三级类目（66 个）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO catalog_category
    (parent_id, category_name, category_code, level_no, sort_no, status, is_deleted, version)
SELECT p.id, v.category_name, v.category_code, 3, v.sort_no, 1, 0, 0
FROM (
              SELECT 'ELEC_WEAR'       AS parent_code, '智能手表'         AS category_name, 'ELEC_WATCH'         AS category_code, 1 AS sort_no
    UNION ALL SELECT 'ELEC_PC',                    '键盘鼠标',                           'ELEC_KEYMOUSE',                1
    UNION ALL SELECT 'ELEC_PC',                    '扩展坞与支架',                       'ELEC_DOCK',                    2
    UNION ALL SELECT 'ELEC_PC',                    '显示设备',                           'ELEC_SCREEN',                  3
    UNION ALL SELECT 'ELEC_ACC',                   '充电与电源',                         'ELEC_CHARGER',                 1
    UNION ALL SELECT 'ELEC_ACC',                   '耳机音箱',                           'ELEC_AUDIO',                   2
    UNION ALL SELECT 'ELEC_SMART_HOME',            '智能安防',                           'ELEC_CAMERA',                  1
    UNION ALL SELECT 'ELEC_SMART_HOME',            '清洁电器',                           'ELEC_CLEANER',                 2

    UNION ALL SELECT 'CLOTH_TOP',                  '针织与T恤',                          'CLOTH_TEE',                    1
    UNION ALL SELECT 'CLOTH_TOP',                  '卫衣与外套',                         'CLOTH_COAT',                   2
    UNION ALL SELECT 'CLOTH_TOP',                  '衬衫',                               'CLOTH_SHIRT',                  3
    UNION ALL SELECT 'CLOTH_BOTTOM',               '长裤',                               'CLOTH_PANTS',                  1
    UNION ALL SELECT 'CLOTH_SHOES_BAG',            '运动鞋',                             'CLOTH_SHOES',                  1
    UNION ALL SELECT 'CLOTH_SHOES_BAG',            '双肩包',                             'CLOTH_BACKPACK',               2
    UNION ALL SELECT 'CLOTH_SHOES_BAG',            '女包',                               'CLOTH_HANDBAG',                3
    UNION ALL SELECT 'CLOTH_ACC',                  '帽子',                               'CLOTH_HAT',                    1
    UNION ALL SELECT 'CLOTH_ACC',                  '腰带',                               'CLOTH_BELT',                   2

    UNION ALL SELECT 'HOME_TEXTILE',               '床品套件',                           'HOME_BEDDING',                 1
    UNION ALL SELECT 'HOME_TEXTILE',               '卫浴布艺',                           'HOME_BATH',                    2
    UNION ALL SELECT 'HOME_TEXTILE',               '窗帘',                               'HOME_CURTAIN',                 3
    UNION ALL SELECT 'HOME_APPLIANCE',             '厨房小电',                           'HOME_KITCHEN',                 1
    UNION ALL SELECT 'HOME_APPLIANCE',             '环境电器',                           'HOME_AIR',                     2
    UNION ALL SELECT 'HOME_APPLIANCE',             '清洁电器',                           'HOME_CLEAN',                   3
    UNION ALL SELECT 'HOME_STORAGE',               '收纳整理',                           'HOME_ORGANIZE',                1
    UNION ALL SELECT 'HOME_STORAGE',               '灯具照明',                           'HOME_LIGHT',                   2
    UNION ALL SELECT 'HOME_DINING',                '水具杯壶',                           'HOME_CUP',                     1
    UNION ALL SELECT 'HOME_DINING',                '餐具',                               'HOME_TABLEWARE',               2

    UNION ALL SELECT 'BEAUTY_SKIN',                '精华面霜',                           'BEAUTY_ESSENCE',               1
    UNION ALL SELECT 'BEAUTY_SKIN',                '清洁卸妆',                           'BEAUTY_CLEANSE',               2
    UNION ALL SELECT 'BEAUTY_SKIN',                '面膜与防晒',                         'BEAUTY_MASK',                  3
    UNION ALL SELECT 'BEAUTY_MAKEUP',              '底妆',                               'BEAUTY_BASE',                  1
    UNION ALL SELECT 'BEAUTY_MAKEUP',              '唇妆',                               'BEAUTY_LIP',                   2
    UNION ALL SELECT 'BEAUTY_BODY',                '身体护理',                           'BEAUTY_BODYCARE',              1
    UNION ALL SELECT 'BEAUTY_BODY',                '洗发护发',                           'BEAUTY_HAIR',                  2

    UNION ALL SELECT 'FOOD_SNACK',                 '坚果炒货',                           'FOOD_NUTS',                    1
    UNION ALL SELECT 'FOOD_SNACK',                 '果干蜜饯',                           'FOOD_DRIED',                   2
    UNION ALL SELECT 'FOOD_SNACK',                 '肉干海味',                           'FOOD_MEAT',                    3
    UNION ALL SELECT 'FOOD_DRINK',                 '咖啡豆',                             'FOOD_COFFEE',                  1
    UNION ALL SELECT 'FOOD_DRINK',                 '茶叶',                               'FOOD_TEA',                     2
    UNION ALL SELECT 'FOOD_DRINK',                 '蜂蜜冲饮',                           'FOOD_HONEY',                   3
    UNION ALL SELECT 'FOOD_STAPLE',                '麦片谷物',                           'FOOD_CEREAL',                  1
    UNION ALL SELECT 'FOOD_STAPLE',                '乳制品',                             'FOOD_DAIRY',                   2
    UNION ALL SELECT 'FOOD_STAPLE',                '调味料',                             'FOOD_SEASONING',               3

    UNION ALL SELECT 'AUDIO_HEADSET',              '头戴耳机',                           'AUDIO_OVER_EAR',               1
    UNION ALL SELECT 'AUDIO_HEADSET',              '真无线耳机',                         'AUDIO_TWS',                    2
    UNION ALL SELECT 'AUDIO_HEADSET',              '运动耳机',                           'AUDIO_SPORT',                  3
    UNION ALL SELECT 'AUDIO_SPEAKER',              '桌面音箱',                           'AUDIO_DESKTOP',                1
    UNION ALL SELECT 'AUDIO_SPEAKER',              '便携音箱',                           'AUDIO_PORTABLE',               2
    UNION ALL SELECT 'AUDIO_SPEAKER',              '家庭影院',                           'AUDIO_CINEMA',                 3
    UNION ALL SELECT 'AUDIO_PLAYER',               '播放器',                             'AUDIO_HIFI',                   1
    UNION ALL SELECT 'AUDIO_PLAYER',               '麦克风',                             'AUDIO_MIC',                    2

    UNION ALL SELECT 'LIFE_BEAN',                  '单品豆',                             'LIFE_SINGLE',                  1
    UNION ALL SELECT 'LIFE_BEAN',                  '拼配豆',                             'LIFE_BLEND',                   2
    UNION ALL SELECT 'LIFE_INSTANT',               '冷萃浓缩',                           'LIFE_COLD',                    1
    UNION ALL SELECT 'LIFE_INSTANT',               '速溶拿铁',                           'LIFE_LATTE',                   2
    UNION ALL SELECT 'LIFE_INSTANT',               '挂耳咖啡',                           'LIFE_DRIP',                    3
    UNION ALL SELECT 'LIFE_TOOL',                  '器具耗材',                           'LIFE_GEAR',                    1
    UNION ALL SELECT 'LIFE_TOOL',                  '烘焙伴手礼',                         'LIFE_GIFT',                    2

    UNION ALL SELECT 'OUTDOOR_APPAREL',            '冲锋衣',                             'OUTDOOR_JACKET',               1
    UNION ALL SELECT 'OUTDOOR_APPAREL',            '运动裤',                             'OUTDOOR_PANTS',                2
    UNION ALL SELECT 'OUTDOOR_CAMP',               '帐篷天幕',                           'OUTDOOR_TENT',                 1
    UNION ALL SELECT 'OUTDOOR_CAMP',               '桌椅',                               'OUTDOOR_FURNITURE',            2
    UNION ALL SELECT 'OUTDOOR_CAMP',               '野餐炊具',                           'OUTDOOR_PICNIC',               3
    UNION ALL SELECT 'OUTDOOR_HIKING',             '徒步鞋',                             'OUTDOOR_SHOES',                1
    UNION ALL SELECT 'OUTDOOR_HIKING',             '背包腰包',                           'OUTDOOR_BAG',                  2
    UNION ALL SELECT 'OUTDOOR_HIKING',             '遮阳帽',                             'OUTDOOR_HAT',                  3
) v
JOIN catalog_category p ON p.category_code = v.parent_code AND p.is_deleted = 0;

-- ---------------------------------------------------------------------------
-- 商品归属：一级类目下沉到三级叶子类目，同步冗余的分类名称
-- source_id 是商品当前所属的一级类目 ID，既定位改动范围，也用于区分同名商品
-- （如「精品手冲咖啡豆」同时存在于食品生鲜与咖啡美食，只靠名称前缀无法区分）。
-- ---------------------------------------------------------------------------
UPDATE catalog_product p
JOIN (
              SELECT 1 AS source_id, '智能运动手表'       AS name_prefix, 'ELEC_WATCH'       AS target_code
    UNION ALL SELECT 1,                '无线蓝牙耳机',                       'ELEC_AUDIO'
    UNION ALL SELECT 1,                '智能降噪无线耳机',                   'ELEC_AUDIO'
    UNION ALL SELECT 1,                '桌面蓝牙音箱',                       'ELEC_AUDIO'
    UNION ALL SELECT 1,                '三模机械键盘',                       'ELEC_KEYMOUSE'
    UNION ALL SELECT 1,                '电竞轻量鼠标',                       'ELEC_KEYMOUSE'
    UNION ALL SELECT 1,                'USB-C扩展坞',                        'ELEC_DOCK'
    UNION ALL SELECT 1,                '铝合金平板支架',                     'ELEC_DOCK'
    UNION ALL SELECT 1,                '便携显示器',                         'ELEC_SCREEN'
    UNION ALL SELECT 1,                '氮化镓快充套装',                     'ELEC_CHARGER'
    UNION ALL SELECT 1,                '磁吸无线移动电源',                   'ELEC_CHARGER'
    UNION ALL SELECT 1,                '智能摄像头',                         'ELEC_CAMERA'
    UNION ALL SELECT 1,                '智能扫地机配件',                     'ELEC_CLEANER'

    UNION ALL SELECT 2,                '精梳棉短袖T恤',                      'CLOTH_TEE'
    UNION ALL SELECT 2,                '羊毛针织开衫',                       'CLOTH_TEE'
    UNION ALL SELECT 2,                '重磅连帽卫衣',                       'CLOTH_COAT'
    UNION ALL SELECT 2,                '轻暖羽绒服',                         'CLOTH_COAT'
    UNION ALL SELECT 2,                '防风机能风衣',                       'CLOTH_COAT'
    UNION ALL SELECT 2,                '免烫商务衬衫',                       'CLOTH_SHIRT'
    UNION ALL SELECT 2,                '直筒休闲长裤',                       'CLOTH_PANTS'
    UNION ALL SELECT 2,                '城市通勤运动鞋',                     'CLOTH_SHOES'
    UNION ALL SELECT 2,                '轻量通勤双肩包',                     'CLOTH_BACKPACK'
    UNION ALL SELECT 2,                '真皮小方包',                         'CLOTH_HANDBAG'
    UNION ALL SELECT 2,                '防晒棒球帽',                         'CLOTH_HAT'
    UNION ALL SELECT 2,                '头层牛皮自动皮带',                   'CLOTH_BELT'

    UNION ALL SELECT 3,                '亲肤纯棉四件套',                     'HOME_BEDDING'
    UNION ALL SELECT 3,                '速干柔软浴巾',                       'HOME_BATH'
    UNION ALL SELECT 3,                '遮光隔热窗帘',                       'HOME_CURTAIN'
    UNION ALL SELECT 3,                '恒温电热水壶',                       'HOME_KITCHEN'
    UNION ALL SELECT 3,                '轻量便携榨汁杯',                     'HOME_KITCHEN'
    UNION ALL SELECT 3,                '静音香薰加湿器',                     'HOME_AIR'
    UNION ALL SELECT 3,                '桌面空气净化器',                     'HOME_AIR'
    UNION ALL SELECT 3,                '无线扫拖机配件',                     'HOME_CLEAN'
    UNION ALL SELECT 3,                '感应式智能垃圾桶',                   'HOME_CLEAN'
    UNION ALL SELECT 3,                '可叠加收纳箱',                       'HOME_ORGANIZE'
    UNION ALL SELECT 3,                '智能护眼台灯',                       'HOME_LIGHT'
    UNION ALL SELECT 3,                '纯钛真空保温杯',                     'HOME_CUP'
    UNION ALL SELECT 3,                '陶瓷餐具套装',                       'HOME_TABLEWARE'

    UNION ALL SELECT 4,                '玻尿酸修护精华',                     'BEAUTY_ESSENCE'
    UNION ALL SELECT 4,                '高保湿面霜',                         'BEAUTY_ESSENCE'
    UNION ALL SELECT 4,                '紧致淡纹眼霜',                       'BEAUTY_ESSENCE'
    UNION ALL SELECT 4,                '氨基酸洁面乳',                       'BEAUTY_CLEANSE'
    UNION ALL SELECT 4,                '温和卸妆油',                         'BEAUTY_CLEANSE'
    UNION ALL SELECT 4,                '舒缓补水面膜',                       'BEAUTY_MASK'
    UNION ALL SELECT 4,                '清透防晒乳',                         'BEAUTY_MASK'
    UNION ALL SELECT 4,                '持妆轻薄粉底液',                     'BEAUTY_BASE'
    UNION ALL SELECT 4,                '丝绒哑光口红',                       'BEAUTY_LIP'
    UNION ALL SELECT 4,                '烟酰胺身体乳',                       'BEAUTY_BODYCARE'
    UNION ALL SELECT 4,                '滋润护手霜',                         'BEAUTY_BODYCARE'
    UNION ALL SELECT 4,                '氨基酸洗发水',                       'BEAUTY_HAIR'

    UNION ALL SELECT 5,                '每日坚果混合装',                     'FOOD_NUTS'
    UNION ALL SELECT 5,                '坚果礼盒',                           'FOOD_NUTS'
    UNION ALL SELECT 5,                '低温烘焙果干',                       'FOOD_DRIED'
    UNION ALL SELECT 5,                '风干牛肉干',                         'FOOD_MEAT'
    UNION ALL SELECT 5,                '低盐脆烤海苔',                       'FOOD_MEAT'
    UNION ALL SELECT 5,                '精品手冲咖啡豆',                     'FOOD_COFFEE'
    UNION ALL SELECT 5,                '高山云雾茶',                         'FOOD_TEA'
    UNION ALL SELECT 5,                '天然成熟蜂蜜',                       'FOOD_HONEY'
    UNION ALL SELECT 5,                '进口水果燕麦片',                     'FOOD_CEREAL'
    UNION ALL SELECT 5,                '高蛋白低脂麦片',                     'FOOD_CEREAL'
    UNION ALL SELECT 5,                '原味希腊酸奶',                       'FOOD_DAIRY'
    UNION ALL SELECT 5,                '有机香辛调味料',                     'FOOD_SEASONING'

    UNION ALL SELECT 6,                '旗舰降噪头戴式耳机',                 'AUDIO_OVER_EAR'
    UNION ALL SELECT 6,                '电竞游戏耳麦',                       'AUDIO_OVER_EAR'
    UNION ALL SELECT 6,                '真无线蓝牙耳机',                     'AUDIO_TWS'
    UNION ALL SELECT 6,                '运动骨传导耳机',                     'AUDIO_SPORT'
    UNION ALL SELECT 6,                '桌面监听音箱',                       'AUDIO_DESKTOP'
    UNION ALL SELECT 6,                '便携蓝牙音箱',                       'AUDIO_PORTABLE'
    UNION ALL SELECT 6,                '家庭影院回音壁',                     'AUDIO_CINEMA'
    UNION ALL SELECT 6,                '便携HiFi播放器',                     'AUDIO_HIFI'
    UNION ALL SELECT 6,                '复古黑胶唱片机',                     'AUDIO_HIFI'
    UNION ALL SELECT 6,                '无线麦克风套装',                     'AUDIO_MIC'

    UNION ALL SELECT 7,                '精品手冲咖啡豆',                     'LIFE_BLEND'
    UNION ALL SELECT 7,                '云南小粒咖啡',                       'LIFE_SINGLE'
    UNION ALL SELECT 7,                '阿拉比卡深烘豆',                     'LIFE_SINGLE'
    UNION ALL SELECT 7,                '低因咖啡豆',                         'LIFE_SINGLE'
    UNION ALL SELECT 7,                '冷萃咖啡液',                         'LIFE_COLD'
    UNION ALL SELECT 7,                '燕麦拿铁咖啡粉',                     'LIFE_LATTE'
    UNION ALL SELECT 7,                '摩卡风味咖啡',                       'LIFE_LATTE'
    UNION ALL SELECT 7,                '挂耳咖啡礼盒',                       'LIFE_DRIP'
    UNION ALL SELECT 7,                '咖啡滤纸套装',                       'LIFE_GEAR'
    UNION ALL SELECT 7,                '手工曲奇咖啡伴手礼',                 'LIFE_GIFT'

    UNION ALL SELECT 8,                '轻量徒步冲锋衣',                     'OUTDOOR_JACKET'
    UNION ALL SELECT 8,                '高弹速干运动裤',                     'OUTDOOR_PANTS'
    UNION ALL SELECT 8,                '户外天幕帐篷',                       'OUTDOOR_TENT'
    UNION ALL SELECT 8,                '户外露营折叠椅',                     'OUTDOOR_FURNITURE'
    UNION ALL SELECT 8,                '便携保温野餐箱',                     'OUTDOOR_PICNIC'
    UNION ALL SELECT 8,                '便携露营咖啡壶',                     'OUTDOOR_PICNIC'
    UNION ALL SELECT 8,                '防滑登山徒步鞋',                     'OUTDOOR_SHOES'
    UNION ALL SELECT 8,                '城市通勤双肩包',                     'OUTDOOR_BAG'
    UNION ALL SELECT 8,                '多功能运动腰包',                     'OUTDOOR_BAG'
    UNION ALL SELECT 8,                '超轻防晒遮阳帽',                     'OUTDOOR_HAT'
) m ON p.category_id = m.source_id AND p.product_name LIKE CONCAT(m.name_prefix, '%')
JOIN catalog_category c ON c.category_code = m.target_code AND c.is_deleted = 0
SET p.category_id = c.id,
    p.category_name = c.category_name
WHERE p.is_deleted = 0;
