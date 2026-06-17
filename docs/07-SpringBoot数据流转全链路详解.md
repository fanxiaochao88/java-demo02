# SpringBoot 数据流转全链路详解

> 面向 SpringBoot 新手，以"苍穹外卖"项目 `Category`（分类管理）模块为蓝本，一步步追踪：**前端 JSON → DTO → Service → Entity → Mapper XML 动态 SQL → 数据库 → 查询结果封装回对象 → 响应 JSON → 前端**。

---

## 1. 整体架构总览

先建立"谁住哪、谁能见谁"的边界：

```
 前端浏览器
    │  ↓ POST /admin/category  (JSON)
    │  ↓ {"type":1, "name":"川菜", "sort":1}
    ▼
┌──────────────────────────────────────────────────┐
│ Controller  (只和 DTO / VO 打交道)                  │
│   - 接收 JSON → @RequestBody 自动反序列化为 DTO      │
│   - 返回 VO → Spring MVC 自动序列化为 JSON           │
└──────────────────┬───────────────────────────────┘
                   │ 调用 Service，传 DTO
                   ▼
┌──────────────────────────────────────────────────┐
│ Service  (DTO → Entity 转换发生在这里)               │
│   - 把 DTO 拷贝/构建成 Entity                        │
│   - 把查询出的 Entity 拷贝/构建成 VO                   │
└──────────────────┬───────────────────────────────┘
                   │ 调用 Mapper，传 Entity/DTO
                   ▼
┌──────────────────────────────────────────────────┐
│ Mapper Interface + XML                            │
│   - 注解方式: @Select @Insert @Delete              │
│   - XML方式: 动态 SQL（<if> <where> <set>）         │
│   - 输入: Entity 或 散参数                          │
│   - 输出: Entity 自动映射（驼峰↔下划线自动转换）       │
└──────────────────┬───────────────────────────────┘
                   │  JDBC
                   ▼
┌──────────────────────────────────────────────────┐
│ MySQL 数据库                                       │
│   - 真实表结构 (字段名 = 下划线: create_time 等)       │
└──────────────────────────────────────────────────┘
```

**核心约定**：
- **Controller** 只认识 `DTO`（入参）和 `VO`（出参），不认识 `Entity`
- **Service** 负责 `DTO ↔ Entity`、`Entity ↔ VO` 的转换
- **Mapper** 只认识 `Entity`，不认识 `DTO/VO`
- **数据库字段** 是下划线命名（`create_time`），**Java 属性** 是驼峰命名（`createTime`），MyBatis 自动互转

---

## 2. 第一步：前端 JSON → Controller 接收为 DTO

### 2.1 前端发来的 JSON

```json
POST /admin/category
Content-Type: application/json

{
  "type": 1,
  "name": "川菜",
  "sort": 1
}
```

### 2.2 DTO 定义

```java
// sky-pojo/src/main/java/com/sky/dto/CategoryDTO.java
@Data
public class CategoryDTO implements Serializable {
    private Long id;
    private Integer type;    // 1菜品分类 2套餐分类
    private String name;     // 分类名称
    private Integer sort;    // 顺序
}
```

**为什么 DTO 和 Entity 是分开的？**
- DTO 只包含"前端需要传的"字段。比如新增时前端不传 `id`、`status`、`createTime`，DTO 里就不写。
- Entity 包含数据库表的所有字段。两者职责不同，分开有利于安全（防止前端伪造不该传的字段）。

### 2.3 Controller 如何接收

```java
// sky-server/src/main/java/com/sky/controller/admin/CategoryController.java
@RestController
@RequestMapping("/admin/category")
public class CategoryController {

    @PostMapping
    public Result save(@RequestBody CategoryDTO category) {  // ← 关键注解
        log.info("新增分类：{}", category);
        categoryService.save(category);
        return Result.success();
    }
}
```

**`@RequestBody` 做了什么？**

Spring MVC 的 `MappingJackson2HttpMessageConverter` 拦截到这个请求，调用 Jackson（你的项目配置了自定义的 `JacksonObjectMapper`）：

