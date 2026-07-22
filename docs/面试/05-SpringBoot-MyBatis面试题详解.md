# 05 - Spring Boot、Spring、MyBatis 面试题详解

> 复习目标：把 Spring 的 IOC、AOP、事务、自动配置、MVC 请求链路和 MyBatis 代理机制讲清楚，并能对应到当前项目代码。

## 1. Spring 体系面试重点

常见提问路径：

1. 先问 IOC、AOP、Bean 生命周期。
2. 再问 Spring MVC 请求流程。
3. 再问 Spring Boot 自动配置。
4. 然后重点追问事务失效。
5. 最后结合 MyBatis 问 Mapper 代理、动态 SQL、缓存、分页。

如果你只准备一个点，优先准备“事务为什么会失效”。这是 Java 后端高频题。

## 2. 什么是 IOC？

### 答案

IOC 是控制反转，意思是对象的创建和依赖关系不再由业务代码自己控制，而是交给 Spring 容器管理。我们通过注解或配置声明依赖，Spring 负责创建对象、装配对象、管理生命周期。

### 讲解

没有 IOC：

```java
OrderService service = new OrderServiceImpl();
```

有 IOC：

```java
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
}
```

`OrderServiceImpl` 不需要自己 new `OrderMapper`，Spring 会注入依赖。

### 项目联系

项目里的 `@Controller`、`@Service`、`@Mapper`、`@Component`、`@Configuration` 都是在把对象交给 Spring 管理。

## 3. 什么是 DI？

### 答案

DI 是依赖注入，是实现 IOC 的方式。Spring 把对象依赖的其他 Bean 注入进来，常见方式有构造器注入、setter 注入、字段注入。

### 推荐

生产项目更推荐构造器注入：

```java
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }
}
```

优点：

1. 依赖不可变。
2. 更利于单元测试。
3. 避免对象创建后依赖为空。

当前项目大量使用字段注入，学习阶段可以接受，但面试里可以说出构造器注入的优势。

## 4. Bean 生命周期是什么？

### 答案

简化流程：

1. 实例化 Bean。
2. 属性填充，也就是依赖注入。
3. 执行 Aware 接口回调。
4. BeanPostProcessor 前置处理。
5. 初始化方法，比如 `@PostConstruct`、`InitializingBean`、init-method。
6. BeanPostProcessor 后置处理。
7. Bean 可以使用。
8. 容器关闭时执行销毁方法。

### 讲解

AOP 代理通常与 BeanPostProcessor 后置处理相关。也就是说，你拿到的 Bean 有时不是原始对象，而是代理对象。Spring 事务、AOP 日志、权限切面都依赖代理机制。

## 5. Spring 如何解决循环依赖？

### 答案

Spring 对单例 Bean 的部分循环依赖可以通过三级缓存解决，核心思路是提前暴露对象引用，让 A 和 B 在属性注入时能互相找到。但构造器循环依赖无法解决，因为对象还没创建出来。

### 讲解

三级缓存大致是：

1. 一级缓存：完整初始化好的单例 Bean。
2. 二级缓存：提前暴露的半成品 Bean。
3. 三级缓存：ObjectFactory，用于生成提前暴露对象，特别是代理对象。

### 面试注意

不要把循环依赖当成好设计。能解决不代表应该这么写。业务代码应该尽量避免 Bean 互相强依赖，可以拆分职责或抽出第三个服务。

## 6. 什么是 AOP？

### 答案

AOP 是面向切面编程，用来处理横切关注点，比如日志、权限、事务、审计字段填充。它可以在不侵入业务代码的情况下，在目标方法前后织入额外逻辑。

### 项目联系

当前项目的 `AutoFillAspect` 就是 AOP：

1. 用 `@Aspect` 声明切面。
2. 用 `@Pointcut` 定义切点，拦截带 `@AutoFill` 注解的 Mapper 方法。
3. 用 `@Before` 在方法执行前填充公共字段。

### 常见术语

| 术语 | 含义 |
| --- | --- |
| JoinPoint | 可被增强的位置，比如方法调用 |
| Pointcut | 匹配哪些 JoinPoint |
| Advice | 增强逻辑，比如前置、后置、环绕 |
| Aspect | 切点 + 通知 |
| Weaving | 把增强逻辑织入目标对象 |

## 7. JDK 动态代理和 CGLIB 有什么区别？

### 答案

JDK 动态代理基于接口生成代理类，目标类需要实现接口；CGLIB 基于继承生成子类代理，不要求接口，但不能代理 final 类和 final 方法。

### Spring 怎么选

如果目标类实现了接口，Spring 默认可以使用 JDK 动态代理；如果没有接口，通常使用 CGLIB。Spring Boot 中也可以通过配置影响代理方式。

### 面试联系

