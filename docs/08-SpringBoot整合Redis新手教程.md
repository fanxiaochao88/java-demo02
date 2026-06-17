# Spring Boot 整合 Redis 新手教程

> **适用对象**：Spring Boot 零基础、Redis 零基础的新手同学
> **前置知识**：Java 基础、知道 Spring Boot 能干啥（能跑起来一个 Web 项目即可）
> **学习目标**：理解 `RedisConfiguration.java` 里的每一行代码，并能自己动手用 Redis 存取数据

---

## 目录

1. [Redis 是什么？](#1-redis-是什么)
2. [Spring Data Redis 是什么？](#2-spring-data-redis-是什么)
3. [Spring Boot 如何自动配置 Redis](#3-spring-boot-如何自动配置-redis)
4. [逐行拆解 RedisConfiguration.java](#4-逐行拆解-redisconfigurationjava)
5. [核心概念：RedisTemplate](#5-核心概念redistemplate)
6. [核心概念：序列化器（Serializer）](#6-核心概念序列化器serializer)
7. [核心概念：RedisConnectionFactory](#7-核心概念redisconnectionfactory)
8. [常用 Redis 操作速查](#8-常用-redis-操作速查)
9. [实战：在 Service 中使用 Redis](#9-实战在-service-中使用-redis)
10. [常见问题与最佳实践](#10-常见问题与最佳实践)
11. [总结](#11-总结)

---

## 1. Redis 是什么？

### 1.1 一句话理解

**Redis 是一个"放在内存里的数据库"**。它把数据存在内存（而不是硬盘）里，所以读写速度极快。你可以把它想象成一个**全局的 `Map<String, Object>`**——你可以往里面 `put` 数据，也可以 `get` 数据，这个 Map 在你程序重启后数据还在。

### 1.2 Redis 的数据类型

Redis 不只是简单的 key-value，它支持 5 种核心数据类型：

| 类型 | 类比 Java | 典型场景 |
|------|-----------|----------|
| **String** | `Map<String, String>` | 存验证码、存 token、计数器 |
| **Hash** | `Map<String, HashMap<String, String>>` | 存用户信息（name, age, email） |
| **List** | `LinkedList<String>` | 消息队列、最新评论列表 |
| **Set** | `HashSet<String>` | 标签、点赞用户集合（去重） |
| **ZSet** (Sorted Set) | 带权重的 `TreeSet` | 排行榜、带优先级的任务队列 |

### 1.3 为什么用 Redis？

```
传统 MySQL：  用户请求 → 查 SQL → 等磁盘 I/O → 返回    （慢，几十毫秒）
使用 Redis：  用户请求 → 查 Redis → 直接内存返回       （快，亚毫秒级）
```

典型的使用场景是 **缓存**：热点数据放 Redis，查不到再查 MySQL，大大减轻数据库压力。

---

## 2. Spring Data Redis 是什么？

### 2.1 问题的提出

假设没有框架，我们要在 Java 里操作 Redis，得这么写：

```java
// 原生 Jedis 客户端（纯手写，非常繁琐）
Jedis jedis = new Jedis("localhost", 6379);
jedis.auth("123456");
jedis.set("key", "value");
String value = jedis.get("key");
jedis.close();  // 容易忘记关！
```

问题很明显：
- 每次都要手动创建连接、关闭连接
- 要手动处理连接的密码、端口等
- 字节和对象的转换要自己写
- 连接池管理很复杂

### 2.2 Spring Data Redis 帮你搞定的事

**Spring Data Redis** 是 Spring 生态里专门操作 Redis 的框架。它在底层的 Redis 客户端（Jedis / Lettuce）上面包了一层，让你用**面向对象的方式**操作 Redis：

```
你的代码
  ↓
Spring Data Redis（RedisTemplate）
  ↓
Lettuce / Jedis（底层连接驱动）
  ↓
Redis 服务器
```

它帮你自动完成了：
- **连接管理**：自动获取、释放连接，连接池自动配置
- **序列化/反序列化**：自动把 Java 对象转成字节存到 Redis，取出来时自动转回 Java 对象
- **异常处理**：把底层的 Redis 异常转成 Spring 的 DataAccessException
- **模板方法**：`RedisTemplate` 就是核心工具类，封装了所有 Redis 操作

---

## 3. Spring Boot 如何自动配置 Redis

### 3.1 引入依赖即可

只要在 `pom.xml` 中引入了：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Spring Boot 就会**自动做很多事**（这是 Spring Boot 的"自动配置"魔法）：

### 3.2 Spring Boot 自动帮你做了什么？

```
1. 读取 application.yml 中的 spring.redis.* 配置
2. 根据配置创建 RedisConnectionFactory（底层连接工厂）
   - 默认使用 Lettuce（高性能 Redis 客户端）
   - 自动配置连接池
3. 自动创建 RedisTemplate<String, String>（一个已经配好的模板）
4. 自动创建 StringRedisTemplate（专门操作 String 的模板）
```

也就是说，**即使你不写 `RedisConfiguration.java`，项目里也能直接用 `@Autowired RedisTemplate`**。

### 3.3 那为什么还要写配置类？

因为 **Spring Boot 自动创建的 `RedisTemplate` 使用的是 Java 原生序列化**（JDK 序列化），会有两个问题：

| 问题 | 说明 |
|------|------|
| **Redis 里存的 key 看不懂** | `"user:1"` 这个 key 存进去，在 Redis 里看到的是 `\xAC\xED\x00\x05...` 乱码 |
| **跨语言不兼容** | Java 原生序列化的二进制格式，其他语言（Python、Go）无法反序列化 |

**所以我们需要自定义一个 RedisTemplate，让 key 用 String 序列化（人可读）。**

---

## 4. 逐行拆解 RedisConfiguration.java

来看咱们项目里的 `RedisConfiguration.java`（我加了详细注释）：

```java
// 第1行：声明包路径，和项目目录结构对应
package com.sky.config;

// 第3行：Lombok 注解，编译时自动生成 log 对象，等价于：
//   private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);
import lombok.extern.slf4j.Slf4j;

// 第4行：导入 @Bean 注解
import org.springframework.context.annotation.Bean;
// 第5行：导入 @Configuration 注解
import org.springframework.context.annotation.Configuration;
// 第6行：Redis 连接工厂接口（Lettuce 或 Jedis 实现的）
import org.springframework.data.redis.connection.RedisConnectionFactory;
// 第7行：RedisTemplate —— 操作 Redis 的核心模板类
import org.springframework.data.redis.core.RedisTemplate;
// 第8行：StringRedisSerializer —— 把 key 序列化成可读字符串
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration  // 第10行：告诉 Spring："这是一个配置类，里面的 @Bean 方法要执行"
@Slf4j          // 第11行：Lombok 提供日志功能
public class RedisConfiguration {

    @Bean  // 第13行：告诉 Spring："这个方法返回的对象，帮我放到容器里管理"
    public RedisTemplate redisTemplate(
        RedisConnectionFactory redisConnectionFactory  // Spring 自动注入连接工厂
    ) {
        // 第15行：手动 new 一个 RedisTemplate 对象
        RedisTemplate redisTemplate = new RedisTemplate();

        // 第18行：给这个 RedisTemplate 设置连接工厂
        //   （连接工厂是 Spring Boot 根据 application.yml 自动创建的）
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 第19行：设置 key 的序列化器为 StringRedisSerializer
        //   "myKey" → 序列化后还是 → "myKey"（Redis 里人可读）
        //   ❌ 如果不用这个，key 会变成：\xAC\xED\x00\x05t\x00\x05myKey
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        // 第21行：返回配置好的 RedisTemplate，Spring 会把它管理起来
        return redisTemplate;
    }
}
```

### 4.1 `@Configuration` 是什么？

```java
@Configuration
public class RedisConfiguration { ... }
```

这是 Spring 的核心注解之一。**被 `@Configuration` 标注的类 = 配置类**。

Spring 启动时会扫描所有 `@Configuration` 类，执行其中所有带 `@Bean` 的方法，把返回值放到**Spring 容器**（也叫 IoC 容器）里。

**类比**：Spring 容器就像一个巨大的 HashMap<String, Object>：
- Key = bean 的名字（默认是方法名），如 `"redisTemplate"`
- Value = 方法返回的对象

### 4.2 `@Bean` 是什么？

```java
@Bean
public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
    RedisTemplate redisTemplate = new RedisTemplate();
    // ... 配置 ...
    return redisTemplate;
}
```

`@Bean` 告诉 Spring："这个方法返回的对象，请帮我管理起来"。

**方法参数 `RedisConnectionFactory redisConnectionFactory`** 是由 Spring 自动传入的——Spring 发现这个方法有参数，就去容器里找有没有现成的 `RedisConnectionFactory` 类型的 bean，找到了就自动传进来。这就是 **依赖注入（DI）**。

### 4.3 `@Slf4j` 是什么？

Lombok 的注解，编译时自动生成：

```java
// 等价于手写：
private static final org.slf4j.Logger log =
    org.slf4j.LoggerFactory.getLogger(RedisConfiguration.class);
```

之后在类里就能直接用 `log.info()`, `log.error()` 打日志了。

---

## 5. 核心概念：RedisTemplate

### 5.1 什么是模板模式？

**模板（Template）设计模式**的思想是：把"复杂的、重复的步骤"封装起来，你只要告诉模板"做什么"，不用管"怎么做"。

```java
// 不用 RedisTemplate（原生）
Jedis jedis = pool.getResource();    // 获取连接
jedis.auth("password");              // 认证
byte[] key = serialize("myKey");     // 序列化 key
byte[] value = serialize(myObj);     // 序列化 value
jedis.set(key, value);               // 执行操作
jedis.close();                       // 释放连接

// 用 RedisTemplate（Spring Data Redis）
redisTemplate.opsForValue().set("myKey", myObj);  // 一行搞定！
```

**获取连接、序列化、释放连接——这些 RedisTemplate 全帮你做了。**

### 5.2 RedisTemplate 的操作分类

`RedisTemplate` 针对 Redis 的 5 种数据类型，提供了 5 种操作接口：

| 方法 | 返回类型 | 对应 Redis 类型 | 常用操作 |
|------|----------|-----------------|----------|
| `opsForValue()` | `ValueOperations` | String | `set`, `get`, `increment` |
| `opsForHash()` | `HashOperations` | Hash | `put`, `get`, `entries` |
| `opsForList()` | `ListOperations` | List | `leftPush`, `rightPop` |
| `opsForSet()` | `SetOperations` | Set | `add`, `members` |
| `opsForZSet()` | `ZSetOperations` | ZSet | `add`, `range` |

### 5.3 代码示例

```java
@Autowired
private RedisTemplate redisTemplate;

// 存字符串
redisTemplate.opsForValue().set("user:name", "张三");

// 取字符串
String name = (String) redisTemplate.opsForValue().get("user:name");

// 存对象到 Hash
redisTemplate.opsForHash().put("user:1001", "name", "张三");
redisTemplate.opsForHash().put("user:1001", "age", "25");

// 取 Hash 中某个字段
String userName = (String) redisTemplate.opsForHash().get("user:1001", "name");

// 设置过期时间（10 秒后自动删除）
redisTemplate.expire("user:name", 10, TimeUnit.SECONDS);

// 删除 key
redisTemplate.delete("user:name");
```

---

## 6. 核心概念：序列化器（Serializer）

### 6.1 什么叫序列化？

**序列化** = 把 Java 对象变成字节（方便存储/传输）
**反序列化** = 把字节变回 Java 对象

```
Java 对象 "张三"  → [序列化] → 字节 0xE5 0xBC 0xA0 0xE4 0xB8 0x89 → 存到 Redis
从 Redis 取回字节 → [反序列化] → Java 对象 "张三"
```

### 6.2 为什么需要关心序列化？

Redis 只能存**字节**，你传给 RedisTemplate 的 Java 对象必须被转换成字节后才能存进去。**序列化器（Serializer）就是负责这个转换的。**

### 6.3 常见序列化器对比

| 序列化器 | key 在 Redis 中的样子 | 优点 | 缺点 |
|----------|----------------------|------|------|
| **JdkSerializationRedisSerializer** | `\xAC\xED\x00\x05t\x00\x05myKey` | 支持所有 Serializable 对象 | 不可读、占用空间大、跨语言不兼容 |
| **StringRedisSerializer** | `myKey` | 可读、简洁 | 只能序列化 String |
| **Jackson2JsonRedisSerializer** | `{"name":"张三","age":25}` | JSON 格式，可读，跨语言兼容 | 需要指定类型 |
| **GenericJackson2JsonRedisSerializer** | `{"@class":"com.sky.User","name":"张三"}` | 自动带类型信息，反序列化方便 | 多了 `@class` 字段，略占空间 |

### 6.4 我们项目中的配置

```java
// 只设置了 key 的序列化器
redisTemplate.setKeySerializer(new StringRedisSerializer());

// value 的序列化器没有设置，使用 Spring Boot 默认值
// 默认：JdkSerializationRedisSerializer
// key 的 Hash key 也没设置，默认也是 JdkSerializationRedisSerializer
```

**这套配置的含义是**：
- ✅ key 用 String 序列化 → Redis 里 key 人可读
- ⚠️ value 用 JDK 原生序列化 → Redis 里 value 是二进制乱码（但 Java 程序能正常反序列化）

### 6.5 更完整的配置（推荐）

如果你希望 value 在 Redis 里也是可读的 JSON 格式，可以这样配：

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(
    RedisConnectionFactory redisConnectionFactory
) {
    RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(redisConnectionFactory);

    // ========== key 的序列化 ==========
    // 普通 key 用 String
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    // Hash 的 key 也用 String
    redisTemplate.setHashKeySerializer(new StringRedisSerializer());

    // ========== value 的序列化 ==========
    // 用 Jackson 把对象转成 JSON
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    objectMapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        DefaultTyping.NON_FINAL
    );

    GenericJackson2JsonRedisSerializer jsonSerializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    // 普通 value 用 JSON
    redisTemplate.setValueSerializer(jsonSerializer);
    // Hash 的 value 也用 JSON
    redisTemplate.setHashValueSerializer(jsonSerializer);

    return redisTemplate;
}
```

这样配置后，在 Redis 里看到的数据就是：
```
key: "dish:1"           ← 可读！
value: {"@class":"com.sky.entity.Dish","name":"鱼香肉丝","price":28.0}  ← JSON 格式，可读！
```

---

## 7. 核心概念：RedisConnectionFactory

### 7.1 它是什么？

**RedisConnectionFactory 是 Redis 连接的管理中心。** 它负责：

- 创建与 Redis 服务器的连接
- 管理连接池（复用连接，避免反复创建/销毁）
- 处理认证（密码）

### 7.2 Spring Boot 如何创建它？

我们不需要手动创建 `RedisConnectionFactory`！Spring Boot 读取 `application.yml` 的配置，自动帮你创建好。

```yaml
# application.yml
spring:
  redis:
    host: localhost      # Redis 服务器地址
    port: 6379           # Redis 端口
    password: '123456'   # Redis 密码
    database: 0          # 使用第几个数据库（Redis 默认有 16 个，0-15）
```

Spring Boot 看到这些配置后：
1. 自动创建 `RedisConnectionFactory`（底层使用 **Lettuce** 客户端）
2. 自动配置好连接池
3. 把它放进 Spring 容器

所以你在 `redisTemplate()` 方法参数里写的 `RedisConnectionFactory redisConnectionFactory`，Spring 会自动把创建好的传进来。

### 7.3 Jedis vs Lettuce

| 特点 | Jedis | Lettuce（默认） |
|------|-------|-----------------|
| 连接方式 | 每个实例一个连接 | 单个连接可在多线程间共享 |
| 线程安全 | 不安全（需要连接池） | 天生线程安全 |
| 性能 | 一般 | 更好（基于 Netty，异步非阻塞） |
| Spring Boot 默认 | 2.x 之前 | 2.x 开始（当前项目用 Lettuce） |

**你不用关心这些**——Spring Boot 默认用 Lettuce，你只要写好配置就行。

---

## 8. 常用 Redis 操作速查

### 8.1 String 操作（opsForValue）

```java
// 存
redisTemplate.opsForValue().set("key", "value");
// 存，并设置 30 秒过期
redisTemplate.opsForValue().set("key", "value", 30, TimeUnit.SECONDS);
// 取
Object value = redisTemplate.opsForValue().get("key");
// 删
redisTemplate.delete("key");
// 判断是否存在
Boolean exists = redisTemplate.hasKey("key");
// 自增（计数器场景）
Long count = redisTemplate.opsForValue().increment("page:views");
```

### 8.2 Hash 操作（opsForHash）

```java
// 存一个字段
redisTemplate.opsForHash().put("user:1", "name", "张三");
redisTemplate.opsForHash().put("user:1", "age", "25");
// 一次存多个字段
Map<String, Object> map = new HashMap<>();
map.put("name", "张三");
map.put("age", "25");
redisTemplate.opsForHash().putAll("user:1", map);
// 取单个字段
String name = (String) redisTemplate.opsForHash().get("user:1", "name");
// 取所有字段
Map<Object, Object> entries = redisTemplate.opsForHash().entries("user:1");
// 删除字段
redisTemplate.opsForHash().delete("user:1", "age");
```

### 8.3 List 操作（opsForList）

```java
// 左边插入
redisTemplate.opsForList().leftPush("queue", "task1");
// 右边取出
Object task = redisTemplate.opsForList().rightPop("queue");
// 获取所有元素
List<Object> list = redisTemplate.opsForList().range("queue", 0, -1);
```

### 8.4 通用操作

```java
// 设置过期时间
redisTemplate.expire("key", 30, TimeUnit.MINUTES);
// 获取剩余过期时间（秒）
Long ttl = redisTemplate.getExpire("key");
// 删除 key
redisTemplate.delete("key");
// 批量删除
redisTemplate.delete(Arrays.asList("key1", "key2"));
// 查看 key 的类型
DataType type = redisTemplate.type("key");
// 模糊查询 key
Set<String> keys = redisTemplate.keys("user:*");
```

---

## 9. 实战：在 Service 中使用 Redis

### 9.1 场景：缓存菜品数据

假设我们有一个 `DishService`，查询菜品信息。每次都查 MySQL 太慢了，我们用 Redis 做缓存。

```java
package com.sky.service.impl;

import com.sky.entity.Dish;
import com.sky.mapper.DishMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;          // 操作 MySQL

    @Autowired
    private RedisTemplate redisTemplate;    // 操作 Redis

    /**
     * 根据 ID 查询菜品（带缓存）
     *
     * 流程：
     * 1. 先查 Redis 缓存
     * 2. 缓存有 → 直接返回（快！）
     * 3. 缓存没有 → 查 MySQL → 放入缓存 → 返回
     */
    @Override
    public Dish getDishById(Long id) {
        // ========== 第 1 步：查 Redis 缓存 ==========
        String cacheKey = "dish:" + id;  // key 格式：dish:1, dish:2
        Dish dish = (Dish) redisTemplate.opsForValue().get(cacheKey);

        if (dish != null) {
            // 缓存命中！直接返回，不走数据库
            System.out.println("✅ 从 Redis 获取菜品：" + dish.getName());
            return dish;
        }

        // ========== 第 2 步：缓存没命中，查 MySQL ==========
        System.out.println("⚠️ Redis 无缓存，查询 MySQL...");
        dish = dishMapper.getById(id);

        if (dish != null) {
            // 把查到的数据放进 Redis，设置 30 分钟过期
            redisTemplate.opsForValue().set(cacheKey, dish, 30, TimeUnit.MINUTES);
            System.out.println("📝 已将菜品放入 Redis 缓存：" + dish.getName());
        }

        return dish;
    }

    /**
     * 更新菜品时，同步删除 Redis 缓存（防止脏数据）
     */
    @Override
    public void updateDish(Dish dish) {
        // 1. 更新 MySQL
        dishMapper.update(dish);

        // 2. 删除 Redis 缓存（下次查询时会重新加载最新数据）
        String cacheKey = "dish:" + dish.getId();
        redisTemplate.delete(cacheKey);

        System.out.println("🗑️ 已清除 Redis 缓存：" + cacheKey);
    }
}
```

### 9.2 运行流程图

```
第一次访问（缓存不存在）：
  请求 dish/1 → DishService → Redis ❌没有 → MySQL ✅有 → 放入 Redis → 返回
  耗时：~50ms

第二次访问（缓存存在）：
  请求 dish/1 → DishService → Redis ✅有 → 直接返回
  耗时：~1ms  （快了 50 倍！）
```

---

## 10. 常见问题与最佳实践

### 10.1 `@Autowired` 注入 RedisTemplate 时报错？

**原因**：Spring Boot 自动创建的 `RedisTemplate<Object, Object>` 和你写的 `RedisTemplate<String, Object>` 类型不匹配，加上 `@Resource` 注解或者别加泛型。

```java
// ✅ 方案一：不加泛型（和咱们配置类返回的类型一致）
@Autowired
private RedisTemplate redisTemplate;

// ✅ 方案二：用 @Resource（按名称注入，绕过类型检查）
@Resource
private RedisTemplate<String, Object> redisTemplate;

// ✅ 方案三：用 StringRedisTemplate（Spring Boot 自动提供的，key 和 value 都是 String）
@Autowired
private StringRedisTemplate stringRedisTemplate;
```

### 10.2 Key 的命名规范

```
❌ 不要这样：
   "key1", "test", "x", "dish1"

✅ 推荐这样（用冒号分隔层级）：
   "dish:1"          （菜品）
   "dish:all"        （所有菜品）
   "user:1001:info"  （用户信息）
   "user:1001:token" （用户 token）
   "order:2024:01"   （订单按年月）
```

好处：在 Redis 客户端工具中会按树形结构展示，一目了然。

### 10.3 一定要设置过期时间！

```java
// ❌ 危险：永久存在，内存迟早被撑爆
redisTemplate.opsForValue().set("key", value);

// ✅ 正确：设置过期时间
redisTemplate.opsForValue().set("key", value, 30, TimeUnit.MINUTES);
```

过期时间根据业务场景设置：
- 验证码：5 分钟
- 用户 token：2 小时
- 菜品缓存：30 分钟
- 配置信息：1 小时

### 10.4 缓存更新策略

| 策略 | 做法 | 适用场景 |
|------|------|----------|
| **删除缓存**（推荐） | 更新数据库 → 删除 Redis 缓存 → 下次查询自动加载 | 大多数场景 |
| **更新缓存** | 更新数据库 → 同时更新 Redis | 实时性要求高 |
| **过期淘汰** | 只设过期时间，不主动更新 | 数据一致性要求不高 |

### 10.5 `RedisTemplate` vs `StringRedisTemplate`

| 对比项 | RedisTemplate | StringRedisTemplate |
|--------|---------------|---------------------|
| 泛型 | `RedisTemplate<K, V>` | 固定 `String, String` |
| key 序列化 | 需要手动设置 | 自动 StringRedisSerializer |
| value 序列化 | 需要手动设置 | 自动 StringRedisSerializer |
| 适用场景 | 需要存 Java 对象 | 只存 String |

**简单场景（存验证码、token）直接用 `StringRedisTemplate`，不需要自己写配置类！**

---

## 11. 总结

### 整体架构图

```
┌──────────────────────────────────────────────────────┐
│                    你的业务代码                        │
│     @Autowired RedisTemplate                          │
│     redisTemplate.opsForValue().set("key", value)     │
└────────────────────┬─────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────┐
│              RedisTemplate（模板类）                    │
│  - 封装序列化/反序列化                                  │
│  - 封装连接管理                                        │
│  - 提供 5 种数据类型的操作接口                          │
└────────────────────┬─────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────┐
│         RedisConnectionFactory（连接工厂）              │
│  - 管理 Redis 连接                                     │
│  - 连接池                                             │
│  - 由 Spring Boot 根据 application.yml 自动创建        │
└────────────────────┬─────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────┐
│              Lettuce（底层 Redis 客户端）               │
│  - 真正的网络连接                                      │
│  - 发送 Redis 命令                                    │
└────────────────────┬─────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────┐
│               Redis 服务器                             │
│            localhost:6379                             │
└──────────────────────────────────────────────────────┘
```

### 关键点回顾

1. **`@Configuration`** → 声明配置类，Spring 会扫描它
2. **`@Bean`** → 声明方法返回值是一个 Bean，Spring 会管理它
3. **`RedisTemplate`** → 操作 Redis 的核心工具类，封装了一切繁琐操作
4. **`RedisConnectionFactory`** → 连接工厂，由 Spring Boot 自动创建，不需要你操心
5. **序列化器** → 决定数据在 Redis 里长什么样；key 一定要用 `StringRedisSerializer`
6. **Spring Boot 自动配置** → 引入依赖 + 写 `application.yml` 配置，Spring Boot 就帮你搞定 90% 的工作

### 学习路径建议

```
第 1 步：理解 @Configuration 和 @Bean（本文 ✓）
第 2 步：理解 RedisTemplate 的 5 种操作（本文 ✓）
第 3 步：在 Service 里做一个简单的缓存（实战）
第 4 步：学习 Spring Cache 注解（@Cacheable, @CacheEvict）
第 5 步：学习 Redis 的高级特性（事务、Pipeline、发布订阅）
```

---

> **本文档针对**：`sky-server/src/main/java/com/sky/config/RedisConfiguration.java`
> **创建日期**：2026-06-05
> **项目环境**：Spring Boot 2.7.3 + Spring Data Redis + Lettuce + MySQL
