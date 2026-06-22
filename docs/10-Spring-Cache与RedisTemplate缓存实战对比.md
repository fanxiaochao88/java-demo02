# Spring Cache 注解 vs RedisTemplate 手动缓存 —— 企业级缓存实战对比

> **适用对象**：已掌握 Redis 基础，正在做实际项目的开发者
> **核心问题**：缓存到底用 Spring Cache 注解（`@Cacheable`）还是手动 `RedisTemplate` 一把梭？
> **阅读收获**：通过项目真实代码对比，理解两种方式的优劣和适用场景

---

## 目录

1. [项目中两套缓存代码对比](#1-项目中两套缓存代码对比)
2. [两种方式的本质区别](#2-两种方式的本质区别)
3. [企业级最佳实践](#3-企业级最佳实践)
4. [常见坑与解决方案](#4-常见坑与解决方案)
5. [总结：什么时候用哪个](#5-总结什么时候用哪个)

---

## 1. 项目中两套缓存代码对比

### 1.1 背景

本项目是一个外卖系统（苍穹外卖），有两类核心数据需要缓存：

| 数据 | 缓存方式 | 代码位置 |
|------|----------|----------|
| **菜品列表** (Dish) | 手动 `RedisTemplate` | `user/DishController`、`admin/DishController` |
| **套餐列表** (Setmeal) | Spring Cache 注解 | `user/SetmealController`、`admin/SetmealController` |

两种方式都正确运行，但写法完全不同的方式——这就是最好的教学素材。

### 1.2 手动 RedisTemplate 方式（菜品）

**读缓存** —— `user/DishController.java`：
```java
@RestController("userDishController")
@RequestMapping("/user/dish")
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;  // 注入 RedisTemplate

    @RequestMapping("/list")
    public Result<List<DishVO>> list(@RequestParam Long categoryId) {

        // 步骤1：手动构造 redis key
        String key = "dish_" + categoryId;

        // 步骤2：手动查询 redis
        List<DishVO> cache = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (cache != null && cache.size() > 0) {
            return Result.success(cache);          // 命中缓存，直接返回
        }

        // 步骤3：手动查数据库
        List<DishVO> list = dishService.list(categoryId);

        // 步骤4：手动写入 redis
        redisTemplate.opsForValue().set(key, list);

        return Result.success(list);
    }
}
```

**写操作清除缓存** —— `admin/DishController.java`：
```java
@PostMapping
public Result save(@RequestBody DishDTO dishDTO) {
    dishService.save(dishDTO);
    clearCache("dish_" + dishDTO.getCategoryId()); // 手动按 key 清理
    return Result.success();
}

@DeleteMapping
public Result delete(@RequestParam List<Long> ids) {
    dishService.deleteBatch(ids);
    clearCache("dish_*");   // 手动模糊匹配清理
    return Result.success();
}

// 封装的清理方法：先查出匹配的 key，再批量删除
private void clearCache(String pattern) {
    Set keys = redisTemplate.keys(pattern);
    redisTemplate.delete(keys);
}
```

### 1.3 Spring Cache 注解方式（套餐）

**读缓存** —— `user/SetmealController.java`：
```java
@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;
    // 不需要注入 RedisTemplate！

    @RequestMapping("/list")
    @Cacheable(value = "setmealCache", key = "#categoryId")
    public Result<List<SetmealVO>> list(@RequestParam Long categoryId) {
        return Result.success(setmealService.list(categoryId));
    }
}
```

**写操作自动清除缓存** —— `admin/SetmealController.java`：
```java
@PostMapping
@CacheEvict(cacheNames = "setmealCache", allEntries = true)
public Result save(@RequestBody SetmealDTO setmealDTO) {
    setmealService.save(setmealDTO);
    return Result.success();
}

@DeleteMapping
@CacheEvict(cacheNames = "setmealCache", allEntries = true)
public Result delete(@RequestParam List<Long> ids) {
    setmealService.delete(ids);
    return Result.success();
}

@PostMapping("/status/{status}")
@CacheEvict(cacheNames = "setmealCache", allEntries = true)
public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
    setmealService.startOrStop(status, id);
    return Result.success();
}
```

### 1.4 启动类必须开启缓存

```java
@SpringBootApplication
@EnableCaching   // 必须加这个，@Cacheable 才会生效
public class SkyApplication { ... }
```

---

## 2. 两种方式的本质区别

### 2.1 对比总表

| 维度 | 手动 RedisTemplate | Spring Cache 注解 |
|------|-------------------|-------------------|
| **代码侵入性** | 高。缓存逻辑和业务逻辑混在一起 | 低。注解声明式，业务逻辑干净 |
| **灵活度** | **极高**。可以自定义 TTL、序列化、淘汰策略 | 较低。依赖全局 CacheManager 配置 |
| **缓存 Key 控制** | 完全自由 | 靠 SpEL 表达式，复杂场景受限 |
| **缓存条件** | 完全自由（`if` 判断） | `condition` / `unless` 属性 |
| **缓存过期时间** | `redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES)` | 全局配置（除非配多个 CacheManager） |
| **代码量** | 多 | 少 |
| **可读性** | 需要一行行理解缓存逻辑 | 注解一眼看出"这个方法会缓存" |
| **单元测试** | 需要 mock `RedisTemplate` | 只需 mock 业务方法本身 |
| **切换缓存方案** | 紧耦合 Redis | 换 CacheManager 即可从 Redis 切到 Caffeine/Guava |

### 2.2 核心原理

**手动 RedisTemplate 的工作原理：**

```
请求进来
  → 你手动构造 key
  → 你手动调 redisTemplate.opsForValue().get(key)
  → 如果命中则直接返回
  → 如果未命中则调 service 查数据库
  → 你手动调 redisTemplate.opsForValue().set(key, data)
  → 返回数据
```

**Spring Cache 注解的工作原理：**

```
请求进来
  → Spring AOP 拦截 @Cacheable 方法
  → 通过 SpEL 解析 key（如 #categoryId → 1）
  → 自动调 CacheManager 查缓存（底层还是 RedisTemplate）
  → 如果命中则直接返回（你的方法体根本不执行）
  → 如果未命中则执行你的方法体
  → 自动将返回值存入缓存
  → 返回数据
```

**关键区别：** 注解方式下，你的方法体不会执行——缓存命中的返回值是 Spring 帮你存的。

### 2.3 对代码的影响

用注解方式，你的 Controller 代码就是"纯业务"的：

```java
// 你写的代码里只有业务，缓存逻辑完全在注解里
@Cacheable(value = "setmealCache", key = "#categoryId")
public Result<List<SetmealVO>> list(@RequestParam Long categoryId) {
    return Result.success(setmealService.list(categoryId)); // 查就完了
}
```

用手动方式，缓存逻辑和业务逻辑是交织的：

```java
public Result<List<DishVO>> list(@RequestParam Long categoryId) {
    // 缓存查询（非业务）
    String key = "dish_" + categoryId;
    List<DishVO> cache = (List<DishVO>) redisTemplate.opsForValue().get(key);
    if (cache != null && cache.size() > 0) {
        return Result.success(cache);
    }
    // 业务查询
    List<DishVO> list = dishService.list(categoryId);
    // 缓存写入（非业务）
    redisTemplate.opsForValue().set(key, list);
    return Result.success(list);
}
```

---

## 3. 企业级最佳实践

### 3.1 推荐做法：**注解为主，手动为辅**

在实际企业项目中，一个合理的策略是：

```
查询类接口  →  @Cacheable
写入类接口  →  @CacheEvict
特殊需求     →  手动 RedisTemplate
```

**为什么？**

| 原因 | 解释 |
|------|------|
| **关注点分离** | 业务代码不应该关心"数据从哪来"、"缓存怎么清" |
| **不易出错** | 手动方式最容易忘的事：写完数据忘了清缓存、查数据忘了加缓存判断 |
| **团队协作** | 新人看注解代码一眼就懂，看手动代码需要理解"先判断后写入"的模式 |
| **可演进** | 将来想从 Redis 切到本地缓存（Caffeine），注解方式只需改配置 |

### 3.2 什么情况必须用手动方式

以下场景注解搞不定，只能手动：

**场景一：需要给不同业务设置不同过期时间**

```java
// 注解做不到：菜品列表缓存 30 分钟，验证码缓存 5 分钟
// 除非配两个独立的 CacheManager（很重）

// 手动方式轻松搞定：
redisTemplate.opsForValue().set("dish_" + id, dishList, 30, TimeUnit.MINUTES);
redisTemplate.opsForValue().set("sms_" + phone, code, 5, TimeUnit.MINUTES);
```

**场景二：批量预热缓存**

```java
// 项目启动时，把热门分类的数据全部加载到 Redis
@PostConstruct
public void preloadHotData() {
    List<Category> hotCategories = categoryService.listHot();
    for (Category c : hotCategories) {
        String key = "dish_" + c.getId();
        List<DishVO> dishes = dishService.list(c.getId());
        redisTemplate.opsForValue().set(key, dishes, 1, TimeUnit.HOURS);
    }
}
```

**场景三：缓存穿透保护（存空值）**

```java
public List<DishVO> listWithNullProtection(Long categoryId) {
    String key = "dish_" + categoryId;
    List<DishVO> cache = (List<DishVO>) redisTemplate.opsForValue().get(key);
    if (cache != null) {
        return cache.isEmpty() ? null : cache;   // 空集合表示"确实没数据"
    }
    List<DishVO> list = dishService.list(categoryId);
    if (list.isEmpty()) {
        // 缓存空值，5 分钟后过期，防止穿透
        redisTemplate.opsForValue().set(key, Collections.emptyList(), 5, TimeUnit.MINUTES);
    } else {
        redisTemplate.opsForValue().set(key, list, 30, TimeUnit.MINUTES);
    }
    return list;
}
```

**场景四：需要精确清除某个 key**

```java
// Spring Cache 通常用 allEntries=true 清空整个 cacheName
// 但如果你只想清除特定分类的缓存：

// 注解做不到（key 属性在 CacheEvict 中只能用于指定单个 key，不能模糊匹配）
// 手动方式：
redisTemplate.delete("dish_" + categoryId);
```

### 3.3 改造建议：统一为注解 + 手动辅助

如果你想让菜品缓存也改成注解方式（与套餐统一），改动如下：

```java
// === user/DishController.java ===

@RestController("userDishController")
@RequestMapping("/user/dish")
public class DishController {

    @Autowired
    private DishService dishService;
    // 删除：@Autowired private RedisTemplate redisTemplate;

    @RequestMapping("/list")
    @Cacheable(value = "dishCache", key = "#categoryId")  // 加这一行
    public Result<List<DishVO>> list(@RequestParam Long categoryId) {
        // 删除所有缓存逻辑
        return Result.success(dishService.list(categoryId));  // 只剩一行业务代码
    }
}

// === admin/DishController.java ===

@PostMapping
@CacheEvict(cacheNames = "dishCache", key = "#dishDTO.categoryId")  // 精确清除
public Result save(@RequestBody DishDTO dishDTO) {
    dishService.save(dishDTO);
    return Result.success();
}

@DeleteMapping
@CacheEvict(cacheNames = "dishCache", allEntries = true)  // 全部清除（因为批量删除涉及多个分类）
public Result delete(@RequestParam List<Long> ids) {
    dishService.deleteBatch(ids);
    return Result.success();
}
```

### 3.4 生产级配置

无论用哪种方式，生产环境都需要配置序列化和 CacheManager：

```java
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置 Spring Cache 使用的 Redis CacheManager
     * 这个 Bean 让 @Cacheable 知道"把数据存到 Redis 而不是内存"
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            // 设置默认过期时间：30 分钟
            .entryTtl(Duration.ofMinutes(30))
            // 禁止缓存 null 值（防止缓存穿透可以改这里）
            .disableCachingNullValues()
            // key 序列化（让 redis 中的 key 可读）
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
            )
            // value 序列化（用 JSON 存，支持反序列化为 Java 对象）
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            // 可以为不同的 cacheName 设置不同的 TTL
            .withCacheConfiguration("dishCache",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("setmealCache",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(30)))
            .build();
    }
}
```

> **注意**：项目当前没有配置 `RedisCacheManager`，Spring Boot 会用默认的 `ConcurrentMapCacheManager`（内存缓存），而非 Redis。如果想用 `@Cacheable` + Redis，必须配置上面这个 Bean。

---

## 4. 常见坑与解决方案

### 坑一：`@Cacheable` 在同类方法调用时失效

```java
@Service
public class DishService {

    // 从这里调用 getById，缓存注解不生效！
    public List<DishVO> getListFromDB(Long id) {
        return this.getById(id);  // ❌ 不经过 AOP 代理
    }

    @Cacheable(value = "dish", key = "#id")
    public DishVO getById(Long id) {
        return dishMapper.selectById(id);
    }
}
```

**原因**：Spring AOP 基于代理，同类方法内部调用不经过代理，注解不生效。

**解决**：把缓存方法放到独立的 Service 中，或用 `ApplicationContext` 获取代理对象调用。

### 坑二：`RedisTemplate` 默认序列化导致 Redis 里看到乱码

```
# 用了默认的序列化器
keys *dish*
  1) "\xac\xed\x00\x05t\x00\x05dish_1"    # 乱码！
```

**原因**：`RedisTemplate` 默认使用 JDK 序列化。

**解决**：项目中 `RedisConfiguration.java` 已经配置了 `StringRedisSerializer`，确保 key 可读。如果值也出现乱码，需要同样设置：
```java
redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
```

### 坑三：`@CacheEvict(allEntries = true)` 范围过大

```java
// 修改了一个套餐，却清空了 setmealCache 中的所有缓存
@CacheEvict(cacheNames = "setmealCache", allEntries = true)
public Result update(@RequestBody SetmealDTO setmealDTO) { ... }
```

**影响**：所有分类下的套餐查询都要重新查库，缓存雪崩。

**改进**：如果能确定影响范围，精准清除：
```java
@CacheEvict(cacheNames = "setmealCache", key = "#setmealDTO.categoryId")
```

> 但如果一个套餐修改可能影响多个分类（如菜品在多个分类下出现），`allEntries = true` 是更安全的选择。

### 坑四：缓存一致性问题

**场景**：数据变了，缓存没清。

```java
// ❌ 这样写会导致数据库更新了，缓存（如果有的话）还是旧的
@PutMapping
public Result update(@RequestBody DishDTO dishDTO) {
    dishService.update(dishDTO);
    return Result.success();  // 没清缓存！
}
```

**铁律**：**所有写操作都要清缓存**。用注解则加 `@CacheEvict`，用手动则调 `redisTemplate.delete()`。

---

## 5. 总结：什么时候用哪个

```
                    你的缓存场景是怎样的？
                          │
          ┌───────────────┼───────────────┐
          │               │               │
    简单的 CRUD      需要不同 TTL    复杂的缓存逻辑
    缓存查询         批量预热       缓存穿透保护
          │               │               │
          ▼               ▼               ▼
     用 @Cacheable    手动 RedisTemplate 就够了
     + @CacheEvict
```

| 场景 | 推荐方式 |
|------|----------|
| 按 ID/分类 查列表、查详情 | **`@Cacheable`** |
| 新增、修改、删除时清缓存 | **`@CacheEvict`** |
| 不同数据需要不同过期时间 | 手动 `RedisTemplate`（或配多个 CacheManager） |
| 项目启动时预热缓存 | 手动 `RedisTemplate` |
| 需要防缓存穿透（存空值） | 手动 `RedisTemplate` |
| 缓存实时性要求极高、需精细控制 | 手动 `RedisTemplate` |
| 团队不熟悉 Redis 底层 | **注解方式**（学习成本低） |

**一句话总结**：

> Spring Cache 注解是**95% 场景**的最佳选择——代码干净、不易出错、团队上手快。
> 手动 RedisTemplate 留给那 **5%** 的复杂场景——需要不同过期时间、防穿透、批量预热等。
> 企业级项目的正确做法是**两者混用，以注解为主**。

---

*本教程基于苍穹外卖项目 (`sky-take-out`) 的真实代码编写，相关文件：*
- `sky-server/src/main/java/com/sky/controller/user/DishController.java`
- `sky-server/src/main/java/com/sky/controller/admin/DishController.java`
- `sky-server/src/main/java/com/sky/controller/user/SetmealController.java`
- `sky-server/src/main/java/com/sky/controller/admin/SetmealController.java`
- `sky-server/src/main/java/com/sky/config/RedisConfiguration.java`
- `sky-server/src/main/java/com/sky/SkyApplication.java`