```
请求体 JSON 字符串
       │
       ▼
ObjectMapper.readValue(jsonString, CategoryDTO.class)
       │
       ▼
CategoryDTO 对象 { type=1, name="川菜", sort=1 }
```

**字段怎么对应的？** 靠 **字段名相同**：
- JSON `"type"` → DTO `type` 属性 → `setType(1)` 调用
- JSON `"name"` → DTO `name` 属性 → `setName("川菜")` 调用

这里走的是 **setter 方法**（Lombok `@Data` 自动生成了 getter/setter）。

### 2.4 你的项目特殊的 JSON 配置

```java
// sky-common/src/main/java/com/sky/json/JacksonObjectMapper.java
public class JacksonObjectMapper extends ObjectMapper {
    public JacksonObjectMapper() {
        // ① JSON 中有未知字段时不报错（兼容性）
        this.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

        // ② 注册 Java 8 时间类型的序列化/反序列化器
        //    LocalDateTime ↔ "yyyy-MM-dd HH:mm:ss"
        SimpleModule simpleModule = new SimpleModule()
            .addDeserializer(LocalDateTime.class, ...)
            .addSerializer(LocalDateTime.class, ...);
        this.registerModule(simpleModule);
    }
}
```

然后注册到 Spring MVC 的消息转换器：

```java
// sky-server/src/main/java/com/sky/config/WebMvcConfiguration.java
@Override
protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    MappingJackson2HttpMessageConverter converter =
        new MappingJackson2HttpMessageConverter();
    converter.setObjectMapper(new JacksonObjectMapper()); // 用自定义的
    converters.add(0, converter);  // 放到最前面，优先级最高
}
```

---

## 3. 第二步：Controller → Service → Mapper（DTO 转 Entity）

### 3.1 Service 接口（同样是 DTO）

```java
// sky-server/src/main/java/com/sky/service/CategoryService.java
public interface CategoryService {
    void save(CategoryDTO category);
    // ...
}
```

### 3.2 Service 实现：DTO → Entity 的转换

这是**最关键的一步**——DTO 需要转换成 Entity 才能传递给 Mapper：

```java
// sky-server/src/main/java/com/sky/service/impl/CategoryServiceImpl.java
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public void save(CategoryDTO category) {
        // ★ 方式一：Builder 模式手动构建 Entity
        Category c = Category.builder()
                .type(category.getType())    // Integer → Integer
                .name(category.getName())    // String → String
                .sort(category.getSort())    // Integer → Integer
                .status(0)                   // DTO 没有 status，Service 自己设默认值
                .build();
        // createTime、updateTime、createUser、updateUser
        // 这些由 AOP 切面 AutoFillAspect 自动填充 ↓

        categoryMapper.insert(c);
    }
}
```

**DTO 和 Entity 的字段对比**：

| CategoryDTO | Category (Entity) | 来源 |
|-------------|-------------------|------|
| `type` | `type` | 前端传的 |
| `name` | `name` | 前端传的 |
| `sort` | `sort` | 前端传的 |
| ❌ 没有 | `id` | 数据库自增 |
| ❌ 没有 | `status` | Service 设默认值 `0` |
| ❌ 没有 | `createTime` | AOP 自动填充当前时间 |
| ❌ 没有 | `updateTime` | AOP 自动填充当前时间 |
| ❌ 没有 | `createUser` | AOP 从 ThreadLocal 取当前用户ID |
| ❌ 没有 | `updateUser` | AOP 从 ThreadLocal 取当前用户ID |

**另一种转换方式：BeanUtils.copyProperties**

```java
// 修改分类时的方式（字段相似度高时用）
@Override
public void update(CategoryDTO category) {
    // ★ 方式二：属性拷贝
    Category c = new Category();
    BeanUtils.copyProperties(category, c);
    // 同名字段自动拷贝：id→id, type→type, name→name, sort→sort
    // 不同名字段不会拷贝，需要手动补充
    categoryMapper.update(c);
}
```

| 方式 | 适用场景 |
|------|---------|
| `Builder` 手动构建 | 字段差异大，需要精确控制 |
| `BeanUtils.copyProperties` | 字段高度重合，同名字段自动拷贝 |

### 3.3 Mapper 接口

