package com.example.recommend.service;

/**
 * 推荐算法类型枚举
 * 
 * <p>定义系统中支持的推荐算法类型：
 * <ul>
 *   <li>USER_BASED - 基于用户的协同过滤算法</li>
 *   <li>ITEM_BASED - 基于物品的协同过滤算法</li>
 *   <li>BEHAVIOR_BASED - 基于行为的推荐算法</li>
 *   <li>HYBRID - 混合推荐算法</li>
 * </ul>
 */
public enum AlgorithmType {
    /** 基于用户的协同过滤算法，根据相似用户的偏好进行推荐 */
    USER_BASED,
    
    /** 基于物品的协同过滤算法，根据相似物品的关联进行推荐 */
    ITEM_BASED,
    
    /** 基于行为的推荐算法，根据用户历史行为模式进行推荐 */
    BEHAVIOR_BASED,
    
    /** 混合推荐算法，结合多种算法策略进行综合推荐 */
    HYBRID
}
//为什么还有个单独分出来的行为推荐算法