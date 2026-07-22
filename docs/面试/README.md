# Java 后端面试指导总目录

> 适用范围：中国大陆 Java 后端开发岗位，重点面向校招、实习、初级和 1-3 年经验候选人。  
> 复习目标：不仅会背结论，还要能把原理、代码、项目场景和优化方案讲成一条完整链路。

## 1. 文档怎么读

建议按下面顺序阅读：

| 顺序 | 文档 | 解决的问题 |
| --- | --- | --- |
| 1 | `01-大陆Java后端面试形势与准备策略.md` | 了解面试怎么考、企业看什么、不同轮次怎么准备 |
| 2 | `02-项目包装与苍穹外卖面试讲法.md` | 把当前项目讲成面试官愿意继续追问的项目 |
| 3 | `03-Java基础集合异常面试题详解.md` | 补齐 Java 语言基础、集合、异常、泛型等高频题 |
| 4 | `04-JVM并发线程池面试题详解.md` | 准备 JVM、内存模型、锁、线程池、并发容器 |
| 5 | `05-SpringBoot-MyBatis面试题详解.md` | 准备 Spring、Spring Boot、事务、AOP、MyBatis |
| 6 | `06-MySQL事务索引SQL优化面试题详解.md` | 准备索引、事务、锁、MVCC、SQL 优化和分页 |
| 7 | `07-Redis缓存分布式锁面试题详解.md` | 准备 Redis 数据结构、缓存问题、分布式锁 |
| 8 | `08-分布式微服务消息队列场景题详解.md` | 准备 MQ、接口幂等、限流、分布式事务、微服务 |
| 9 | `09-手写题SQL题系统设计题训练.md` | 训练现场编码、SQL 和系统设计表达 |
| 10 | `10-HR行为面反问与模拟面试评分表.md` | 准备 HR 面、行为面、反问、模拟评分 |

## 2. 面试回答通用结构

大多数技术题不要只背定义，建议按五段回答：

1. 先给结论：一句话说明它是什么、解决什么问题。
2. 再讲原理：讲关键机制，不要一上来堆术语。
3. 补充细节：讲边界条件、常见坑、版本差异。
4. 结合项目：说明自己在 `sky-take-out` 里哪里用过或可以怎么改。
5. 主动扩展：如果有生产级优化，再补一句“实际项目里我会进一步考虑什么”。

例如面试官问“Redis 缓存穿透怎么解决”：

```text
缓存穿透是请求查询一个数据库里也不存在的数据，缓存一直查不到，导致请求都打到数据库。
常见解决方式有缓存空值、布隆过滤器、参数校验和热点接口限流。
如果是业务上确实可能不存在的数据，比如根据 id 查菜品详情，可以把 null 结果短 TTL 缓存起来；
如果数据集合比较稳定，比如商品 id 集合，可以用布隆过滤器提前拦截明显不存在的 id。
在苍穹外卖项目里，菜品、套餐、分类详情如果做缓存，就要考虑不存在 id 的短 TTL 空缓存，避免恶意请求拖垮 MySQL。
```

## 3. 技术面重点排序

如果时间有限，优先级如下：

1. 项目链路：登录鉴权、下单、支付后状态变化、订单报表、Redis 缓存、WebSocket 通知。
2. MySQL：索引、事务、锁、MVCC、慢 SQL、分页优化。
3. Java 基础：集合、HashMap、String、异常、泛型、Stream。
4. Spring：Bean 生命周期、IOC、AOP、事务失效、自动配置。
5. 并发：线程池参数、锁、volatile、ThreadLocal、CompletableFuture。
6. Redis：缓存一致性、穿透/击穿/雪崩、分布式锁、过期策略。
7. MQ 和分布式：可靠消息、重复消费、幂等、分布式事务。

## 4. 7 天冲刺计划

| 天数 | 任务 | 输出物 |
| --- | --- | --- |
| 第 1 天 | 梳理简历和项目讲法 | 1 分钟项目介绍、3 个亮点、3 个不足与改进 |
| 第 2 天 | Java 基础和集合 | 能手写 HashMap 扩容、equals/hashCode、ArrayList/LinkedList 对比 |
| 第 3 天 | JVM 和并发 | 能解释线程池、锁、volatile、ThreadLocal 泄漏 |
| 第 4 天 | Spring 和 MyBatis | 能解释 IOC/AOP/事务失效/MyBatis 一级二级缓存 |
| 第 5 天 | MySQL | 能解释 B+ 树、最左前缀、MVCC、间隙锁、慢 SQL |
| 第 6 天 | Redis 和 MQ | 能解释缓存三大问题、分布式锁、消息可靠性 |
| 第 7 天 | 模拟面试 | 完成 2 轮自问自答，录音复盘表达问题 |

## 5. 30 天进阶计划

1. 第 1 周：把项目讲熟，补 Spring Boot + MyBatis + MySQL 主链路。
2. 第 2 周：集中攻克 Java 基础、JVM、并发。
3. 第 3 周：集中攻克 Redis、MQ、分布式场景题。
4. 第 4 周：做手写题、SQL 题、系统设计题和模拟面试。

每周至少做一次“闭卷讲项目”：不看代码，用白纸画出请求链路、数据表、核心类和异常处理。

## 6. 面试中不要犯的错误

1. 不要说“我只是跟着教程做的”。可以说“这是一个学习项目，我重点把订单链路、缓存、权限和通知这几块重新梳理过，并补了自己的优化思考”。
2. 不要只背概念。面试官更关心你是否能解释为什么这么设计、出了问题怎么排查。
3. 不要把没做过的东西说成生产经验。可以说“当前项目里还没完整落地，但我知道如果上生产要补哪些点”。
4. 不要项目一问就散。所有回答尽量回到“用户请求进来以后，经过 Controller、Service、Mapper、DB/Redis，最后返回或推送”这条链路。
5. 不要回避不足。能清楚说出不足和改进路线，本身就是加分项。

## 7. 参考资料

这些资料用于校准技术结论，实际面试以目标公司技术栈和岗位 JD 为准：

- Java 官方文档：https://docs.oracle.com/en/java/
- OpenJDK JEP：https://openjdk.org/jeps/0
- Spring Boot Reference：https://docs.spring.io/spring-boot/reference/
- Spring Framework Reference：https://docs.spring.io/spring-framework/reference/
- MyBatis 官方文档：https://mybatis.org/mybatis-3/
- MySQL 8.4 Reference Manual：https://dev.mysql.com/doc/refman/8.4/en/
- Redis 官方文档：https://redis.io/docs/latest/
- Apache Kafka 官方文档：https://kafka.apache.org/documentation/
- RabbitMQ 官方文档：https://www.rabbitmq.com/docs

