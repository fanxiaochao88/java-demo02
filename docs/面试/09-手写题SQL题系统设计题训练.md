# 09 - 手写题、SQL 题、系统设计题训练

> 复习目标：面试现场遇到编码题、SQL 题、简单系统设计题时，有稳定的解题步骤和表达方式。

## 1. 手写题怎么准备

Java 后端初级岗位常见手写题不会特别偏竞赛，但会看基础编码能力：

1. 字符串处理。
2. 数组和双指针。
3. HashMap 计数。
4. 链表反转。
5. 栈和队列。
6. TopK。
7. LRU 缓存。
8. 简单递归和二分。
9. SQL 查询。

答题原则：

1. 先复述题意，确认输入输出。
2. 先给最简单方案，再优化。
3. 写代码时注意空值、边界、复杂度。
4. 写完主动用例子走一遍。
5. 最后说时间复杂度和空间复杂度。

## 2. 字符串反转

### 题目

给定字符串 `"abcde"`，返回 `"edcba"`。

### 答案

```java
public String reverse(String s) {
    if (s == null || s.length() <= 1) {
        return s;
    }
    char[] chars = s.toCharArray();
    int left = 0;
    int right = chars.length - 1;
    while (left < right) {
        char temp = chars[left];
        chars[left] = chars[right];
        chars[right] = temp;
        left++;
        right--;
    }
    return new String(chars);
}
```

### 讲解

使用双指针，一个从左往右，一个从右往左，交换字符。时间复杂度 O(n)，空间复杂度 O(n)，因为创建了字符数组。

## 3. 判断字符串是否是回文

### 题目

判断 `"level"` 是否是回文。

### 答案

```java
public boolean isPalindrome(String s) {
    if (s == null) {
        return false;
    }
    int left = 0;
    int right = s.length() - 1;
    while (left < right) {
        if (s.charAt(left) != s.charAt(right)) {
            return false;
        }
        left++;
        right--;
    }
    return true;
}
```

### 追问

如果忽略大小写和非字母数字，可以先移动指针跳过无效字符，并比较小写形式。

## 4. 数组两数之和

### 题目

给定数组 `[2,7,11,15]` 和目标值 `9`，返回两个数下标 `[0,1]`。

### 答案

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (map.containsKey(need)) {
            return new int[]{map.get(need), i};
        }
        map.put(nums[i], i);
    }
    return new int[0];
}
```

### 讲解

遍历数组时，用 HashMap 保存已经出现过的数和下标。对当前数 `x`，只要看 `target - x` 是否出现过。时间复杂度 O(n)，空间复杂度 O(n)。

## 5. 统计字符出现次数

### 题目

统计字符串中每个字符出现次数。

### 答案

```java
public Map<Character, Integer> countChars(String s) {
    Map<Character, Integer> result = new HashMap<>();
    if (s == null) {
        return result;
    }
    for (char c : s.toCharArray()) {
        result.put(c, result.getOrDefault(c, 0) + 1);
    }
    return result;
}
```

### 项目联系

这种 HashMap 计数思想也可用于把订单明细按订单 id 分组、统计菜品销量、统计状态数量。

## 6. 链表反转

### 题目

反转单链表。

### 答案

```java
class ListNode {
    int val;
    ListNode next;
    ListNode(int val) {
        this.val = val;
    }
}

public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode cur = head;
    while (cur != null) {
        ListNode next = cur.next;
        cur.next = prev;
        prev = cur;
        cur = next;
    }
    return prev;
}
```

### 讲解

每次把当前节点的 next 指向前一个节点。需要临时保存原来的 next，否则链表会断。

## 7. 用数组实现简单队列

### 答案

```java
public class ArrayQueue {
    private final int[] data;
    private int head = 0;
    private int tail = 0;

    public ArrayQueue(int capacity) {
        this.data = new int[capacity + 1];
    }

    public boolean offer(int value) {
        if ((tail + 1) % data.length == head) {
            return false;
        }
        data[tail] = value;
        tail = (tail + 1) % data.length;
        return true;
    }

