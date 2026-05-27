import csv
import random

# 商品分类
categories = [
    "数码产品", "服饰鞋包", "家居生活", "美妆护肤",
    "食品饮料", "母婴用品", "运动户外", "图书文具"
]

# 分类对应的商品名称模板
category_templates = {
    "数码产品": [
        "苹果iPhone 15 Pro Max 256GB 深空黑",
        "华为Mate 60 Pro 5G手机",
        "小米14 Ultra 骁龙8 Gen3",
        "iPad Pro 12.9英寸 M4芯片",
        "MacBook Pro 14英寸",
        "索尼WH-1000XM5降噪耳机",
        "AirPods Pro 2代",
        "尼康Z7 II全画幅相机",
        "任天堂Switch OLED版",
        "索尼PS5游戏机光驱版"
    ],
    "服饰鞋包": [
        "耐克Air Jordan 1芝加哥限定",
        "优衣库UNIQLO羽绒服",
        "安踏KT7篮球鞋",
        "阿迪达斯NMD R1跑鞋",
        "Lululemon瑜伽垫",
        "Coach蔻驰单肩斜挎包",
        "New Balance 574运动鞋",
        "韩都衣舍连衣裙",
        "恒源祥男士商务衬衫",
        "百丽女靴秋冬款"
    ],
    "家居生活": [
        "小米扫地机器人扫拖一体",
        "戴森V15吸尘器",
        "宜家POÄNG波昂扶手椅",
        "飞利浦电动牙刷HX9352",
        "美的电饭煲4L智能",
        "九阳豆浆机家用",
        "科沃斯擦窗机器人",
        "苏泊尔电压力锅",
        "慕思乳胶床垫",
        "水星家纺四件套"
    ],
    "美妆护肤": [
        "SK-II神仙水精华液230ml",
        "雅诗兰黛小棕瓶眼霜",
        "兰蔻小黑瓶精华肌底液",
        "香奈儿N°5淡香水",
        "海蓝之谜精华面霜",
        "资生堂红腰子精华",
        "MAC子弹头口红",
        "植村秀琥珀卸妆油",
        "科颜氏高保湿面霜",
        "OLAY小白瓶精华液"
    ],
    "食品饮料": [
        "三只松鼠坚果礼盒",
        "星巴克咖啡豆中度烘焙",
        "农夫山泉矿泉水24瓶",
        "三只松鼠每日坚果",
        "百草味牛肉干",
        "良品铺子猪肉脯",
        "可口可乐330ml*24罐",
        "德芙巧克力礼盒装",
        "奥利奥饼干巧克力味",
        "旺旺大礼包"
    ],
    "母婴用品": [
        "帮宝适绿帮纸尿裤XL码",
        "好奇铂金装纸尿裤",
        "美赞臣蓝臻奶粉3段",
        "费雪学步车玩具",
        "乐亲婴儿摇椅",
        "可优比婴儿床围栏",
        "全棉时代婴儿棉柔巾",
        "贝亲宽口径奶瓶",
        "美赞臣蓝臻奶粉2段",
        "小龙哈彼婴儿推车"
    ],
    "运动户外": [
        "迪卡侬瑜伽垫防滑健身垫",
        "Keep健身器械家用",
        "迪卡侬自行车山地车",
        "李宁羽毛球拍单拍",
        "斯伯丁篮球NBA系列",
        "骆驼登山鞋户外鞋",
        "速比涛游泳装备全套",
        "迪卡侬滑板成人",
        "牧高笛帐篷户外露营",
        "探路者冲锋衣男款"
    ],
    "图书文具": [
        "活着 余华正版书籍",
        "三体全集刘慈欣科幻小说",
        "明朝那些事儿当年明月",
        "华为MatePad Pro平板电脑",
        "晨光文具中性笔0.5mm",
        "得力文件资料册A4",
        "kindle Paperwhite电子书",
        "得力计算器财务用",
        "得力打印纸A4复印纸",
        "晨光铅笔HB小学生"
    ]
}

# 商品描述模板
desc_templates = [
    "品质卓越，性能出色，深受用户喜爱，是您的理想选择！",
    "新品上市，限时优惠，正品保障，假一赔十，快来抢购！",
    "热销爆款，好评如潮，品质保证，值得信赖！",
    "正品行货，原厂直销，质量上乘，性价比高！",
    "精选优质材料，精细工艺，匠心打造，品质生活之选！"
]

# 图片占位URL
image_urls = [
    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e",
    "https://images.unsplash.com/photo-1485968579168-82ca61346455",
    "https://images.unsplash.com/photo-1441986300917-64674bd600d8",
    "https://images.unsplash.com/photo-1484704849700-f032a568e944",
    "https://images.unsplash.com/photo-1523275335684-37898b6baf30"
]

def generate_price(category):
    if category in ["数码产品"]:
        return random.randint(1000, 10000)
    elif category in ["美妆护肤", "运动户外"]:
        return random.randint(100, 2000)
    elif category in ["服饰鞋包", "家居生活"]:
        return random.randint(50, 3000)
    elif category in ["食品饮料", "母婴用品"]:
        return random.randint(10, 1000)
    else:
        return random.randint(10, 5000)

# 生成1000条商品数据
products = []
for _ in range(1000):
    category = random.choice(categories)
    name = random.choice(category_templates[category])
    price = generate_price(category)
    desc = random.choice(desc_templates)
    image = random.choice(image_urls)
    
    products.append({
        "name": name,
        "category": category,
        "price": price,
        "image_url": image,
        "description": desc
    })

# 保存为CSV
with open("d:/app/ks/recommendtwo/sql/chinese_products.csv", "w", encoding="utf-8-sig", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["name", "category", "price", "image_url", "description"])
    writer.writeheader()
    for p in products:
        writer.writerow(p)

print(f"成功生成 {len(products)} 条中文商品数据！")