```java
// sky-server/src/main/java/com/sky/mapper/CategoryMapper.java
@Mapper
public interface CategoryMapper {

    // 注解方式：简单 SQL
    @Insert("insert into category (type, name, sort, create_time, " +
            "update_time, create_user, update_user, status) " +
            "values (#{type}, #{name}, #{sort}, #{createTime}, " +
            "#{updateTime}, #{createUser}, #{updateUser}, #{status})")
    void insert(Category c);

    // XML方式：复杂动态 SQL
    Page<Category> pageQuery(CategoryPageQueryDTO pageDto);

    void update(Category c);   // 动态更新，XML 实现
}
```

---

## 4. 第三步：Mapper XML 动态 SQL 组装（核心）

这是你最关心的问题——**XML 里的变量怎么和 Java 对象对应，条件怎么动态拼接**。

### 4.1 动态更新：`<set>` + `<if>`

```xml
<!-- sky-server/src/main/resources/mapper/CategoryMapper.xml -->

<mapper namespace="com.sky.mapper.CategoryMapper">   <!-- 绑定到接口 -->

    <!-- update(Category c) 的 XML 实现 -->
    <update id="update">                             <!-- id = 方法名 -->
        update category
        <set>
            <if test="name != null">name = #{name},</if>
            <if test="type != null">type = #{type},</if>
            <if test="status != null">status = #{status},</if>
            <if test="sort != null">sort = #{sort},</if>
            <if test="updateTime != null">update_time = #{updateTime},</if>
            <if test="updateUser != null">update_user = #{updateUser}</if>
        </set>
        where id = #{id}
    </update>
</mapper>
```

**假设调用 `categoryMapper.update(c)`，传入的 Entity 只有 `id=5, status=1`：**

MyBatis 实际生成的 SQL：

```sql
update category
SET status = ?        -- 只拼接了 status!=null 的字段
where id = ?
-- 参数: status=1, id=5
```

**`<set>` 标签的智能之处**：
- 自动处理逗号问题——如果最后一条被 `<if>` 跳过了，会去掉多余的逗号
- 如果所有 `<if>` 都不满足（所有字段都是 null），`SET` 关键字直接不生成

### 4.2 动态查询：`<where>` + `<if>` + 模糊搜索

```xml
<select id="pageQuery" resultType="com.sky.entity.Category">   <!-- 返回类型 -->
    select * from category
    <where>
        <if test="name != null and name != ''">
            name like concat('%', #{name}, '%')     <!-- 模糊搜索 -->
        </if>
        <if test="type != null">
            and type = #{type}
        </if>
    </where>
    order by sort asc
</select>
```

**假设分页查询传入 `name="川", type=null`**：

生成的 SQL：

```sql
select * from category
WHERE name like concat('%', ?, '%')   -- type 跳过，and 被 <where> 自动吃掉
order by sort asc
-- 参数: "川"
```

**`<where>` 标签的智能之处**：
- 如果所有条件都不满足，`WHERE` 关键字直接不生成（不会出现 `SELECT * FROM category WHERE` 没条件）
- 如果第一个条件满足、第二个跳过，自动把第二个的 `and` 吃掉
- `test` 里面可以直接写 `name != null and name != ''`，这是 OGNL 表达式

### 4.3 `#{}` vs `${}` 的关键区别

| 写法 | 含义 | 安全性 |
|------|------|--------|
| `#{name}` | **预编译占位符** `?` → `PreparedStatement` | ✅ 防 SQL 注入 |
| `${name}` | **直接字符串拼接** | ❌ 可能 SQL 注入 |

```xml
<!-- ✅ 正确：值用 #{} -->
where name = #{name}

<!-- ❌ 危险：用户输入直接拼 SQL -->
where name = '${name}'
```

**什么时候必须用 `${}`？** 排序字段、表名等非值的地方：

```xml
order by ${sortColumn} ${sortDirection}
```

但你的项目里 `order by sort asc` 是写死的，所以全用 `#{}` 没问题。

### 4.4 字段映射：Java 驼峰 ↔ 数据库下划线

这是**自动的**，不需要手动配置。

```java
// Entity 属性 (Java 驼峰)
private LocalDateTime createTime;
```