    public Integer poll() {
        if (head == tail) {
            return null;
        }
        int value = data[head];
        head = (head + 1) % data.length;
        return value;
    }
}
```

### 讲解

这里使用循环数组。为了区分队空和队满，浪费一个数组位置。队空是 `head == tail`，队满是 `(tail + 1) % length == head`。

## 8. LRU 缓存怎么实现？

### 思路

LRU 要求最近使用的数据放前面，最久未使用的数据被淘汰。Java 中可以用 `LinkedHashMap` 实现。

### 答案

```java
public class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LruCache(int capacity) {
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

### 讲解

`LinkedHashMap` 第三个构造参数 `accessOrder` 设置为 true，表示按访问顺序维护链表。每次 get/put 都会把元素移动到链表尾部，最老元素在头部。

### 追问

这个实现不是线程安全的。多线程场景要加锁或使用成熟缓存库，比如 Caffeine。

## 9. TopK 问题

### 题目

从大量数字中找出最大的 K 个。

### 答案思路

用大小为 K 的小顶堆：

1. 堆没满时直接加入。
2. 堆满后，如果当前元素大于堆顶，就删除堆顶，加入当前元素。
3. 遍历结束后，堆里就是最大的 K 个数。

### Java 实现

```java
public List<Integer> topK(int[] nums, int k) {
    PriorityQueue<Integer> heap = new PriorityQueue<>();
    for (int num : nums) {
        if (heap.size() < k) {
            heap.offer(num);
        } else if (num > heap.peek()) {
            heap.poll();
            heap.offer(num);
        }
    }
    return new ArrayList<>(heap);
}
```

### 复杂度

时间复杂度 O(n log k)，适合 n 很大但 k 较小的场景。

### 项目联系

销量 Top10 报表可以用 SQL 排序实现；如果数据已经在内存或来自流式统计，也可以用 TopK 思路。

## 10. 常见 SQL 题：每个用户的订单数

### 题目

统计每个用户的订单数量。

### SQL

```sql
select user_id, count(*) as order_count
from orders
group by user_id;
```

### 讲解

`group by user_id` 按用户分组，`count(*)` 统计每组数量。

## 11. SQL 题：查询订单数大于 5 的用户

```sql
select user_id, count(*) as order_count
from orders
group by user_id
having count(*) > 5;
```

### where 和 having 区别

`where` 在分组前过滤行；`having` 在分组后过滤聚合结果。

## 12. SQL 题：查询每个分类下销量最高的菜品

如果 MySQL 支持窗口函数：

```sql
select *
from (
    select
        category_id,
        dish_id,
        sum(number) as total_sales,
        row_number() over (
            partition by category_id
            order by sum(number) desc
        ) as rn
    from order_detail
    group by category_id, dish_id
) t
where t.rn = 1;
```

### 讲解

1. 先按分类和菜品统计销量。
2. 再用 `row_number` 在每个分类内排序。
3. 取排名第一。

如果 MySQL 版本不支持窗口函数，可以用子查询或在业务层处理。

## 13. SQL 题：查询最近 7 天每天营业额

```sql
select
    date(order_time) as stat_date,
    sum(amount) as turnover
from orders
where order_time >= date_sub(curdate(), interval 7 day)
  and status = 5
group by date(order_time)
order by stat_date;
```

### 优化提醒

`where date(order_time) = ?` 会对索引列使用函数，可能导致索引失效。范围条件更好：

```sql
where order_time >= ?
  and order_time < ?
```

## 14. SQL 题：查有订单的用户信息

```sql
select distinct u.*
from user u
join orders o on u.id = o.user_id;
```

如果只需要判断存在：

```sql
select *
from user u
where exists (
    select 1
    from orders o
    where o.user_id = u.id
);
```

## 15. 系统设计题：设计登录鉴权

### 回答结构

```text
用户提交账号密码或微信 code。
服务端校验身份成功后生成 token，返回前端。
前端后续请求携带 token。
后端拦截器校验 token，解析用户 id，放入 ThreadLocal。
Controller 和 Service 处理业务。
请求结束后清理 ThreadLocal。
```

### 要补充的点

1. 密码要加盐哈希存储，不要明文。
2. JWT payload 不放敏感信息。
3. token 设置过期时间。
4. 如果需要主动退出，可用 Redis 黑名单或服务端 token 版本号。
5. 管理端和用户端 token 可以使用不同密钥或不同 claims。

## 16. 系统设计题：设计下单接口

### 简版方案

1. 校验用户登录。
2. 校验地址。
3. 查询购物车。
4. 校验商品状态和价格。
5. 创建订单主表。
6. 创建订单明细。
7. 清空购物车。
8. 返回订单信息。

### 生产补充

1. 使用事务。
2. 防重复提交。
3. 订单号全局唯一。
4. 如果有库存，扣减库存。
5. 如果有优惠券，核销优惠券。
6. 关键操作记录日志。
7. 支付超时自动取消。

### 面试答法

```text
我会先保证正确性，再考虑高并发。
普通下单用数据库事务保证订单、明细、购物车一致；
如果并发很高，再考虑 Redis 预扣库存、MQ 削峰、异步创建订单。
```

## 17. 系统设计题：设计缓存菜品列表

### 方案

1. key：`dish:list:{categoryId}`。
2. 查询时先查 Redis。
3. 未命中查 MySQL。
4. 写入 Redis，TTL 加随机值。
5. 菜品新增、修改、删除、起售停售时删除对应分类缓存。
6. 防穿透：不存在分类可缓存空值短 TTL。

### 追问

如果分类下菜品变化频繁，缓存收益会降低。要结合读写比例决定是否缓存。

## 18. 系统设计题：设计订单超时自动取消

### 方案一：定时任务扫描

```text
每分钟扫描超过 15 分钟未支付订单 -> 更新为已取消 -> 恢复库存
```

优点：简单。  
缺点：扫描压力和时间不精确。

### 方案二：延迟消息

```text
创建订单 -> 发送 15 分钟延迟消息 -> 消费时检查订单是否仍未支付 -> 未支付则取消
```

优点：更及时。  
缺点：依赖 MQ，消息可靠性和重复消费要处理。

### 方案三：Redis ZSet

```text
订单 id 放入 ZSet，score 为过期时间戳。
后台任务按 score 拉取到期订单处理。
```

优点：实现相对灵活。  
缺点：仍需要后台任务和可靠性处理。

## 19. 系统设计题：设计销量排行榜

### 简单方案

用 SQL 按订单明细聚合：

```sql
select name, sum(number) as sales
from order_detail
group by name
order by sales desc
limit 10;
```

适合数据量不大、实时性要求不高。

### 进阶方案

1. 支付成功后发送订单已支付事件。
2. 消费者更新 Redis ZSet：

```text
ZINCRBY sales:rank 3 "宫保鸡丁"
```

3. 查询排行榜：

```text
ZREVRANGE sales:rank 0 9 WITHSCORES
```

### 风险

Redis 统计要考虑补偿和定期校准，最终仍以数据库订单明细为准。

## 20. 面试现场不会做怎么办？

可以这样处理：

1. 先确认题意：“我理解输入是这样，输出是这样，对吗？”
2. 给暴力解法：“最直接可以双重循环，时间复杂度 O(n²)。”
3. 再尝试优化：“如果用 HashMap 保存已访问元素，可以降到 O(n)。”
4. 写出能跑的代码，不要一上来追求最优。
5. 主动说明边界：“空数组、重复元素、找不到结果我会这样处理。”

面试官通常更看重思路是否清楚，而不是每道题都秒杀。

## 21. 自测题单

Java 手写：

1. 反转字符串。
2. 判断回文。
3. 两数之和。
4. 字符计数。
5. 链表反转。
6. 实现简单队列。
7. LRU 缓存。
8. TopK。

SQL：

1. 每个用户订单数。
2. 订单数大于 5 的用户。
3. 最近 7 天营业额。
4. 销量 Top10。
5. 每个分类销量最高菜品。

系统设计：

1. 登录鉴权。
2. 下单接口。
3. 菜品缓存。
4. 订单超时取消。
5. 销量排行榜。