事务和 AOP 都通常通过代理实现。因此“同类方法内部调用事务失效”本质上是因为没有经过代理对象。

## 8. Spring 事务是怎么实现的？

### 答案

Spring 声明式事务主要通过 AOP 代理实现。调用带 `@Transactional` 的方法时，请求先进入代理对象，代理对象在方法执行前开启事务，方法正常返回时提交事务，抛出符合条件的异常时回滚事务。

### 核心点

1. 本质是代理。
2. 事务边界通常放在 Service 层。
3. 默认运行时异常和 Error 回滚，受检异常默认不回滚。
4. 数据库引擎要支持事务，比如 MySQL InnoDB。

## 9. Spring 事务什么时候会失效？

### 答案

高频场景：

1. 方法不是 public。
2. 同一个类内部方法调用，没有经过代理对象。
3. 异常被 catch 后没有继续抛出。
4. 抛出受检异常但没有配置 rollbackFor。
5. 数据库不支持事务或表引擎不支持事务。
6. 事务方法没有被 Spring 管理，比如自己 new 对象。
7. 多线程异步执行，事务上下文没有传播到新线程。
8. `@Transactional` 放在 private/final 方法上。

### 详细讲解

同类调用失效：

```java
@Service
public class OrderService {
    public void outer() {
        inner(); // 没有经过代理对象
    }

    @Transactional
    public void inner() {
        // 事务可能不生效
    }
}
```

异常被吞：

```java
@Transactional
public void submit() {
    try {
        insertOrder();
        insertDetail();
    } catch (Exception e) {
        log.error("failed", e);
    }
}
```

如果异常被 catch 后不再抛出，代理对象认为方法正常结束，就会提交事务。

正确做法：

```java
@Transactional(rollbackFor = Exception.class)
public void submit() {
    insertOrder();
    insertDetail();
}
```

### 项目联系

下单链路涉及订单主表、订单明细、购物车清空，应该把事务加在 Service 的下单方法上，并保证异常能抛出触发回滚。

## 10. Spring 事务传播行为是什么？

### 答案

传播行为定义一个事务方法调用另一个事务方法时，事务如何传播。

常见传播行为：

| 传播行为 | 含义 |
| --- | --- |
| REQUIRED | 默认，有事务就加入，没有就新建 |
| REQUIRES_NEW | 挂起当前事务，新开一个事务 |
| NESTED | 嵌套事务，依赖保存点 |
| SUPPORTS | 有事务就加入，没有就非事务 |
| NOT_SUPPORTED | 挂起事务，以非事务执行 |
| MANDATORY | 必须在已有事务中执行 |
| NEVER | 必须非事务执行 |

### 面试重点

最常用是 REQUIRED。`REQUIRES_NEW` 常用于主流程失败也希望保留的操作，比如审计日志，但要注意它会独立提交。

## 11. Spring MVC 请求流程是什么？

### 答案

简化流程：

1. 请求进入 DispatcherServlet。
2. HandlerMapping 找到对应 Controller 方法。
3. HandlerAdapter 调用目标方法。
4. 参数解析和类型转换。
5. Controller 调用 Service。
6. 返回结果经过消息转换器，比如转 JSON。
7. 返回 HTTP 响应。

### 项目联系

项目中 Controller 接收 DTO，调用 Service 处理业务，最终返回 `Result<T>`。JSON 序列化由 Spring MVC 的 HttpMessageConverter 完成。

## 12. 拦截器和过滤器有什么区别？

### 答案

Filter 是 Servlet 规范的一部分，作用在更底层，可以过滤几乎所有请求；Interceptor 是 Spring MVC 提供的，主要拦截进入 Controller 的请求。

| 点 | Filter | Interceptor |
| --- | --- | --- |
| 来源 | Servlet 规范 | Spring MVC |
| 执行位置 | DispatcherServlet 前后 | HandlerMapping 找到处理器后 |
| 能否注入 Spring Bean | 可以，但配置略不同 | 天然在 Spring 容器中 |
| 常见用途 | 编码、跨域、安全过滤 | 登录校验、权限、日志 |

### 项目联系

JWT 登录校验适合用拦截器实现，因为它要结合 Spring MVC 路径规则和业务上下文。

## 13. Spring Boot 自动配置是什么？

### 答案

Spring Boot 自动配置会根据 classpath 中的依赖、配置文件和条件注解，自动创建常用 Bean，减少手动配置。

例如引入 `spring-boot-starter-web` 后，会自动配置 Tomcat、Spring MVC、JSON 转换器等；引入 `spring-boot-starter-data-redis` 后，会自动配置 Redis 连接工厂和 RedisTemplate 相关 Bean。

### 关键机制

