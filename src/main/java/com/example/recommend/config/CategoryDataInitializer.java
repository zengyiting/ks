package com.example.recommend.config;

import com.example.recommend.model.Category;
import com.example.recommend.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类数据初始化器
 * 初始化淘宝/拼多多风格的商品分类数据
 */
@Configuration
public class CategoryDataInitializer {

    /**
     * 淘宝/拼多多风格的商品分类数据
     */
    private static final CategoryInfo[] TOP_LEVEL_CATEGORIES = {
        new CategoryInfo("女装", 1, "女士服饰相关"),
        new CategoryInfo("男装", 2, "男士服饰相关"),
        new CategoryInfo("鞋靴", 3, "鞋类靴类商品"),
        new CategoryInfo("箱包", 4, "箱包配件商品"),
        new CategoryInfo("数码", 5, "数码电子产品"),
        new CategoryInfo("家电", 6, "家用电器"),
        new CategoryInfo("美妆", 7, "美妆护肤"),
        new CategoryInfo("食品", 8, "食品零食"),
        new CategoryInfo("家居", 9, "家居日用"),
        new CategoryInfo("母婴", 10, "母婴用品"),
        new CategoryInfo("运动", 11, "运动户外"),
        new CategoryInfo("图书", 12, "图书文具"),
        new CategoryInfo("汽车", 13, "汽车用品"),
        new CategoryInfo("珠宝", 14, "珠宝首饰"),
        new CategoryInfo("宠物", 15, "宠物用品")
    };