```sql
-- 数据库字段 (下划线)
create_time
```

MyBatis 全局配置 (`mybatis.configuration.map-underscore-to-camel-case=true`) 会自动：
- `create_time` → `createTime`
- `create_user` → `createUser`
- `id_number` → `idNumber`

你的项目在 `application.yml` 中肯定有这个配置：

```yaml
mybatis:
  configuration:
    map-underscore-to-camel-case: true
```

**不需要在 XML 中写 `resultMap` 就能自动映射**，因为 `resultType="com.sky.entity.Category"` 指定了目标类型，MyBatis 会自动按规则转换。

---

## 5. 第四步：查询结果封装回对象

### 5.1 简单查询：自动映射

```java
// Mapper 注解方式
@Select("select * from category where type = #{type} order by sort asc")
List<Category> list(Integer type);
```

**全过程**：

```
1. MyBatis 执行 SELECT * FROM category WHERE type = ? ORDER BY sort ASC
2. JDBC ResultSet 返回:
   ┌────┬──────┬───────┬──────┬────────┬───────────┬──────────────┬──────────────┬─────────────┬─────────────┐
   │ id │ type │ name  │ sort │ status │ create_time │ update_time  │ create_user  │ update_user │
   ├────┼──────┼───────┼──────┼────────┼───────────┼──────────────┼──────────────┼─────────────┼─────────────┤
   │ 1  │ 1    │ 川菜  │ 1    │ 1      │ 2024-...   │ 2024-...     │ 10           │ 10          │
   │ 2  │ 1    │ 粤菜  │ 2    │ 1      │ 2024-...   │ 2024-...     │ 10           │ 10          │
   └────┴──────┴───────┴──────┴────────┴───────────┴──────────────┴──────────────┴─────────────┴─────────────┘

3. MyBatis 逐行反射创建 Category 对象:
   第1行 → Category{id=1, type=1, name="川菜", sort=1, status=1,
                    createTime=LocalDateTime("2024-..."), ...}
   第2行 → Category{id=2, type=1, name="粤菜", sort=2, status=1,
                    createTime=LocalDateTime("2024-..."), ...}

4. 装入 List<Category> 返回
```

**映射的关键**：
- MyBatis 从 ResultSet 拿到列名 `create_time`
- 转成驼峰 `createTime`
- 反射调用 `Category.setCreateTime(value)`
- 全部字段设置完毕，一个 Entity 对象就构建好了

### 5.2 分页查询：PageHelper 分页插件

```java
// CategoryServiceImpl.pageQuery()
@Override
public PageResult pageQuery(CategoryPageQueryDTO pageDto) {
    PageHelper.startPage(pageDto.getPage(), pageDto.getPageSize());  // ① 启动分页
    Page<Category> page = categoryMapper.pageQuery(pageDto);         // ② 执行查询
    long total = page.getTotal();          // 总记录数
    List<Category> result = page.getResult(); // 当前页数据
    return new PageResult(total, result);  // ③ 封装分页结果
}
```

**PageHelper 做了什么？**
- 拦截你的 SQL `SELECT * FROM category WHERE ...`
- 自动追加 `LIMIT ? OFFSET ?`
- 同时执行一条 `SELECT COUNT(*) FROM category WHERE ...` 拿总数

---

## 6. 第五步：Entity → VO → JSON → 前端

### 6.1 统一返回格式

```java
// sky-common/src/main/java/com/sky/result/Result.java
@Data
public class Result<T> {
    private Integer code;  // 1=成功, 0=失败
    private String msg;    // 错误消息
    private T data;        // 实际数据（泛型）
}
```

Controller 返回 `Result.success(pageResult)` 时：
- `code = 1`
- `data = PageResult{total=25, records=[Category{...}, Category{...}]}`

### 6.2 返回 Entity 还是 VO？

**简单场景**（你的 Category 模块）直接返回 Entity：

```java
@GetMapping("/list")
public Result<List<Category>> list(Integer type) {
    List<Category> list = categoryService.list(type);
    return Result.success(list);
}
```

**复杂场景**（登录）需要转换 Entity → VO：

