package com.example.recommend.algo;

/**
 * 推荐结果类，用于封装单个商品的推荐信息。
 * <p>
 * 该类实现了 Comparable 接口，支持按照推荐得分进行降序排序，
 * 便于在推荐系统中对推荐结果进行优先级排列。
 */
public class Recommendation implements Comparable<Recommendation> {
    /**
     * 商品ID，唯一标识被推荐的商品。
     */
    private final Long itemId;

    /**
     * 推荐得分，表示该商品与用户的匹配程度或推荐强度。
     * 得分越高，表示推荐优先级越高。
     */
    private final double score;

    /**
     * 构造推荐结果对象。
     *
     * @param itemId 商品ID，唯一标识被推荐的商品
     * @param score  推荐得分，表示该商品与用户的匹配程度，值越大表示推荐优先级越高
     */
    public Recommendation(Long itemId, double score) {
        this.itemId = itemId;
        this.score = score;
    }

    /**
     * 获取商品ID。
     *
     * @return 商品ID
     */
    public Long getItemId() {
        return itemId;
    }

    /**
     * 获取推荐得分。
     *
     * @return 推荐得分，值越大表示推荐优先级越高
     */
    public double getScore() {
        return score;
    }

    /**
     * 比较两个推荐结果的优先级。
     * <p>
     * 按照推荐得分进行降序排序，得分高的排在前面。
     * 该方法使得 Recommendation 对象可以直接用于排序操作，
     * 例如在 PriorityQueue 或 sorted stream 中使用。
     *
     * @param o 待比较的另一个 Recommendation 对象
     * @return 如果当前对象得分高于参数对象返回负数，低于则返回正数，相等返回0
     */
    @Override
    public int compareTo(Recommendation o) {
        return Double.compare(o.score, this.score);
    }
}