    private static final SubCategoryInfo[] SUB_CATEGORIES = {
        // 女装子分类
        new SubCategoryInfo("女装", "连衣裙", 1, "各种款式连衣裙"),
        new SubCategoryInfo("女装", "T恤", 2, "女士T恤"),
        new SubCategoryInfo("女装", "衬衫", 3, "女士衬衫"),
        new SubCategoryInfo("女装", "外套", 4, "女士外套"),
        new SubCategoryInfo("女装", "裤子", 5, "女士裤子"),
        new SubCategoryInfo("女装", "裙子", 6, "半身裙"),
        new SubCategoryInfo("女装", "针织衫", 7, "针织毛衣"),
        new SubCategoryInfo("女装", "卫衣", 8, "女士卫衣"),
        new SubCategoryInfo("女装", "风衣", 9, "女士风衣"),
        new SubCategoryInfo("女装", "羽绒服", 10, "女士羽绒服"),
        new SubCategoryInfo("女装", "牛仔裤", 11, "女士牛仔裤"),
        new SubCategoryInfo("女装", "套装", 12, "女士套装"),
        
        // 男装子分类
        new SubCategoryInfo("男装", "T恤", 1, "男士T恤"),
        new SubCategoryInfo("男装", "衬衫", 2, "男士衬衫"),
        new SubCategoryInfo("男装", "外套", 3, "男士外套"),
        new SubCategoryInfo("男装", "裤子", 4, "男士裤子"),
        new SubCategoryInfo("男装", "卫衣", 5, "男士卫衣"),
        new SubCategoryInfo("男装", "毛衣", 6, "男士毛衣"),
        new SubCategoryInfo("男装", "风衣", 7, "男士风衣"),
        new SubCategoryInfo("男装", "羽绒服", 8, "男士羽绒服"),
        new SubCategoryInfo("男装", "牛仔裤", 9, "男士牛仔裤"),
        new SubCategoryInfo("男装", "西装", 10, "男士西装"),
        
        // 鞋靴子分类
        new SubCategoryInfo("鞋靴", "运动鞋", 1, "运动休闲鞋"),
        new SubCategoryInfo("鞋靴", "皮鞋", 2, "真皮皮鞋"),
        new SubCategoryInfo("鞋靴", "凉鞋", 3, "夏季凉鞋"),
        new SubCategoryInfo("鞋靴", "靴子", 4, "冬季靴子"),
        new SubCategoryInfo("鞋靴", "拖鞋", 5, "居家拖鞋"),
        new SubCategoryInfo("鞋靴", "高跟鞋", 6, "女士高跟鞋"),
        new SubCategoryInfo("鞋靴", "板鞋", 7, "休闲板鞋"),
        new SubCategoryInfo("鞋靴", "帆布鞋", 8, "帆布休闲鞋"),
        
        // 箱包子分类
        new SubCategoryInfo("箱包", "手提包", 1, "女士手提包"),
        new SubCategoryInfo("箱包", "双肩包", 2, "双肩背包"),
        new SubCategoryInfo("箱包", "钱包", 3, "男女钱包"),
        new SubCategoryInfo("箱包", "旅行箱", 4, "拉杆旅行箱"),
        new SubCategoryInfo("箱包", "书包", 5, "学生书包"),
        new SubCategoryInfo("箱包", "公文包", 6, "商务公文包"),
        
        // 数码子分类
        new SubCategoryInfo("数码", "手机", 1, "智能手机"),
        new SubCategoryInfo("数码", "电脑", 2, "笔记本电脑"),
        new SubCategoryInfo("数码", "平板", 3, "平板电脑"),
        new SubCategoryInfo("数码", "耳机", 4, "蓝牙耳机"),
        new SubCategoryInfo("数码", "手表", 5, "智能手表"),
        new SubCategoryInfo("数码", "相机", 6, "数码相机"),
        new SubCategoryInfo("数码", "充电宝", 7, "移动电源"),
        new SubCategoryInfo("数码", "U盘", 8, "USB闪存盘"),
        new SubCategoryInfo("数码", "路由器", 9, "网络路由器"),
        new SubCategoryInfo("数码", "音箱", 10, "蓝牙音箱"),
        
        // 家电子分类
        new SubCategoryInfo("家电", "冰箱", 1, "家用冰箱"),
        new SubCategoryInfo("家电", "洗衣机", 2, "洗衣机"),
        new SubCategoryInfo("家电", "空调", 3, "空调"),
        new SubCategoryInfo("家电", "电视", 4, "智能电视"),
        new SubCategoryInfo("家电", "热水器", 5, "电热水器"),
        new SubCategoryInfo("家电", "微波炉", 6, "微波炉"),
        new SubCategoryInfo("家电", "电饭煲", 7, "智能电饭煲"),
        new SubCategoryInfo("家电", "豆浆机", 8, "豆浆机"),
        new SubCategoryInfo("家电", "吸尘器", 9, "家用吸尘器"),
        new SubCategoryInfo("家电", "空气净化器", 10, "空气净化器"),
        
        // 美妆子分类
        new SubCategoryInfo("美妆", "口红", 1, "口红唇膏"),
        new SubCategoryInfo("美妆", "面膜", 2, "护肤面膜"),
        new SubCategoryInfo("美妆", "粉底", 3, "粉底液"),
        new SubCategoryInfo("美妆", "眼影", 4, "眼影盘"),
        new SubCategoryInfo("美妆", "腮红", 5, "腮红"),
        new SubCategoryInfo("美妆", "卸妆", 6, "卸妆产品"),
        new SubCategoryInfo("美妆", "香水", 7, "香水"),
        new SubCategoryInfo("美妆", "护肤", 8, "护肤品"),
        new SubCategoryInfo("美妆", "防晒", 9, "防晒产品"),
        new SubCategoryInfo("美妆", "眉笔", 10, "眉笔"),
        
        // 食品子分类
        new SubCategoryInfo("食品", "零食", 1, "休闲零食"),
        new SubCategoryInfo("食品", "饮料", 2, "饮料饮品"),
        new SubCategoryInfo("食品", "生鲜", 3, "生鲜食品"),
        new SubCategoryInfo("食品", "粮油", 4, "粮油米面"),
        new SubCategoryInfo("食品", "酒水", 5, "酒类饮品"),
        new SubCategoryInfo("食品", "糖果", 6, "糖果巧克力"),
        new SubCategoryInfo("食品", "坚果", 7, "坚果炒货"),
        new SubCategoryInfo("食品", "饼干", 8, "饼干糕点"),
        
        // 家居子分类
        new SubCategoryInfo("家居", "家纺", 1, "家纺用品"),
        new SubCategoryInfo("家居", "家具", 2, "家用家具"),
        new SubCategoryInfo("家居", "收纳", 3, "收纳用品"),
        new SubCategoryInfo("家居", "清洁", 4, "清洁用品"),
        new SubCategoryInfo("家居", "厨房", 5, "厨房用品"),
        new SubCategoryInfo("家居", "卫浴", 6, "卫浴用品"),
        new SubCategoryInfo("家居", "装饰", 7, "家居装饰"),
        new SubCategoryInfo("家居", "灯具", 8, "照明灯具"),
        
        // 母婴子分类
        new SubCategoryInfo("母婴", "奶粉", 1, "婴儿奶粉"),
        new SubCategoryInfo("母婴", "纸尿裤", 2, "婴儿纸尿裤"),
        new SubCategoryInfo("母婴", "童装", 3, "儿童服装"),
        new SubCategoryInfo("母婴", "玩具", 4, "儿童玩具"),
        new SubCategoryInfo("母婴", "用品", 5, "母婴用品"),
        new SubCategoryInfo("母婴", "推车", 6, "婴儿推车"),
        new SubCategoryInfo("母婴", "安全座椅", 7, "儿童安全座椅"),
        
        // 运动子分类
        new SubCategoryInfo("运动", "服装", 1, "运动服装"),
        new SubCategoryInfo("运动", "鞋", 2, "运动鞋"),
        new SubCategoryInfo("运动", "户外", 3, "户外装备"),
        new SubCategoryInfo("运动", "健身", 4, "健身器材"),
        new SubCategoryInfo("运动", "球类", 5, "球类用品"),
        new SubCategoryInfo("运动", "骑行", 6, "骑行装备"),
        
        // 图书子分类
        new SubCategoryInfo("图书", "小说", 1, "文学小说"),
        new SubCategoryInfo("图书", "教材", 2, "教辅教材"),
        new SubCategoryInfo("图书", "绘本", 3, "儿童绘本"),
        new SubCategoryInfo("图书", "经管", 4, "经管励志"),
        new SubCategoryInfo("图书", "科技", 5, "科技科普"),
        new SubCategoryInfo("图书", "杂志", 6, "杂志期刊"),
        new SubCategoryInfo("图书", "文具", 7, "文具用品"),
        
        // 汽车子分类
        new SubCategoryInfo("汽车", "配件", 1, "汽车配件"),
        new SubCategoryInfo("汽车", "用品", 2, "汽车用品"),
        new SubCategoryInfo("汽车", "装饰", 3, "汽车装饰"),
        new SubCategoryInfo("汽车", "养护", 4, "汽车养护"),
        new SubCategoryInfo("汽车", "电子", 5, "汽车电子"),
        
        // 珠宝子分类
        new SubCategoryInfo("珠宝", "项链", 1, "项链吊坠"),
        new SubCategoryInfo("珠宝", "手链", 2, "手链手镯"),
        new SubCategoryInfo("珠宝", "耳环", 3, "耳饰"),
        new SubCategoryInfo("珠宝", "戒指", 4, "戒指"),
        new SubCategoryInfo("珠宝", "手表", 5, "珠宝手表"),
        
        // 宠物子分类
        new SubCategoryInfo("宠物", "食品", 1, "宠物食品"),
        new SubCategoryInfo("宠物", "用品", 2, "宠物用品"),
        new SubCategoryInfo("宠物", "玩具", 3, "宠物玩具"),
        new SubCategoryInfo("宠物", "美容", 4, "宠物美容"),
        new SubCategoryInfo("宠物", "医疗", 5, "宠物医疗")
    };