1. Starter 负责依赖聚合。
2. AutoConfiguration 负责自动创建 Bean。
3. `@ConditionalOnClass`、`@ConditionalOnMissingBean` 等条件注解决定是否生效。
4. 配置属性绑定负责读取 `application.yml`。

## 14. `@SpringBootApplication` 包含什么？

### 答案

它是组合注解，核心包含：

1. `@SpringBootConfiguration`。
2. `@EnableAutoConfiguration`。
3. `@ComponentScan`。

### 讲解

`@ComponentScan` 默认扫描启动类所在包及其子包，所以项目启动类通常放在根包 `com.sky` 下，方便扫描 controller、service、mapper、config 等组件。

## 15. MyBatis Mapper 接口为什么不用写实现类？

### 答案

MyBatis 会为 Mapper 接口创建动态代理对象。调用接口方法时，代理对象根据方法名、参数和 XML/注解中的 SQL 映射，执行对应 SQL，并把结果映射成 Java 对象。

### 讲解

核心不是“接口自动有实现”，而是 MyBatis 在运行时生成代理。代理对象会找到对应的 `MappedStatement`，执行 SQL，处理参数和结果映射。

### 项目联系

`OrderMapper`、`DishMapper`、`EmployeeMapper` 等都是 Mapper 接口，真正 SQL 多数写在 XML 文件里。

## 16. `#{}` 和 `${}` 有什么区别？

### 答案

`#{}` 是预编译参数占位，会使用 PreparedStatement，能防止 SQL 注入；`${}` 是字符串拼接，会把参数直接拼进 SQL，有 SQL 注入风险。

### 示例

安全：

```sql
select * from employee where username = #{username}
```

危险：

```sql
select * from employee where username = '${username}'
```

### 什么时候用 `${}`

少数场景需要动态拼接表名、列名、排序字段，但必须做白名单校验。

## 17. resultType 和 resultMap 有什么区别？

### 答案

`resultType` 适合简单映射，查询列名和对象属性名能直接对应；`resultMap` 适合复杂映射，比如字段名不一致、一对多、一对一、嵌套结果。

### 项目联系

如果数据库字段是 `create_time`，Java 属性是 `createTime`，可以依赖驼峰映射；如果查询结果更复杂，比如订单带订单明细，就更适合使用 resultMap 或在 Service 层组装 VO。

## 18. MyBatis 动态 SQL 有哪些？

### 答案

常见标签：

1. `if`：条件判断。
2. `where`：自动处理 where 和 and。
3. `set`：更新时自动处理逗号。
4. `foreach`：遍历集合，常用于批量插入、in 查询。
5. `choose/when/otherwise`：类似 switch。
6. `trim`：自定义前缀后缀处理。

### 项目联系

分页查询、条件查询订单、按时间范围统计报表，都适合动态 SQL。

## 19. MyBatis 一级缓存和二级缓存是什么？

### 答案

一级缓存是 SqlSession 级别，默认开启。同一个 SqlSession 中相同查询可能直接从缓存返回。二级缓存是 Mapper namespace 级别，需要配置开启，多个 SqlSession 可共享。

### 面试注意

实际 Spring Boot + MyBatis 项目里，每次 Mapper 方法通常由 Spring 管理 SqlSession 生命周期，一级缓存感知不强。二级缓存容易带来一致性问题，生产中要谨慎使用，很多项目更倾向用 Redis 这类外部缓存统一管理。

## 20. PageHelper 分页原理是什么？

### 答案

PageHelper 通常通过 MyBatis 插件机制拦截 SQL 执行，在查询前设置分页参数，然后改写 SQL，增加 limit，并执行 count 查询获取总数。

### 使用注意

1. `PageHelper.startPage` 要紧挨着查询方法。
2. 不要分页后又在内存中过滤大量数据。
3. 大页码分页性能差，必要时使用游标分页或基于 id 的范围查询。

## 21. MyBatis 常见性能问题有哪些？

### 答案

1. N+1 查询：列表查出来后循环查详情。
2. 一次查询太多字段或太多行。
3. 动态 SQL 没走索引。
4. 模糊查询前缀 `%` 导致索引失效。
5. 大分页 offset 很大。
6. resultMap 嵌套映射过度复杂。

### 项目联系

订单分页查询后，如果每个订单再查一次订单明细，数据量大时可能出现 N+1。可以按订单 id 批量查询明细，再在内存按 orderId 分组组装。

## 22. 面试自测

1. IOC 和 DI 分别是什么？
2. Bean 生命周期怎么讲？
3. AOP 在当前项目哪里用到？
4. Spring 事务为什么会失效？
5. `@Transactional` 默认回滚哪些异常？
6. Spring MVC 请求流程是什么？
7. Spring Boot 自动配置如何生效？
8. Mapper 接口为什么不用实现类？
9. `#{}` 和 `${}` 有什么区别？
10. PageHelper 分页原理是什么？

