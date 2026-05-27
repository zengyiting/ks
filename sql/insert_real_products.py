import random
import pymysql
from datetime import datetime, timedelta

categories = ['数码产品', '服饰鞋包', '家居生活', '美妆护肤', '食品饮料', '母婴用品', '运动户外', '图书文具']

product_data = [
    ('小米14手机', '数码产品', 3999, '小米14，骁龙8Gen3处理器，徕卡光学镜头，5000万像素主摄，120W快充', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=400'),
    ('华为Mate60 Pro', '数码产品', 6999, '华为Mate60 Pro，麒麟9000S芯片，卫星通话，XMAGE影像系统', 'https://images.unsplash.com/photo-1512941937151-b5e916484d27?w=400'),
    ('iPhone 15 Pro Max', '数码产品', 9999, '苹果iPhone 15 Pro Max，A17 Pro芯片，钛金属设计，专业级相机系统', 'https://images.unsplash.com/photo-1695047913585-5c5c5c5c5c5c?w=400'),
    ('联想拯救者游戏本', '数码产品', 8999, '联想拯救者R9000P，R9处理器，RTX4060显卡，165Hz高刷屏', 'https://images.unsplash.com/photo-1525542726600-9c3f1e4e4e4e?w=400'),
    ('戴尔XPS 15笔记本', '数码产品', 12999, '戴尔XPS 15，i7处理器，4K OLED触控屏，轻薄商务本', 'https://images.unsplash.com/photo-1593642632559-0c6d3fc62aa0?w=400'),
    ('索尼WH-1000XM5耳机', '数码产品', 2699, '索尼旗舰降噪耳机，30小时续航，Hi-Res音质认证', 'https://images.unsplash.com/photo-1505747320843-6d6d6d6d6d6d?w=400'),
    ('AirPods Pro 2', '数码产品', 1899, '苹果AirPods Pro 2代，主动降噪，空间音频，自适应通透模式', 'https://images.unsplash.com/photo-1606220588951-50d3d3d3d3d3?w=400'),
    ('小米手环8 Pro', '数码产品', 399, '小米手环8 Pro，大屏显示，GPS定位，NFC门禁，150+运动模式', 'https://images.unsplash.com/photo-1575311373932-4c4c4c4c4c4c?w=400'),
    ('Apple Watch S9', '数码产品', 3199, 'Apple Watch Series 9，S9 SiP芯片，血氧检测，心电图功能', 'https://images.unsplash.com/photo-1546893830-8f8f8f8f8f8f?w=400'),
    ('三星Galaxy Tab S9+', '数码产品', 6999, '三星平板电脑，骁龙8 Gen2，12.4英寸AMOLED屏，S Pen手写笔', 'https://images.unsplash.com/photo-1544247025-8c8c8c8c8c8c?w=400'),
    
    ('波司登羽绒服女款', '服饰鞋包', 699, '波司登轻薄羽绒服，白鸭绒填充，防风保暖，时尚连帽设计', 'https://images.unsplash.com/photo-1549591892-5c5c5c5c5c5c?w=400'),
    ('耐克AJ1篮球鞋', '服饰鞋包', 1499, 'Air Jordan 1 Retro High，经典芝加哥配色，皮革鞋面', 'https://images.unsplash.com/photo-1542291026-7c7c7c7c7c7c?w=400'),
    ('阿迪达斯三叶草卫衣', '服饰鞋包', 599, 'Adidas Originals，经典三叶草Logo，纯棉面料，宽松版型', 'https://images.unsplash.com/photo-1556826858-5c5c5c5c5c5c?w=400'),
    ('优衣库HEATTECH打底衫', '服饰鞋包', 149, '优衣库HEATTECH技术，自发热保暖，轻薄贴身', 'https://images.unsplash.com/photo-1562157878-6c7c7c7c7c7c?w=400'),
    ('北面1996羽绒服', '服饰鞋包', 2899, 'The North Face 1996 Retro，700蓬鹅绒，防风防水外壳', 'https://images.unsplash.com/photo-1544978165-5c5c5c5c5c5c?w=400'),
    ('李宁驭帅18篮球鞋', '服饰鞋包', 1199, '李宁驭帅18，碳板科技，䨻轻弹科技，专业实战篮球鞋', 'https://images.unsplash.com/photo-1549056360-6c7c7c7c7c7c?w=400'),
    ('新秀丽拉杆箱', '服饰鞋包', 799, 'Samsonite新秀丽，20寸登机箱，PC材质，万向轮设计', 'https://images.unsplash.com/photo-1553062407-5c5c5c5c5c5c?w=400'),
    ('蔻驰COACH单肩包', '服饰鞋包', 1899, 'COACH蔻驰，经典老花图案，真皮材质，手提斜挎两用', 'https://images.unsplash.com/photo-1548036328-5c5c5c5c5c5c?w=400'),
    
    ('顾家布艺沙发', '家居生活', 5999, '顾家家居现代简约沙发，三人位+贵妃椅，羽绒填充坐垫', 'https://images.unsplash.com/photo-1555041468-5c5c5c5c5c5c?w=400'),
    ('宜家MALM斗柜', '家居生活', 999, 'IKEA马尔姆六斗柜，白色饰面，简约现代风格', 'https://images.unsplash.com/photo-1595428774223-5c5c5c5c5c5c?w=400'),
    ('美的变频空调', '家居生活', 2499, '美的1.5匹变频空调，新一级能效，冷暖两用，智能控制', 'https://images.unsplash.com/photo-1585771728703-5c5c5c5c5c5c?w=400'),
    ('海尔风冷冰箱', '家居生活', 3999, '海尔468升冰箱，风冷无霜，变频压缩机，智能互联', 'https://images.unsplash.com/photo-1589518204723-5c5c5c5c5c5c?w=400'),
    ('老板烟灶套餐', '家居生活', 4299, '老板侧吸油烟机+燃气灶套装，大吸力，智能清洗', 'https://images.unsplash.com/photo-1585771728703-5c5c5c5c5c5c?w=400'),
    ('九阳破壁机', '家居生活', 699, '九阳多功能破壁机，冷热双打，预约定时，易清洗', 'https://images.unsplash.com/photo-1585771728703-5c5c5c5c5c5c?w=400'),
    ('苏泊尔不粘锅', '家居生活', 199, '苏泊尔火红点不粘锅，30cm口径，电磁炉通用', 'https://images.unsplash.com/photo-1585771728703-5c5c5c5c5c5c?w=400'),
    ('小米空气净化器', '家居生活', 1299, '米家空气净化器4 Pro，500m³/h CADR，HEPA滤网', 'https://images.unsplash.com/photo-1585771728703-5c5c5c5c5c5c?w=400'),
    
    ('兰蔻小黑瓶精华', '美妆护肤', 899, '兰蔻小黑瓶精华肌底液，修护肌肤屏障，保湿抗初老', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('雅诗兰黛小棕瓶', '美妆护肤', 899, '雅诗兰黛特润修护精华露，夜间修护，淡纹紧致', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('SK-II神仙水', '美妆护肤', 1699, 'SK-II护肤精华露，Pitera精华，嫩肤焕采', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('迪奥999口红', '美妆护肤', 380, 'Dior迪奥烈艳蓝金唇膏999，经典正红色，丝绒质地', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('香奈儿5号香水', '美妆护肤', 1199, 'Chanel香奈儿五号香水，经典花香调，优雅迷人', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('完美日记眼影盘', '美妆护肤', 119, '完美日记动物眼影盘，12色珠光哑光，新手友好', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('花西子散粉', '美妆护肤', 169, '花西子空气蜜粉，定妆控油，隐形毛孔，裸妆自然', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('百雀羚护肤套装', '美妆护肤', 299, '百雀羚水嫩倍现护肤套装，水乳霜精华，补水保湿', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    
    ('三只松鼠坚果礼盒', '食品饮料', 128, '三只松鼠坚果大礼包，1543g，碧根果夏威夷果开心果', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('茅台飞天53度', '食品饮料', 2999, '茅台飞天酱香型白酒，53度500ml，国酒经典', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('农夫山泉矿泉水', '食品饮料', 35, '农夫山泉饮用天然水，550ml*24瓶，弱碱性水质', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('伊利金典牛奶', '食品饮料', 68, '伊利金典有机纯牛奶，250ml*16盒，优质蛋白', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('蒙牛特仑苏', '食品饮料', 88, '蒙牛特仑苏有机纯牛奶，300ml*12盒，限定牧场奶源', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('星巴克拿铁咖啡', '食品饮料', 69, '星巴克拿铁咖啡饮品，270ml*6瓶，低糖配方', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('元气森林气泡水', '食品饮料', 59, '元气森林无糖气泡水，480ml*12瓶，0糖0脂0卡', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    ('良品铺子猪肉脯', '食品饮料', 35, '良品铺子靖江猪肉脯，200g，独立小包装', 'https://images.unsplash.com/photo-1599491898750-5c5c5c5c5c5c?w=400'),
    
    ('贝亲玻璃奶瓶', '母婴用品', 149, '贝亲宽口径玻璃奶瓶，240ml，仿母乳设计，易清洗', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('好奇铂金纸尿裤', '母婴用品', 199, '好奇铂金装纸尿裤，M号64片，柔软透气，防红臀', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('飞鹤星飞帆奶粉', '母婴用品', 358, '飞鹤星飞帆婴幼儿配方奶粉，3段700g，OPO结构脂', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('好孩子婴儿推车', '母婴用品', 899, '好孩子轻便婴儿推车，可躺可坐，一键折叠', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('美德乐吸奶器', '母婴用品', 1399, '美德乐丝韵翼双边电动吸奶器，静音高效', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('爱他美奶粉', '母婴用品', 389, '爱他美卓萃婴幼儿配方奶粉，3段900g，德国进口', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('Babycare湿巾', '母婴用品', 99, 'Babycare婴儿湿巾，80抽*6包，EDI纯水无酒精', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    ('小熊辅食机', '母婴用品', 199, '小熊婴儿辅食机，蒸搅一体，多功能料理', 'https://images.unsplash.com/photo-1596462502278-4c4c4c4c4c4c?w=400'),
    
    ('耐克飞马跑鞋', '运动户外', 899, 'Nike Air Zoom Pegasus 40，缓震透气，日常跑步', 'https://images.unsplash.com/photo-1542291026-7c7c7c7c7c7c?w=400'),
    ('阿迪达斯UB跑鞋', '运动户外', 1299, 'Adidas Ultra Boost，Boost中底，能量回弹', 'https://images.unsplash.com/photo-1542291026-7c7c7c7c7c7c?w=400'),
    ('李宁超轻跑鞋', '运动户外', 599, '李宁超轻20，䨻轻弹科技，马拉松专业跑鞋', 'https://images.unsplash.com/photo-1542291026-7c7c7c7c7c7c?w=400'),
    ('迪卡侬登山包', '运动户外', 149, '迪卡侬户外双肩背包，30L大容量，防泼水', 'https://images.unsplash.com/photo-1553062407-5c5c5c5c5c5c?w=400'),
    ('哥伦比亚冲锋衣', '运动户外', 1299, 'Columbia户外冲锋衣，防水透气，三合一设计', 'https://images.unsplash.com/photo-1544978165-5c5c5c5c5c5c?w=400'),
    ('始祖鸟速干T恤', '运动户外', 599, 'Arc\'teryx速干T恤，轻薄透气，户外运动必备', 'https://images.unsplash.com/photo-1562157878-6c7c7c7c7c7c?w=400'),
    ('骆驼露营帐篷', '运动户外', 399, '骆驼全自动露营帐篷，一键弹开，防雨防晒', 'https://images.unsplash.com/photo-1553062407-5c5c5c5c5c5c?w=400'),
    ('斯伯丁篮球', '运动户外', 299, 'Spalding斯伯丁篮球，室内外通用，FIBA认证', 'https://images.unsplash.com/photo-1542291026-7c7c7c7c7c7c?w=400'),
    
    ('余华活着', '图书文具', 28, '余华代表作《活着》，经典文学，感人至深', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('刘慈欣三体', '图书文具', 78, '刘慈欣《三体》三部曲，科幻巨著，雨果奖作品', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('人类简史', '图书文具', 45, '尤瓦尔·赫拉利《人类简史》，了解人类发展史', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('明朝那些事儿', '图书文具', 249, '当年明月《明朝那些事儿》全套9册，历史通俗读物', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('小王子', '图书文具', 22, '圣埃克苏佩里《小王子》，经典童话，温暖治愈', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('新华字典', '图书文具', 26, '新华字典第12版双色本，学生必备工具书', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('Kindle电子书', '图书文具', 1399, 'Kindle Paperwhite5，6.8英寸300ppi，16GB存储', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
    ('得力文具盒', '图书文具', 39, '得力多功能文具盒，双层设计，学生文具收纳', 'https://images.unsplash.com/photo-1544947950-fc5c5c5c5c5c?w=400'),
]

conn = pymysql.connect(
    host='localhost',
    user='root',
    password='2004zyta',
    database='recommend',
    charset='utf8mb4'
)
cursor = conn.cursor()

insert_sql = """INSERT INTO items (name, category, price, description, image_url, created_at)
                VALUES (%s, %s, %s, %s, %s, %s)"""

now = datetime.now()
products = []

for name, category, price, desc, image_url in product_data:
    created_at = now - timedelta(days=random.randint(0, 30), hours=random.randint(0, 23))
    products.append((name, category, price, desc, image_url, created_at))

for i in range(200):
    base_product = random.choice(product_data)
    name = base_product[0] + random.choice([' Pro', ' Plus', ' Max', ' 升级版', ' 豪华版', ' 限定版', ''])
    category = base_product[1]
    price = round(base_product[2] * random.uniform(0.8, 1.5), 2)
    desc = base_product[3] + '，品质优良，性价比高。'
    image_url = base_product[4]
    created_at = now - timedelta(days=random.randint(0, 30), hours=random.randint(0, 23))
    products.append((name, category, price, desc, image_url, created_at))

random.shuffle(products)

count = 0
for product in products:
    try:
        cursor.execute(insert_sql, product)
        count += 1
    except Exception as e:
        print(f"插入失败: {product[0]}, 错误: {e}")

conn.commit()
print(f"成功插入 {count} 条商品数据")

cursor.execute("SELECT COUNT(*) FROM items")
total = cursor.fetchone()[0]
print(f"商品表当前共 {total} 条数据")

cursor.close()
conn.close()