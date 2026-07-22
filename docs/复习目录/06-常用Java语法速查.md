# 06 - 常用 Java 语法速查

## 1. 泛型

项目里最典型的是 `Result<T>`：

```java
public class Result<T> implements Serializable {
    private T data;
}
```

### 你要记住

- `T` 是占位符
- 用泛型可以让返回值更灵活
- 避免频繁强转

## 2. 枚举

```java
public enum OperationType {
    UPDATE,
    INSERT
}
```

### 常见用途

- 表示固定状态
- 比字符串更安全
- 比数字更可读

## 3. 自定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    OperationType value();
}
```

### 两个最关键的元注解

- `@Target`：注解可以放在哪里
- `@Retention`：注解活到什么时候

## 4. Lombok

项目里大量使用：

- `@Data`
- `@Builder`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@Slf4j`

### 典型写法

```java
@Data
@Builder
public class EmployeeLoginVO {
    private Long id;
    private String userName;
    private String token;
}
```

## 5. 集合、Lambda、Stream

### 集合转 Map

```java
Map<LocalDate, BigDecimal> map = list.stream()
        .collect(Collectors.toMap(TurnoverReportDTO::getLocalDate, TurnoverReportDTO::getTurnover));
```

### 集合转字符串

```java
String joined = dateList.stream()
        .map(LocalDate::toString)
        .collect(Collectors.joining(","));
```

### 常见用途

- 分组聚合
- 补齐日期
- 提取字段
- 拼接前端图表数据

## 6. Java 时间 API

项目里经常用：

- `LocalDate`
- `LocalTime`
- `LocalDateTime`
- `DateTimeFormatter`

### 常见写法

```java
LocalDateTime start = LocalDateTime.of(begin, LocalTime.MIN);
LocalDateTime end = LocalDateTime.of(endDate, LocalTime.MAX);
```

## 7. 反射

```java
Method method = entry.getClass().getDeclaredMethod("setUpdateTime", LocalDateTime.class);
method.invoke(entry, LocalDateTime.now());
```

### 语法点

- `getDeclaredMethod`：找方法
- `invoke`：调用方法
- 反射适合做通用框架能力，不适合业务里到处滥用

## 8. 线程本地变量

```java
public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
```

### 使用节奏

```java
BaseContext.setCurrentId(id);
Long currentId = BaseContext.getCurrentId();
BaseContext.removeCurrentId();
```

## 9. MyBatis XML 动态 SQL

### 更新语句

```xml
<update id="update">
    update employee
    <set>
        <if test="name != null">name = #{name},</if>
        <if test="status != null">status = #{status},</if>
    </set>
    where id = #{id}
</update>
```

### 查询语句

```xml
<select id="pageQuery" resultType="com.sky.entity.Employee">
    select * from employee
    <where>
        <if test="name != null and name != ''">
            name like concat('%',#{name},'%')
        </if>
    </where>
    order by create_time desc
</select>
```

### 动态 SQL 里最常用的标签

- `<if>`
- `<where>`
- `<set>`
- `<choose>`
- `<foreach>`

## 10. Spring MVC 参数绑定

```java
@RequestBody OrdersSubmitDTO dto
@PathVariable Long id
@RequestParam Long page
@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin
```

### 记忆规则

- `@RequestBody`：JSON 请求体
- `@PathVariable`：路径参数
- `@RequestParam`：查询参数
- `@DateTimeFormat`：日期字符串转日期对象

## 11. 文件和 IO

```java
MultipartFile file
InputStream inputStream
ServletOutputStream out
```

### 主要场景

- 文件上传到 COS
- 读取 Excel 模板
- 导出报表文件

## 12. 最后要会的“通用动作”

- 会看懂 builder
- 会看懂 stream
- 会看懂注解
- 会看懂 XML 动态 SQL
- 会看懂时间 API

