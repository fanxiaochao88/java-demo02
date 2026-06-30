# 15 - MyBatis 分组聚合查询与 ECharts 统计实战

> 面向刚开始使用 Spring Boot + MyBatis + MySQL 的同学。
> 这篇重点解决一个核心疑问：**以前 `select *` 查出来能直接封装成实体类，为什么一到 `group by`、`count`、`sum`、ECharts 报表就懵了？**

---

## 目录

1. [先给你一个结论](#1-先给你一个结论)
2. [普通查询到底查出来什么](#2-普通查询到底查出来什么)
3. [分组查询到底是什么](#3-分组查询到底是什么)
4. [聚合函数是什么](#4-聚合函数是什么)
5. [分组查询查出来后映射成什么 Java 类型](#5-分组查询查出来后映射成什么-java-类型)
6. [MyBatis 接收分组结果的三种方式](#6-mybatis-接收分组结果的三种方式)
7. [从学生表理解完整流程](#7-从学生表理解完整流程)
8. [为什么不要用 Entity 接分组结果](#8-为什么不要用-entity-接分组结果)
9. [订单营业额统计：项目里的真实场景](#9-订单营业额统计项目里的真实场景)
10. [用户增长统计：新增用户和总用户](#10-用户增长统计新增用户和总用户)
11. [订单数量统计：总订单、有效订单、完成率](#11-订单数量统计总订单有效订单完成率)
12. [商品销量 Top10：连接查询 + 分组查询](#12-商品销量-top10连接查询--分组查询)
13. [WHERE、GROUP BY、HAVING、ORDER BY 的顺序](#13-wheregroup-byhavingorder-by-的顺序)
14. [ECharts 报表查询的固定套路](#14-echarts-报表查询的固定套路)
15. [常见坑位](#15-常见坑位)
16. [你接下来应该怎么练](#16-你接下来应该怎么练)

---

## 1. 先给你一个结论

你以前写的查询大多是这种：

```sql
select * from student where id = 1;
select * from student where class_id = 2;
```

这类查询的特点是：

```text
数据库表里的一行  -> Java 里的一个 Entity 对象
数据库表里的多行  -> Java 里的 List<Entity>
```

例如：

```text
student 表字段:
id, name, class_id, age

Java 实体类:
Student(id, name, classId, age)
```

字段基本能一一对应，所以 MyBatis 可以帮你自动封装。

但是分组统计查询是另一类东西：

```sql
select class_id, count(*) as studentCount
from student
group by class_id;
```

它查出来的不是某一个学生，也不是一批学生，而是：

```text
每个班级一行统计结果
```

结果长这样：

| class_id | studentCount |
| --- | ---: |
| 1 | 42 |
| 2 | 38 |
| 3 | 45 |

这不是 `Student`。

因为 `Student` 里面通常有：

```java
id
name
classId
age
```

但这次 SQL 返回的是：

```java
classId
studentCount
```

所以你的脑子里要先换一个模型：

```text
普通业务查询:
  查对象本身 -> Entity / List<Entity>

统计报表查询:
  查统计结果 -> DTO / VO / Map / 基础类型
```

后面所有内容都围绕这个模型展开。

---

## 2. 普通查询到底查出来什么

### 2.1 单行查询

SQL：

```sql
select id, name, class_id, age
from student
where id = 1;
```

查询结果：

| id | name | class_id | age |
| ---: | --- | ---: | ---: |
| 1 | 张三 | 2 | 18 |

Java 接收：

```java
Student student = studentMapper.getById(1L);
```

Mapper：

```java
Student getById(Long id);
```

XML：

```xml
<select id="getById" resultType="com.sky.entity.Student">
    select id, name, class_id, age
    from student
    where id = #{id}
</select>
```

这叫查一行完整对象。

### 2.2 多行查询

SQL：

```sql
select id, name, class_id, age
from student
where class_id = 2;
```

查询结果：

| id | name | class_id | age |
| ---: | --- | ---: | ---: |
| 1 | 张三 | 2 | 18 |
| 5 | 李四 | 2 | 19 |
| 8 | 王五 | 2 | 18 |

Java 接收：

```java
List<Student> studentList = studentMapper.listByClassId(2L);
```

Mapper：

```java
List<Student> listByClassId(Long classId);
```

XML：

```xml
<select id="listByClassId" resultType="com.sky.entity.Student">
    select id, name, class_id, age
    from student
    where class_id = #{classId}
</select>
```

这叫查多行完整对象。

### 2.3 普通查询的核心

普通查询的核心是：

```text
SQL 返回的每一行，都能看成一个完整的业务对象。
```

所以 MyBatis 可以把：

```text
id          -> id
name        -> name
class_id    -> classId
age         -> age
```

封装成 `Student`。

---

## 3. 分组查询到底是什么

### 3.1 先不要想 SQL，先想生活中的分组

假设有一堆学生：

| id | name | class_id |
| ---: | --- | ---: |
| 1 | 张三 | 1 |
| 2 | 李四 | 1 |
| 3 | 王五 | 2 |
| 4 | 赵六 | 2 |
| 5 | 钱七 | 2 |

如果问题是：

```text
请查询每个班级有多少学生。
```

你脑子里会这样做：

```text
先按 class_id 把学生分堆:

class_id = 1:
  张三、李四

class_id = 2:
  王五、赵六、钱七

再数每一堆有几个人:

class_id = 1 -> 2 人
class_id = 2 -> 3 人
```

SQL 的 `group by` 做的就是这件事。

### 3.2 GROUP BY 的本质

SQL：

```sql
select class_id, count(*) as studentCount
from student
group by class_id;
```

可以拆成三步理解：

```text
第 1 步：from student
  先拿到 student 表里的原始行。

第 2 步：group by class_id
  按 class_id 把原始行分成一组一组。

第 3 步：select class_id, count(*)
  每一组输出一行结果。
  class_id 是这一组的分组字段。
  count(*) 是这一组的行数。
```

所以分组查询的核心是：

```text
多行原始数据 -> 按某个字段分成多组 -> 每组压缩成一行统计结果
```

### 3.3 分组查询不是查原始对象

这点非常重要。

下面这个 SQL：

```sql
select class_id, count(*) as studentCount
from student
group by class_id;
```

查出来的是：

```text
班级统计结果
```

不是：

```text
学生对象
```

所以 Java 里应该用类似这种类接：

```java
@Data
public class ClassStudentCountVO {
    private Long classId;
    private Long studentCount;
}
```

而不是用：

```java
Student
```

---

## 4. 聚合函数是什么

分组之后，你通常要对每一组做统计。

这些统计函数就叫聚合函数。

### 4.1 常见聚合函数

| 聚合函数 | 含义 | 示例 |
| --- | --- | --- |
| `count(*)` | 统计行数 | 每个班多少学生 |
| `sum(amount)` | 求和 | 每天营业额 |
| `avg(score)` | 平均值 | 每个班平均分 |
| `max(score)` | 最大值 | 每个班最高分 |
| `min(score)` | 最小值 | 每个班最低分 |

### 4.2 count 的常见写法

```sql
select count(*) from student;
```

含义：统计 `student` 表有多少行。

```sql
select count(id) from student;
```

含义：统计 `id` 不为 `null` 的行数。

大多数主键 `id` 都不为 `null`，所以 `count(*)` 和 `count(id)` 结果通常一样。

连接查询里要注意：

```sql
select c.id, c.name, count(s.id) as studentCount
from class c
left join student s on s.class_id = c.id
group by c.id, c.name;
```

这里更推荐 `count(s.id)`，因为 `left join` 后，如果某个班级没有学生，`s.id` 是 `null`，统计出来就是 `0`。

### 4.3 sum 的常见写法

```sql
select sum(amount)
from orders
where status = 5;
```

含义：统计所有已完成订单的实收金额总和。

但是如果没有任何符合条件的订单，`sum(amount)` 可能返回 `null`。

所以报表里经常写：

```sql
select coalesce(sum(amount), 0) as totalAmount
from orders
where status = 5;
```

`coalesce(a, b)` 的意思是：

```text
如果 a 不是 null，就返回 a。
如果 a 是 null，就返回 b。
```

### 4.4 avg、max、min

```sql
select class_id, avg(score) as avgScore
from student_score
group by class_id;
```

```sql
select class_id, max(score) as maxScore
from student_score
group by class_id;
```

```sql
select class_id, min(score) as minScore
from student_score
group by class_id;
```

这些都不是查某个学生，而是查每个班级的统计值。

---

## 5. 分组查询查出来后映射成什么 Java 类型

这是你现在最关键的疑问。

先看一张表。

| SQL 类型 | SQL 示例 | 查询结果形状 | Java 接收类型 |
| --- | --- | --- | --- |
| 查单个完整对象 | `select * from student where id = ?` | 一行，字段和实体对应 | `Student` |
| 查多个完整对象 | `select * from student` | 多行，字段和实体对应 | `List<Student>` |
| 查单个值 | `select count(*) from student` | 一行一列 | `Long` / `Integer` |
| 查一行统计结果 | `select count(*), sum(amount) from orders` | 一行多列 | `OrderSummaryVO` |
| 查多行统计结果 | `select class_id, count(*) ... group by class_id` | 多行，每行是一个统计结果 | `List<ClassStudentCountVO>` |
| 查临时结构 | 任意自定义列 | 多行或单行 | `Map` / `List<Map<String,Object>>` |

### 5.1 最推荐的思维方式

每次写 SQL 前先问自己：

```text
这个 SQL 返回的一行，应该被 Java 看成什么？
```

如果返回的一行是一个学生：

```java
Student
```

如果返回的一行是一个班级人数统计：

```java
ClassStudentCountVO
```

如果返回的一行是一天的营业额：

```java
TurnoverDailyVO
```

如果返回的一行是一天的新增用户数：

```java
UserDailyCountVO
```

这个类不一定非要叫 `VO`，也可以叫 `DTO`。

在你的项目结构里：

```text
sky-pojo/src/main/java/com/sky/dto  通常放入参或中间传输对象
sky-pojo/src/main/java/com/sky/vo   通常放返回给前端的对象
```

如果只是 Mapper 查出来给 Service 内部整理，也可以放 DTO。
如果就是 Controller 最终返回给前端，可以放 VO。

---

## 6. MyBatis 接收分组结果的三种方式

### 6.1 方式一：用专门的 DTO/VO 接，最推荐

比如每个班级的人数统计：

```java
@Data
public class ClassStudentCountVO {
    private Long classId;
    private Long studentCount;
}
```

Mapper：

```java
List<ClassStudentCountVO> countStudentByClass();
```

XML：

```xml
<select id="countStudentByClass" resultType="com.sky.vo.ClassStudentCountVO">
    select
        class_id as classId,
        count(*) as studentCount
    from student
    group by class_id
</select>
```

注意这里的别名：

```sql
class_id as classId
count(*) as studentCount
```

MyBatis 封装对象时，本质是按列名找属性名。

如果 SQL 查出来的列名是：

```text
classId
studentCount
```

Java 类里也有：

```text
classId
studentCount
```

就很容易自动封装。

### 6.2 方式二：用 Map 接，适合临时测试，不推荐长期业务

Mapper：

```java
List<Map<String, Object>> countStudentByClass();
```

XML：

```xml
<select id="countStudentByClass" resultType="java.util.Map">
    select
        class_id as classId,
        count(*) as studentCount
    from student
    group by class_id
</select>
```

Service：

```java
List<Map<String, Object>> list = studentMapper.countStudentByClass();

for (Map<String, Object> row : list) {
    Object classId = row.get("classId");
    Object studentCount = row.get("studentCount");
}
```

Map 的优点：

```text
写起来快。
不用新建类。
```

Map 的缺点：

```text
key 写错，编译器不知道。
value 是 Object，取出来经常要强转。
count、sum、date 的真实 Java 类型容易和你想的不一样。
代码读起来不如 VO 清楚。
```

所以正式业务里更推荐 DTO/VO。

你的项目里现在 `ReportMapper.xml` 的 `sumByMap` 就用了 `resultType="java.util.Map"`。
这能跑通一些简单场景，但对新手来说更容易迷糊：查出来的 `dateKey` 到底是什么类型，`totalAmount` 到底是 `Double` 还是 `BigDecimal`，都需要额外判断。

### 6.3 方式三：查单个值，用基础类型接

SQL：

```sql
select count(*)
from user;
```

Mapper：

```java
Long countAllUser();
```

XML：

```xml
<select id="countAllUser" resultType="java.lang.Long">
    select count(*)
    from user
</select>
```

金额求和：

```java
BigDecimal sumCompletedOrderAmount();
```

XML：

```xml
<select id="sumCompletedOrderAmount" resultType="java.math.BigDecimal">
    select coalesce(sum(amount), 0)
    from orders
    where status = 5
</select>
```

金额建议用 `BigDecimal`，不要用 `Double`。

---

## 7. 从学生表理解完整流程

### 7.1 需求

查询每个班级的学生人数，返回给前端画柱状图。

前端可能需要：

```json
{
  "classNameList": "一班,二班,三班",
  "studentCountList": "42,38,45"
}
```

也可能需要：

```json
[
  {"className": "一班", "studentCount": 42},
  {"className": "二班", "studentCount": 38},
  {"className": "三班", "studentCount": 45}
]
```

两种都可以，取决于前端接口约定。

你的苍穹外卖项目里很多报表 VO 用的是第一种：

```java
private String dateList;
private String turnoverList;
```

也就是逗号拼接字符串。

### 7.2 表结构假设

```sql
student(
  id bigint,
  name varchar(50),
  class_id bigint
)

class(
  id bigint,
  name varchar(50)
)
```

### 7.3 只统计有学生的班级

```sql
select
    s.class_id as classId,
    count(*) as studentCount
from student s
group by s.class_id;
```

这个 SQL 只能查出有学生的班级。

如果某个班级一个学生都没有，它不会出现在结果中。

### 7.4 统计所有班级，包括 0 人班级

```sql
select
    c.id as classId,
    c.name as className,
    count(s.id) as studentCount
from class c
left join student s on s.class_id = c.id
group by c.id, c.name
order by c.id;
```

这里用到了 `left join`。

含义是：

```text
以 class 表为主。
即使某个班没有学生，也保留这个班。
student 没匹配上的字段就是 null。
count(s.id) 会把 null 排除，所以人数是 0。
```

### 7.5 定义接收 SQL 结果的 VO

```java
package com.sky.vo;

import lombok.Data;

@Data
public class ClassStudentCountVO {
    private Long classId;
    private String className;
    private Long studentCount;
}
```

### 7.6 Mapper 接口

```java
@Mapper
public interface StudentReportMapper {
    List<ClassStudentCountVO> countStudentByClass();
}
```

### 7.7 Mapper XML

```xml
<mapper namespace="com.sky.mapper.StudentReportMapper">
    <select id="countStudentByClass" resultType="com.sky.vo.ClassStudentCountVO">
        select
            c.id as classId,
            c.name as className,
            count(s.id) as studentCount
        from class c
        left join student s on s.class_id = c.id
        group by c.id, c.name
        order by c.id
    </select>
</mapper>
```

### 7.8 Service 整理成 ECharts 需要的结构

```java
List<ClassStudentCountVO> rows = studentReportMapper.countStudentByClass();

String classNameList = rows.stream()
        .map(ClassStudentCountVO::getClassName)
        .collect(Collectors.joining(","));

String studentCountList = rows.stream()
        .map(row -> String.valueOf(row.getStudentCount()))
        .collect(Collectors.joining(","));
```

这就是报表开发最常见的套路：

```text
Mapper 查出一行一行的统计结果
Service 把这些结果整理成前端图表需要的格式
Controller 返回 VO
```

---

## 8. 为什么不要用 Entity 接分组结果

假设你的 `Student` 是：

```java
@Data
public class Student {
    private Long id;
    private String name;
    private Long classId;
    private Integer age;
}
```

而你的 SQL 是：

```sql
select class_id as classId, count(*) as studentCount
from student
group by class_id;
```

SQL 返回列：

```text
classId
studentCount
```

`Student` 属性：

```text
id
name
classId
age
```

只有 `classId` 对得上。

`studentCount` 在 `Student` 里没有。

`id`、`name`、`age` 在 SQL 结果里没有。

所以用 `Student` 接会出现这些问题：

```text
1. 对象语义不对：这不是一个学生。
2. 很多属性是 null。
3. 统计字段接不住。
4. 后面的人读代码会误会。
```

正确做法是：

```text
SQL 返回什么形状，就定义什么形状的 DTO/VO。
```

这个习惯非常重要。

---

## 9. 订单营业额统计：项目里的真实场景

你的项目里有 `orders` 表，对应实体类是：

```java
com.sky.entity.Orders
```

里面有这些关键字段：

```java
private Integer status;          // 订单状态
private LocalDateTime orderTime; // 下单时间
private BigDecimal amount;       // 实收金额
```

订单状态里：

```java
public static final Integer COMPLETED = 5;
```

也就是说，统计营业额时通常只统计已完成订单：

```sql
where status = 5
```

### 9.1 需求

查询某个日期范围内每天的营业额，用于 ECharts 折线图。

例如：

```text
begin = 2026-06-01
end   = 2026-06-07
```

前端希望拿到：

```json
{
  "dateList": "2026-06-01,2026-06-02,2026-06-03,2026-06-04,2026-06-05,2026-06-06,2026-06-07",
  "turnoverList": "120.50,0,88.00,230.00,0,56.00,99.00"
}
```

注意：中间没有订单的日期也要有 `0`。

### 9.2 SQL 怎么写

```sql
select
    date(order_time) as orderDate,
    coalesce(sum(amount), 0) as turnover
from orders
where status = 5
  and order_time >= '2026-06-01 00:00:00'
  and order_time <= '2026-06-07 23:59:59'
group by date(order_time)
order by orderDate;
```

这个 SQL 的结果可能是：

| orderDate | turnover |
| --- | ---: |
| 2026-06-01 | 120.50 |
| 2026-06-03 | 88.00 |
| 2026-06-04 | 230.00 |
| 2026-06-06 | 56.00 |
| 2026-06-07 | 99.00 |

你会发现：

```text
2026-06-02 没有订单，所以 SQL 不会返回这一行。
2026-06-05 没有订单，所以 SQL 不会返回这一行。
```

这就是为什么 Service 里要补 0。

### 9.3 定义 Mapper 查询结果 DTO

推荐新建一个类，例如：

```java
package com.sky.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TurnoverDailyDTO {
    private LocalDate orderDate;
    private BigDecimal turnover;
}
```

它表达的是：

```text
某一天的营业额统计结果
```

不是订单实体。

### 9.4 Mapper 接口

推荐用 `@Param` 明确参数名：

```java
@Mapper
public interface ReportMapper {

    List<TurnoverDailyDTO> sumTurnoverByDay(@Param("begin") LocalDateTime begin,
                                            @Param("end") LocalDateTime end,
                                            @Param("status") Integer status);
}
```

### 9.5 Mapper XML

```xml
<select id="sumTurnoverByDay" resultType="com.sky.dto.TurnoverDailyDTO">
    select
        date(order_time) as orderDate,
        coalesce(sum(amount), 0) as turnover
    from orders
    where status = #{status}
      and order_time &gt;= #{begin}
      and order_time &lt;= #{end}
    group by date(order_time)
    order by orderDate
</select>
```

XML 里不能直接写 `<`，要写成：

```xml
&lt;
&lt;=
&gt;
&gt;=
```

所以你在 MyBatis XML 里会看到：

```xml
order_time &gt;= #{begin}
order_time &lt;= #{end}
```

### 9.6 Service 怎么补齐日期和 0

你的项目里已经有 `TurnoverReportVO`：

```java
private String dateList;
private String turnoverList;
```

Service 可以这样整理：

```java
public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
    // 1. 生成 begin 到 end 之间的每一天
    List<LocalDate> dateList = new ArrayList<>();
    LocalDate current = begin;
    while (!current.isAfter(end)) {
        dateList.add(current);
        current = current.plusDays(1);
    }

    // 2. 查询数据库里有订单的日期
    LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
    LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

    List<TurnoverDailyDTO> rows = reportMapper.sumTurnoverByDay(
            beginTime,
            endTime,
            Orders.COMPLETED
    );

    // 3. 把查询结果转成 Map，方便按日期查金额
    Map<LocalDate, BigDecimal> turnoverMap = rows.stream()
            .collect(Collectors.toMap(
                    TurnoverDailyDTO::getOrderDate,
                    TurnoverDailyDTO::getTurnover
            ));

    // 4. 按完整日期列表逐天取值，没有就补 0
    List<String> turnoverList = dateList.stream()
            .map(date -> turnoverMap.getOrDefault(date, BigDecimal.ZERO))
            .map(BigDecimal::toString)
            .collect(Collectors.toList());

    return TurnoverReportVO.builder()
            .dateList(dateList.stream()
                    .map(LocalDate::toString)
                    .collect(Collectors.joining(",")))
            .turnoverList(String.join(",", turnoverList))
            .build();
}
```

这段代码的关键不是 Stream，而是思路：

```text
1. 先生成完整日期轴。
2. SQL 只查有数据的日期。
3. SQL 结果转 Map。
4. 遍历完整日期轴，从 Map 里取值。
5. 取不到就补 0。
```

ECharts 的折线图、柱状图经常都要这样做。

### 9.7 你现在项目里的代码为什么会卡住

你当前 `ReportMapper.xml` 已经有类似 SQL：

```xml
<select id="sumByMap" resultType="java.util.Map">
    select
    DATE(order_time) AS dateKey,
    COALESCE(SUM(amount), 0) AS totalAmount
    from orders
    ...
    GROUP BY DATE(order_time)
</select>
```

但 `ReportServiceImpl` 里创建了：

```java
List<Double> turnoverList = new ArrayList<>();
```

然后查询了：

```java
List<HashMap<String, Double>> sqlRes = reportMapper.sumByMap(map);
```

后面还没有把 `sqlRes` 里的结果放进 `turnoverList`。

也就是说，这个业务真正缺的是：

```text
把 SQL 查到的“有数据的日期和金额”
整理成前端需要的“完整日期列表和每天金额列表”。
```

这就是分组统计业务里 Service 层的主要价值。

---

## 10. 用户增长统计：新增用户和总用户

你的项目里 `User` 实体有：

```java
private LocalDateTime createTime;
```

用户统计常见两个指标：

```text
newUserList    每天新增用户数
totalUserList  截止每天的用户总数
```

### 10.1 每天新增用户数

SQL：

```sql
select
    date(create_time) as createDate,
    count(*) as newUserCount
from user
where create_time >= '2026-06-01 00:00:00'
  and create_time <= '2026-06-07 23:59:59'
group by date(create_time)
order by createDate;
```

为了避免表名和数据库内置概念混淆，也可以写成：

```sql
from `user`
```

DTO：

```java
@Data
public class UserDailyCountDTO {
    private LocalDate createDate;
    private Long newUserCount;
}
```

Mapper：

```java
List<UserDailyCountDTO> countNewUserByDay(@Param("begin") LocalDateTime begin,
                                          @Param("end") LocalDateTime end);
```

XML：

```xml
<select id="countNewUserByDay" resultType="com.sky.dto.UserDailyCountDTO">
    select
        date(create_time) as createDate,
        count(*) as newUserCount
    from `user`
    where create_time &gt;= #{begin}
      and create_time &lt;= #{end}
    group by date(create_time)
    order by createDate
</select>
```

### 10.2 截止每天的用户总数

总用户数有两种做法。

#### 做法 A：每天查一次总数，简单但 SQL 次数多

假设日期范围 7 天，就查 7 次：

```sql
select count(*)
from `user`
where create_time <= '2026-06-01 23:59:59';
```

```sql
select count(*)
from `user`
where create_time <= '2026-06-02 23:59:59';
```

优点：

```text
容易理解。
```

缺点：

```text
日期范围越大，SQL 次数越多。
```

#### 做法 B：先查起始日前总数，再每天累加新增，推荐

思路：

```text
1. 查 begin 之前已有多少用户。
2. 查 begin 到 end 每天新增多少用户。
3. Java 里从 begin 开始逐天累加。
```

Mapper：

```java
Long countUserBefore(@Param("begin") LocalDateTime begin);
```

XML：

```xml
<select id="countUserBefore" resultType="java.lang.Long">
    select count(*)
    from `user`
    where create_time &lt; #{begin}
</select>
```

Service 核心逻辑：

```java
Long baseTotal = userMapper.countUserBefore(beginTime);

Map<LocalDate, Long> newUserMap = rows.stream()
        .collect(Collectors.toMap(
                UserDailyCountDTO::getCreateDate,
                UserDailyCountDTO::getNewUserCount
        ));

long runningTotal = baseTotal;
List<String> totalUserList = new ArrayList<>();
List<String> newUserList = new ArrayList<>();

for (LocalDate date : dateList) {
    long newCount = newUserMap.getOrDefault(date, 0L);
    runningTotal += newCount;

    newUserList.add(String.valueOf(newCount));
    totalUserList.add(String.valueOf(runningTotal));
}
```

最后构建你项目里的 `UserReportVO`：

```java
return UserReportVO.builder()
        .dateList(dateList.stream()
                .map(LocalDate::toString)
                .collect(Collectors.joining(",")))
        .newUserList(String.join(",", newUserList))
        .totalUserList(String.join(",", totalUserList))
        .build();
```

---

## 11. 订单数量统计：总订单、有效订单、完成率

订单报表通常需要：

```text
每日订单数
每日有效订单数
订单总数
有效订单数
订单完成率
```

在你的项目里，已完成订单状态是：

```java
Orders.COMPLETED = 5
```

可以认为有效订单就是已完成订单。

### 11.1 用一个 SQL 同时统计总订单和有效订单

```sql
select
    date(order_time) as orderDate,
    count(*) as orderCount,
    sum(case when status = 5 then 1 else 0 end) as validOrderCount
from orders
where order_time >= '2026-06-01 00:00:00'
  and order_time <= '2026-06-07 23:59:59'
group by date(order_time)
order by orderDate;
```

这里的核心是：

```sql
sum(case when status = 5 then 1 else 0 end)
```

它的意思是：

```text
每行订单：
  如果 status = 5，就记 1。
  否则记 0。

每组求和：
  就得到了这一组里已完成订单数量。
```

### 11.2 DTO

```java
@Data
public class OrderDailyCountDTO {
    private LocalDate orderDate;
    private Long orderCount;
    private Long validOrderCount;
}
```

### 11.3 Mapper XML

```xml
<select id="countOrderByDay" resultType="com.sky.dto.OrderDailyCountDTO">
    select
        date(order_time) as orderDate,
        count(*) as orderCount,
        sum(case when status = #{completedStatus} then 1 else 0 end) as validOrderCount
    from orders
    where order_time &gt;= #{begin}
      and order_time &lt;= #{end}
    group by date(order_time)
    order by orderDate
</select>
```

Mapper：

```java
List<OrderDailyCountDTO> countOrderByDay(@Param("begin") LocalDateTime begin,
                                         @Param("end") LocalDateTime end,
                                         @Param("completedStatus") Integer completedStatus);
```

### 11.4 完成率怎么算

```java
long totalOrderCount = rows.stream()
        .mapToLong(OrderDailyCountDTO::getOrderCount)
        .sum();

long validOrderCount = rows.stream()
        .mapToLong(OrderDailyCountDTO::getValidOrderCount)
        .sum();

double orderCompletionRate = 0.0;
if (totalOrderCount > 0) {
    orderCompletionRate = validOrderCount * 1.0 / totalOrderCount;
}
```

如果前端要百分比，你可以返回：

```text
0.75
```

让前端显示成：

```text
75%
```

也可以后端直接乘以 100，但要和前端约定好。

---

## 12. 商品销量 Top10：连接查询 + 分组查询

你的项目里有 `order_detail`，实体类是：

```java
com.sky.entity.OrderDetail
```

关键字段：

```java
private String name;      // 商品名称
private Long orderId;     // 订单 id
private Integer number;   // 数量
```

如果要统计销量 Top10，通常不是只查 `order_detail`，还要关联 `orders`。

原因是：

```text
order_detail 记录了商品数量。
orders 记录了订单状态和下单时间。
```

你要统计某个时间范围内已完成订单的商品销量，就需要：

```text
order_detail join orders
```

### 12.1 SQL

```sql
select
    od.name as name,
    sum(od.number) as number
from order_detail od
join orders o on od.order_id = o.id
where o.status = 5
  and o.order_time >= '2026-06-01 00:00:00'
  and o.order_time <= '2026-06-30 23:59:59'
group by od.name
order by number desc
limit 10;
```

这段 SQL 的执行思路：

```text
1. order_detail 和 orders 通过 order_id = id 连接。
2. 只保留已完成订单。
3. 只保留时间范围内订单。
4. 按商品名称分组。
5. 每组 sum(number)，得到销量。
6. 按销量倒序。
7. 只取前 10 条。
```

### 12.2 DTO

你的项目里已经有：

```java
com.sky.dto.GoodsSalesDTO
```

字段正好是：

```java
private String name;
private Integer number;
```

可以直接接这个查询结果。

### 12.3 Mapper XML

```xml
<select id="getSalesTop10" resultType="com.sky.dto.GoodsSalesDTO">
    select
        od.name as name,
        sum(od.number) as number
    from order_detail od
    join orders o on od.order_id = o.id
    where o.status = #{status}
      and o.order_time &gt;= #{begin}
      and o.order_time &lt;= #{end}
    group by od.name
    order by number desc
    limit 10
</select>
```

Mapper：

```java
List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin,
                                  @Param("end") LocalDateTime end,
                                  @Param("status") Integer status);
```

### 12.4 Service 整理成 VO

你的项目里有：

```java
SalesTop10ReportVO
```

字段是：

```java
private String nameList;
private String numberList;
```

整理方式：

```java
List<GoodsSalesDTO> rows = reportMapper.getSalesTop10(beginTime, endTime, Orders.COMPLETED);

String nameList = rows.stream()
        .map(GoodsSalesDTO::getName)
        .collect(Collectors.joining(","));

String numberList = rows.stream()
        .map(row -> String.valueOf(row.getNumber()))
        .collect(Collectors.joining(","));

return SalesTop10ReportVO.builder()
        .nameList(nameList)
        .numberList(numberList)
        .build();
```

---

## 13. WHERE、GROUP BY、HAVING、ORDER BY 的顺序

### 13.1 SQL 书写顺序

你写 SQL 时通常按这个顺序：

```sql
select ...
from ...
where ...
group by ...
having ...
order by ...
limit ...
```

### 13.2 可以这样理解执行过程

虽然数据库内部优化器会做很多优化，但你学习时可以先按这个顺序理解：

```text
1. from       从哪些表拿数据
2. join       表之间怎么连接
3. where      对原始行进行过滤
4. group by   把过滤后的行分组
5. 聚合函数    对每组计算 count/sum/avg...
6. having     对分组后的统计结果进行过滤
7. select     决定最终输出哪些列
8. order by   排序
9. limit      限制条数
```

### 13.3 WHERE 和 HAVING 的区别

`where` 过滤的是分组前的原始行：

```sql
select class_id, count(*) as studentCount
from student
where age >= 18
group by class_id;
```

含义：

```text
先筛出年龄 >= 18 的学生。
再按班级分组统计人数。
```

`having` 过滤的是分组后的统计结果：

```sql
select class_id, count(*) as studentCount
from student
group by class_id
having count(*) >= 30;
```

含义：

```text
先按班级统计人数。
再只保留人数 >= 30 的班级。
```

一句话记忆：

```text
where 过滤行。
having 过滤组。
```

### 13.4 GROUP BY 后 SELECT 能写什么

这也是新手常见坑。

推荐遵守这个规则：

```text
select 后面只能写：
1. group by 里的字段
2. 聚合函数
```

正确：

```sql
select class_id, count(*)
from student
group by class_id;
```

错误或不推荐：

```sql
select id, name, class_id, count(*)
from student
group by class_id;
```

因为一个班级里有很多学生。

分组后每个班级只输出一行。

那 `id` 和 `name` 到底应该显示哪一个学生的？

这个问题本身就不合理。

如果你的 MySQL 开启了 `ONLY_FULL_GROUP_BY`，这种 SQL 会直接报错。

---

## 14. ECharts 报表查询的固定套路

你最近做 ECharts，建议把所有报表都套进这个套路里。

### 14.1 固定流程

```text
1. Controller 接收 begin、end 等查询条件。
2. Service 把 LocalDate 转成 LocalDateTime。
3. Mapper 用 SQL 做 group by 统计。
4. SQL 返回 List<统计DTO>。
5. Service 生成完整 x 轴。
6. Service 把 SQL 结果转成 Map。
7. Service 按完整 x 轴补齐缺失值。
8. Service 封装成 VO。
9. Controller 返回给前端。
```

### 14.2 为什么要生成完整 x 轴

因为 SQL 的 `group by` 只会返回有数据的组。

比如 7 天里只有 3 天有订单：

```text
SQL 返回:
2026-06-01 -> 100
2026-06-03 -> 200
2026-06-07 -> 300
```

但 ECharts 折线图需要：

```text
x 轴:
2026-06-01
2026-06-02
2026-06-03
2026-06-04
2026-06-05
2026-06-06
2026-06-07

y 轴:
100
0
200
0
0
0
300
```

所以补 0 通常放在 Service 层。

### 14.3 通用日期列表生成方法

```java
private List<LocalDate> buildDateList(LocalDate begin, LocalDate end) {
    List<LocalDate> dateList = new ArrayList<>();
    LocalDate current = begin;
    while (!current.isAfter(end)) {
        dateList.add(current);
        current = current.plusDays(1);
    }
    return dateList;
}
```

### 14.4 通用时间边界

```java
LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
```

也可以使用更推荐的左闭右开区间：

```java
LocalDateTime beginTime = begin.atStartOfDay();
LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
```

对应 SQL：

```sql
where order_time >= #{beginTime}
  and order_time < #{endExclusive}
```

左闭右开的好处是：

```text
不需要纠结 23:59:59.999999。
对 datetime 精度变化更稳。
```

MyBatis XML：

```xml
where order_time &gt;= #{beginTime}
  and order_time &lt; #{endExclusive}
```

---

## 15. 常见坑位

### 15.1 坑一：用实体类接统计结果

错误思路：

```text
orders 表查出来就一定用 Orders 接。
user 表查出来就一定用 User 接。
```

正确思路：

```text
查订单本身，用 Orders。
查订单统计结果，用统计 DTO/VO。
```

例如：

```java
Orders                 // 一条订单
TurnoverDailyDTO       // 某天营业额
OrderDailyCountDTO     // 某天订单数
GoodsSalesDTO          // 某个商品销量
```

### 15.2 坑二：SQL 别名和 Java 属性对不上

Java：

```java
private Long studentCount;
```

SQL 最好写：

```sql
count(*) as studentCount
```

不要写成：

```sql
count(*) as student_count
```

除非你确认 MyBatis 的下划线转驼峰配置能生效。

统计类 DTO/VO 字段少，直接用别名对齐最稳。

### 15.3 坑三：count 的类型不是 Integer

`count(*)` 在 MySQL 里常常映射成 `Long`。

所以 DTO 里建议写：

```java
private Long count;
```

如果业务最后必须是 `Integer`，可以在 Service 里转换。

### 15.4 坑四：金额用 Double

金额建议用：

```java
BigDecimal
```

不要用：

```java
Double
```

原因是 `Double` 是二进制浮点数，可能出现精度问题。

你的 `Orders.amount` 已经是 `BigDecimal`，这是对的。

### 15.5 坑五：sum 结果为 null

没有符合条件的订单时：

```sql
sum(amount)
```

可能返回 `null`。

报表里建议：

```sql
coalesce(sum(amount), 0) as turnover
```

### 15.6 坑六：日期缺失没有补 0

`group by date(order_time)` 只返回有订单的日期。

没有订单的日期不会返回。

所以 ECharts 的连续日期图表，一定要在 Service 层补齐。

### 15.7 坑七：在 WHERE 里对字段套函数

不推荐：

```sql
where date(order_time) = '2026-06-01'
```

更推荐：

```sql
where order_time >= '2026-06-01 00:00:00'
  and order_time < '2026-06-02 00:00:00'
```

原因是 `order_time` 如果有索引，第二种更容易利用索引。

可以在 `select` 和 `group by` 里使用：

```sql
date(order_time)
```

但过滤条件尽量用原字段范围。

### 15.8 坑八：Map 接收结果后类型猜错

比如：

```java
List<Map<String, Object>> rows = mapper.query();
```

你以为：

```java
Double amount = (Double) row.get("totalAmount");
```

但实际可能是：

```java
BigDecimal
```

这时就会强转异常。

所以正式代码里推荐 DTO/VO。

### 15.9 坑九：多个参数没有 @Param

Mapper：

```java
List<TurnoverDailyDTO> query(LocalDateTime begin, LocalDateTime end, Integer status);
```

XML 里写：

```xml
#{begin}
#{end}
#{status}
```

可能拿不到。

更稳的写法：

```java
List<TurnoverDailyDTO> query(@Param("begin") LocalDateTime begin,
                             @Param("end") LocalDateTime end,
                             @Param("status") Integer status);
```

### 15.10 坑十：忘记排序

报表数据通常要按日期升序：

```sql
order by orderDate
```

Top10 通常要按统计值倒序：

```sql
order by number desc
limit 10
```

不排序时，数据库不保证返回顺序就是你想要的顺序。

---

## 16. 你接下来应该怎么练

不要一上来就写复杂报表。

按这个顺序练，最稳。

### 16.1 第一组：只练 SQL

在 MySQL 客户端里写：

```sql
select count(*) from orders;
```

```sql
select status, count(*) as count
from orders
group by status;
```

```sql
select date(order_time) as orderDate, count(*) as count
from orders
group by date(order_time)
order by orderDate;
```

```sql
select date(order_time) as orderDate, coalesce(sum(amount), 0) as amount
from orders
where status = 5
group by date(order_time)
order by orderDate;
```

目标是先看懂 SQL 结果长什么样。

### 16.2 第二组：练 MyBatis 映射

每写一个 SQL，就问：

```text
这一行结果应该定义成什么 Java 类？
```

然后写：

```java
DTO/VO
Mapper interface
Mapper XML
```

先不用管 ECharts。

### 16.3 第三组：练 Service 整理数据

重点练：

```text
List<统计DTO> -> Map<日期, 统计值>
完整日期列表 -> 补 0 -> 逗号拼接字符串
```

你会发现 ECharts 报表大多都是这个套路。

### 16.4 第四组：练项目里的四个报表

建议按这个顺序：

```text
1. 营业额统计 TurnoverReportVO
2. 用户统计 UserReportVO
3. 订单统计 OrderReportVO
4. 销量 Top10 SalesTop10ReportVO
```

这四个练完，你对：

```text
group by
count
sum
case when
join
DTO/VO 映射
ECharts 数据整理
```

基本就能形成一套完整感觉。

---

## 最后总结

分组查询不难，真正让人懵的是它改变了你以前的对象模型。

以前你写：

```sql
select * from orders
```

你查的是订单本身，所以用：

```java
Orders
List<Orders>
```

现在你写：

```sql
select date(order_time), sum(amount)
from orders
group by date(order_time)
```

你查的是：

```text
每天的营业额统计结果
```

所以应该用：

```java
TurnoverDailyDTO
List<TurnoverDailyDTO>
```

记住这一句话：

```text
SQL 返回的一行是什么含义，Java 就定义一个对应含义的类去接它。
```

只要这个观念建立起来，分组查询、聚合函数、MyBatis 映射、ECharts 统计数据整理，就都能串起来。