```java
// EmployeeController.login()
@PostMapping("/login")
public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
    Employee employee = employeeService.login(employeeLoginDTO);

    // ★ Entity → VO 的转换
    EmployeeLoginVO vo = EmployeeLoginVO.builder()
            .id(employee.getId())
            .name(employee.getName())
            .userName(employee.getUsername())   // 注意: Entity.username → VO.userName
            .token(token)
            .build();

    return Result.success(vo);   // 返回 VO，不返回 Entity（密码等敏感字段不暴露）
}
```

**为什么登录不直接返回 Entity？**
- Entity 的 `Employee` 有 `password` 字段——绝不能返回到前端
- VO 的 `EmployeeLoginVO` 只有 `id`、`userName`、`name`、`token`——安全

### 6.3 Java 对象 → JSON 字符串

Spring MVC 的 `MappingJackson2HttpMessageConverter` 再次介入：

```java
// Jackson 自动调用:
ObjectMapper.writeValueAsString(resultObject)
```

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "total": 25,
    "records": [
      {
        "id": 1,
        "type": 1,
        "name": "川菜",
        "sort": 1,
        "status": 1,
        "createTime": "2024-01-15 10:30:00",
        "updateTime": "2024-01-15 10:30:00",
        "createUser": 10,
        "updateUser": 10
      }
    ]
  }
}
```

**时间格式化**：你的 `JacksonObjectMapper` 配置了 `LocalDateTime → "yyyy-MM-dd HH:mm:ss"`，所以 `createTime` 不会输出 Java 默认的数组格式。

---

## 7. 完整链路串联

以 **"新增分类"** 为例，从头到尾走一遍：

```
                    【前端】
  POST /admin/category
  Body: {"type": 1, "name": "川菜", "sort": 1}
                         │
        ┌────────────────┼────────────────┐
        │  1. JSON 反序列化 (Jackson)        │
        │  "type" → setType(1)             │
        │  "name" → setName("川菜")         │
        │  "sort" → setSort(1)             │
        └────────────────┼────────────────┘
                         ▼
               CategoryDTO {
                 type=1, name="川菜", sort=1
               }
                         │
        ┌────────────────┼────────────────┐
        │  2. Service: DTO → Entity        │
        │  Builder 手动构建                  │
        │  + status 设为默认值 0             │
        │  + AOP 填充 createTime 等         │
        └────────────────┼────────────────┘
                         ▼
               Category {
                 id=null, type=1, name="川菜",
                 sort=1, status=0,
                 createTime=2024-...,
                 createUser=10, ...
               }
                         │
        ┌────────────────┼────────────────┐
        │  3. Mapper: 注解SQL               │
        │  #{type} → ? → 1                │
        │  #{name} → ? → "川菜"            │
        │  #{createTime} → ? → 2024-...   │
        │  驼峰 createTime 自动映射到        │
        │  数据库字段 create_time            │
        └────────────────┼────────────────┘
                         ▼
              INSERT INTO category
              (type, name, sort, create_time, ...)
              VALUES (1, '川菜', 1, '2024-...', ...)
                         │
        ┌────────────────┼────────────────┐
        │  4. MySQL 执行，返回自增ID         │
        └────────────────┼────────────────┘
                         ▼
              返回 Result { code: 1 }
                         │
        ┌────────────────┼────────────────┐
        │  5. Result → JSON 序列化 (Jackson) │
        │  code=1 → "code": 1             │
        │  data=null → 不输出              │
        └────────────────┼────────────────┘
                         ▼
                    【前端】
              {"code": 1, "msg": null}
```

---

## 8. 核心机制总结

### 8.1 各层之间如何保证字段正确对应

| 转换环节 | 靠什么 | 示例 |
|----------|--------|------|
| JSON → DTO | **属性名相同** + setter | `"name"` → `setName()` |
| DTO → Entity | Service 手动 **Builder / BeanUtils** | `category.getName()` → `entity.setName()` |
| Entity → SQL 参数 | MyBatis `#{}` + **属性名** → 数据库字段名（驼峰转下划线） | `#{createTime}` → `create_time` 列的 `?` |
| SQL 结果 → Entity | MyBatis + **列名转驼峰** + setter | `create_time` 列 → `setCreateTime()` |
| Entity → JSON | Jackson + **getter** → 属性名 | `getCreateTime()` → `"createTime"` |