    @Bean
    public CommandLineRunner initCategories(CategoryRepository categoryRepository) {
        return args -> {
            // 检查是否已存在类别数据
            if (categoryRepository.count() > 0) {
                System.out.println("类别数据已存在，跳过初始化");
                return;
            }

            System.out.println("开始初始化商品类别数据...");

            List<Category> categories = new ArrayList<>();

            // 创建顶级类别
            for (CategoryInfo info : TOP_LEVEL_CATEGORIES) {
                Category cat = new Category();
                cat.setName(info.name);
                cat.setParentId(null);
                cat.setLevel(1);
                cat.setSortOrder(info.sortOrder);
                cat.setEnabled(true);
                cat.setDescription(info.description);
                categories.add(cat);
            }

            List<Category> savedTopLevel = categoryRepository.saveAll(categories);
            categories.clear();

            // 创建子类别
            for (SubCategoryInfo info : SUB_CATEGORIES) {
                // 查找父类别
                Category parent = savedTopLevel.stream()
                        .filter(c -> c.getName().equals(info.parentName))
                        .findFirst()
                        .orElse(null);

                if (parent != null) {
                    Category cat = new Category();
                    cat.setName(info.name);
                    cat.setParentId(parent.getId());
                    cat.setLevel(2);
                    cat.setSortOrder(info.sortOrder);
                    cat.setEnabled(true);
                    cat.setDescription(info.description);
                    categories.add(cat);
                }
            }

            categoryRepository.saveAll(categories);
            System.out.println("商品类别数据初始化完成，共创建 " + (savedTopLevel.size() + categories.size()) + " 个类别");
        };
    }

    private record CategoryInfo(String name, int sortOrder, String description) {}
    private record SubCategoryInfo(String parentName, String name, int sortOrder, String description) {}
}