### 8.2 为什么你感觉混乱？——数据类型对照表

| 层 | 对象类型 | 命名风格 | 包路径 |
|----|---------|---------|--------|
| 前端 | JSON | 驼峰 (camelCase) | - |
| Controller 入参 | DTO | 驼峰 | `com.sky.dto` |
| Controller 出参 | VO / Entity | 驼峰 | `com.sky.vo` / `com.sky.entity` |
| Service 方法参数 | DTO | 驼峰 | `com.sky.dto` |
| Service 方法返回值 | Entity / VO / PageResult | 驼峰 | `com.sky.entity` / `com.sky.vo` |
| Service→Mapper 传递 | Entity / DTO | 驼峰（Java侧） | `com.sky.entity` / `com.sky.dto` |
| Mapper XML `test` 属性 | **OGNL** 表达式 | 驼峰（Java属性名） | - |
| Mapper XML `#{}` | 占位符，引用**方法参数的属性** | 驼峰（Java属性名） | - |
| 数据库 | 表字段 | 下划线 (snake_case) | - |

### 8.3 新手常见踩坑点

**坑1：JSON 字段名和 DTO 属性名对不上**

```java
// 前端发: {"type_name": 1}
// DTO: private Integer type;
// ❌ 默认不匹配！需要 @JsonProperty("type_name") 或让前端改
```

**坑2：XML 里 `#{ }` 写成了数据库字段名**

```xml
<!-- ❌ 错误：用了数据库字段名 -->
values (#{type}, #{name}, #{create_time})

<!-- ✅ 正确：用 Java 对象属性名 -->
values (#{type}, #{name}, #{createTime})
```

**规则**：`#{}`、`test`、`resultType` 里永远用 **Java 属性名（驼峰）**，不管数据库叫什么。

**坑3：忘记下划线转驼峰配置**

```yaml
# 如果没有这个配置，查询结果里的 create_time 不会自动映射到 createTime
mybatis:
  configuration:
    map-underscore-to-camel-case: true
```

**坑4：返回 Entity 暴露了敏感字段**

```java
// ❌ 登录接口返回 Employee（包含 password）
// ✅ 返回 EmployeeLoginVO（只有 id, userName, name, token）
```

---

## 9. EmployeeMapper 混合模式补充

你的项目里 Employee 模块还展示了**注解 + XML 混合使用**的模式：

```java
// 简单 SQL → 注解（一目了然）
@Select("select * from employee where username = #{username}")
Employee getByUsername(String username);

@Insert("insert into employee (...) values (#{name}, ...)")
void insert(Employee employee);

// 动态 SQL → XML（注解里写不了 <if> 判断）
Page<Employee> pageQuery(EmployeePageQueryDTO dto);  // XML 实现
void update(Employee employee);                       // XML 实现
```

**原则**：简单的用注解，需要 `<if>` `<where>` `<set>` `<foreach>` 动态 SQL 的用 XML。

---

## 10. 一句话总结

```
前端 JSON → (Jackson反序列化, 靠属性名) → DTO
  → (Service手动构建/BeanUtils拷贝) → Entity
    → (MyBatis #{属性名}, 自动转下划线) → SQL ? 占位参数
      → (MySQL 执行) → ResultSet 下划线列名
        → (MyBatis自动转驼峰+反射setter) → Entity
          → (Service构建/拷贝) → VO
            → (Jackson序列化, 靠getter) → 前端 JSON
```

**记住三个"自动"**：
1. JSON ↔ Java 对象：Jackson 自动，**靠属性名匹配**，走 getter/setter
2. Java 属性名 ↔ 数据库字段名：MyBatis 自动驼峰转下划线，**全局配置开关**
3. 数据库列值 → Java 属性值：MyBatis 自动反射调用 setter，**resultType 指定类即可**

**记住一个"手动"**：
- DTO ↔ Entity ↔ VO 的转换：**需要你自己在 Service 层手动写**（Builder 或 BeanUtils）

这就是全链路。每一环都只有一种转换规则，理清之后就不会再觉得混乱了。
