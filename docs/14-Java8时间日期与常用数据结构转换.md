# 14 - Java 8 时间日期与常用数据结构转换

> 这篇不是为了背 API，而是为了建立“后端业务开发时我知道能做什么”的能力边界。
> 你可以忘记具体方法名，但要知道：哪些类型适合表达什么，哪些类型之间可以互相转换，遇到接口参数、数据库字段、前端展示时该往哪个方向想。

---

## 目录

1. [先建立一张后端类型地图](#1-先建立一张后端类型地图)
2. [Java 8 时间日期类型总览](#2-java-8-时间日期类型总览)
3. [LocalDate、LocalTime、LocalDateTime 常用能力](#3-localdatelocaltimelocaldatetime-常用能力)
4. [格式化与解析：时间和字符串互转](#4-格式化与解析时间和字符串互转)
5. [时区、时间戳、旧 Date 的边界](#5-时区时间戳旧-date-的边界)
6. [Spring 接口服务中的时间处理](#6-spring-接口服务中的时间处理)
7. [String 的常用能力](#7-string-的常用能力)
8. [数组的常用能力](#8-数组的常用能力)
9. [List 的常用能力](#9-list-的常用能力)
10. [String、数组、List 之间的转换](#10-string数组list-之间的转换)
11. [List\<LocalDateTime\> 这种组合类型怎么处理](#11-listlocaldatetime-这种组合类型怎么处理)
12. [API 接口开发中的典型转换场景](#12-api-接口开发中的典型转换场景)
13. [常见坑位](#13-常见坑位)
14. [能力边界速查表](#14-能力边界速查表)
15. [最后给你的学习抓手](#15-最后给你的学习抓手)

---

## 1. 先建立一张后端类型地图

后端写业务代码时，大多数转换都可以拆成两类：

```text
值类型：一个具体值
  String、Integer、Long、BigDecimal、LocalDateTime、LocalDate

容器类型：装一批值
  数组 T[]、List<T>、Set<T>、Map<K, V>

展示/传输形态：给前端、URL、JSON、日志、数据库看的形态
  String、JSON 数组、JSON 对象、数据库 datetime、URL 查询参数
```

你心里要有的不是“某个 API 怎么背”，而是下面这几个判断：

1. **我要表达单个值，还是一组值？**
   - 单个订单创建时间：`LocalDateTime`
   - 一批订单 ID：`List<Long>`
   - 一批日期：`List<LocalDate>`

2. **我要计算，还是展示？**
   - 计算时间差、比较先后：用 `LocalDateTime`、`LocalDate`、`Duration`
   - 返回给前端展示：通常转成 `String`，或者交给 Jackson 自动序列化

3. **我要保留顺序、去重，还是按 key 查找？**
   - 保留顺序：`List`
   - 去重：`Set`
   - 按 id 查对象：`Map<Long, Dish>`

4. **我是固定长度，还是动态增删？**
   - 固定长度：数组
   - 经常增删查：`List`

5. **这个时间有没有时区含义？**
   - 本地业务时间，如下单时间：`LocalDateTime`
   - 绝对时间点，如 token 过期时间戳：`Instant` 或旧库要求的 `Date`

---

## 2. Java 8 时间日期类型总览

Java 8 新增了 `java.time` 包，它比老的 `java.util.Date`、`Calendar` 更适合业务开发。

### 2.1 常用类型表

| 类型 | 表达什么 | 常见场景 | 不适合什么 |
| --- | --- | --- | --- |
| `LocalDate` | 日期，不含时间 | `2026-06-29`、报表起止日期、生日 | 精确到秒的下单时间 |
| `LocalTime` | 时间，不含日期 | `10:30:00`、营业开始时间 | 某一天的具体时间点 |
| `LocalDateTime` | 日期 + 时间，不含时区 | `2026-06-29 10:30:00`、创建时间、更新时间、下单时间 | 跨时区绝对时间点 |
| `Instant` | UTC 时间线上的一个瞬间 | 时间戳、token 过期、系统日志 | 直接展示给用户看的本地时间 |
| `ZonedDateTime` | 日期时间 + 时区 | 跨国家业务、会议时间 | 普通单地区后台业务可能偏重 |
| `OffsetDateTime` | 日期时间 + UTC 偏移量 | 对接外部 API，带 `+08:00` 的时间 | 只关心本地业务时间的场景 |
| `Duration` | 两个时间点之间的时间量，偏“秒、分钟、小时” | 15 分钟未支付自动取消 | 几个月、几年这种日历概念 |
| `Period` | 两个日期之间的日历量，偏“年、月、日” | 会员有效期 1 个月、年龄 | 精确到秒的耗时 |
| `DateTimeFormatter` | 时间格式化/解析器 | `LocalDateTime` 和 `String` 互转 | 存业务时间本身 |
| `ZoneId` | 时区 ID | `Asia/Shanghai` | 单独表示时间 |

### 2.2 最常用的三个

在 Spring Boot 业务项目里，最常用的是：

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
```

对应关系：

```text
LocalDate      = 2026-06-29
LocalTime      = 14:30:00
LocalDateTime  = 2026-06-29 14:30:00
```

你的项目里也大量用到了这个模式：

```text
订单创建时间、支付时间、取消时间、配送时间  -> LocalDateTime
报表查询 begin、end 日期                  -> LocalDate
一天的开始和结束时间                      -> LocalTime
```

### 2.3 它们是不可变对象

`LocalDateTime`、`LocalDate`、`String` 都是不可变对象。

```java
LocalDateTime now = LocalDateTime.now();
LocalDateTime tomorrow = now.plusDays(1);

// now 没变，tomorrow 是一个新对象
```

所以写业务时要记住：`plusDays`、`minusHours`、`withXxx` 这些方法不会修改原对象，而是返回新对象。

---

## 3. LocalDate、LocalTime、LocalDateTime 常用能力

### 3.1 创建当前时间

```java
LocalDate today = LocalDate.now();
LocalTime nowTime = LocalTime.now();
LocalDateTime now = LocalDateTime.now();
```

业务含义：

```java
order.setOrderTime(LocalDateTime.now());
employee.setCreateTime(LocalDateTime.now());
employee.setUpdateTime(LocalDateTime.now());
```

### 3.2 手动创建指定时间

```java
LocalDate date = LocalDate.of(2026, 6, 29);
LocalTime time = LocalTime.of(14, 30, 0);
LocalDateTime dateTime = LocalDateTime.of(2026, 6, 29, 14, 30, 0);
```

也可以把日期和时间拼起来：

```java
LocalDate date = LocalDate.of(2026, 6, 29);

LocalDateTime start = LocalDateTime.of(date, LocalTime.MIN);
LocalDateTime end = LocalDateTime.of(date, LocalTime.MAX);
```

更常见的写法：

```java
LocalDateTime start = date.atStartOfDay();
LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();
```

`endExclusive` 表示“不包含明天 00:00:00”，做数据库查询时更稳：

```sql
where order_time >= start
  and order_time < endExclusive
```

### 3.3 日期、时间、日期时间互转

```java
LocalDateTime dateTime = LocalDateTime.now();

LocalDate date = dateTime.toLocalDate();
LocalTime time = dateTime.toLocalTime();
```

`LocalDate` 转 `LocalDateTime`：

```java
LocalDate date = LocalDate.of(2026, 6, 29);

LocalDateTime start = date.atStartOfDay();
LocalDateTime custom = date.atTime(14, 30, 0);
```

`LocalTime` 单独不能变成 `LocalDateTime`，因为它缺日期：

```java
LocalTime time = LocalTime.of(14, 30);
LocalDate date = LocalDate.now();

LocalDateTime dateTime = LocalDateTime.of(date, time);
```

### 3.4 加减时间

```java
LocalDateTime now = LocalDateTime.now();

LocalDateTime after15Minutes = now.plusMinutes(15);
LocalDateTime after1Hour = now.plusHours(1);
LocalDateTime tomorrow = now.plusDays(1);
LocalDateTime nextMonth = now.plusMonths(1);

LocalDateTime before15Minutes = now.minusMinutes(15);
LocalDateTime yesterday = now.minusDays(1);
```

业务例子：

```java
// 查找 15 分钟前创建、仍未支付的订单
LocalDateTime time = LocalDateTime.now().minusMinutes(15);
List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
```

### 3.5 修改某个字段

```java
LocalDateTime now = LocalDateTime.now();

LocalDateTime startOfHour = now.withMinute(0).withSecond(0).withNano(0);
LocalDateTime firstDayOfMonth = now.withDayOfMonth(1);
LocalDateTime firstMonth = now.withMonth(1);
```

它不是原地修改，而是返回新对象。

### 3.6 比较时间先后

```java
LocalDateTime orderTime = order.getOrderTime();
LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);

if (orderTime.isBefore(deadline)) {
    // 订单超过 15 分钟
}

if (orderTime.isAfter(deadline)) {
    // 订单还没超过 15 分钟
}

if (orderTime.isEqual(deadline)) {
    // 刚好相等
}
```

也可以用 `compareTo`：

```java
int result = orderTime.compareTo(deadline);

// result < 0: orderTime 更早
// result = 0: 相等
// result > 0: orderTime 更晚
```

### 3.7 计算两个时间之间的差

用 `Duration` 计算小时、分钟、秒：

```java
import java.time.Duration;

LocalDateTime begin = LocalDateTime.of(2026, 6, 29, 10, 0);
LocalDateTime end = LocalDateTime.of(2026, 6, 29, 11, 30);

Duration duration = Duration.between(begin, end);

long minutes = duration.toMinutes(); // 90
long hours = duration.toHours();     // 1
long seconds = duration.getSeconds();
```

用 `Period` 计算年、月、日：

```java
import java.time.LocalDate;
import java.time.Period;

LocalDate begin = LocalDate.of(2026, 1, 1);
LocalDate end = LocalDate.of(2026, 6, 29);

Period period = Period.between(begin, end);

int months = period.getMonths();
int days = period.getDays();
```

注意：`Duration` 更像“精确耗时”，`Period` 更像“日历差值”。

---

## 4. 格式化与解析：时间和字符串互转

后端接口开发中，最常见的转换是：

```text
前端传字符串 -> 后端解析成 LocalDateTime -> 参与业务计算/数据库查询
后端 LocalDateTime -> 序列化成字符串 -> 返回给前端展示
```

### 4.1 常见格式

| 字符串 | Java 类型 | pattern |
| --- | --- | --- |
| `2026-06-29` | `LocalDate` | `yyyy-MM-dd` |
| `14:30:00` | `LocalTime` | `HH:mm:ss` |
| `2026-06-29 14:30:00` | `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` |
| `2026-06-29T14:30:00` | `LocalDateTime` | ISO 默认格式 |

### 4.2 String 转 LocalDateTime

```java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

String text = "2026-06-29 14:30:00";
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

LocalDateTime time = LocalDateTime.parse(text, formatter);
```

如果字符串是 ISO 格式：

```java
String text = "2026-06-29T14:30:00";
LocalDateTime time = LocalDateTime.parse(text);
```

### 4.3 LocalDateTime 转 String

```java
LocalDateTime time = LocalDateTime.now();
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

String text = time.format(formatter);
```

### 4.4 LocalDate 和 LocalTime 的转换

```java
DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

LocalDate date = LocalDate.parse("2026-06-29", dateFormatter);
LocalTime time = LocalTime.parse("14:30:00", timeFormatter);

String dateText = date.format(dateFormatter);
String timeText = time.format(timeFormatter);
```

### 4.5 pattern 要记住的几个符号

| 符号 | 含义 | 示例 |
| --- | --- | --- |
| `yyyy` | 年 | `2026` |
| `MM` | 月 | `06` |
| `dd` | 日 | `29` |
| `HH` | 24 小时制小时 | `14` |
| `hh` | 12 小时制小时 | `02` |
| `mm` | 分钟 | `30` |
| `ss` | 秒 | `00` |
| `SSS` | 毫秒 | `123` |

最容易错的是：

```text
MM = 月
mm = 分钟

HH = 24 小时制
hh = 12 小时制

yyyy = 普通年份
YYYY = week-based-year，业务里一般不要用
```

### 4.6 DateTimeFormatter 是线程安全的

Java 8 之前常见的 `SimpleDateFormat` 不是线程安全的，不能随便做成全局静态变量。

`DateTimeFormatter` 是不可变且线程安全的，所以可以这样写：

```java
private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
```

---

## 5. 时区、时间戳、旧 Date 的边界

### 5.1 LocalDateTime 没有时区

这点非常关键：

```text
LocalDateTime = 本地日期时间
它只表示 2026-06-29 14:30:00
它不表示这个时间属于北京、东京、纽约，还是 UTC
```

所以 `LocalDateTime` 适合：

```text
订单创建时间
员工更新时间
菜品创建时间
报表查询条件
```

不适合单独表达：

```text
全球统一时间戳
跨时区会议时间
日志采集系统的绝对发生时刻
```

### 5.2 Instant 是绝对时间点

```java
import java.time.Instant;

Instant now = Instant.now();
long epochMilli = now.toEpochMilli();
```

`Instant` 更像 JS 里的时间戳概念：它在全球时间线上只有一个位置。

### 5.3 LocalDateTime 和 Instant 互转

必须提供时区：

```java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

ZoneId zoneId = ZoneId.of("Asia/Shanghai");

LocalDateTime localDateTime = LocalDateTime.now();
Instant instant = localDateTime.atZone(zoneId).toInstant();

LocalDateTime back = LocalDateTime.ofInstant(instant, zoneId);
```

如果不提供时区，Java 不知道 `2026-06-29 14:30:00` 应该解释成哪个地区的时间。

### 5.4 和旧 Date 互转

有些老库还在用 `java.util.Date`，例如 JWT、老 JDBC、部分第三方 SDK。

`Date -> LocalDateTime`：

```java
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

Date date = new Date();

LocalDateTime localDateTime = LocalDateTime.ofInstant(
        date.toInstant(),
        ZoneId.systemDefault()
);
```

`LocalDateTime -> Date`：

```java
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

LocalDateTime localDateTime = LocalDateTime.now();

Date date = Date.from(
        localDateTime.atZone(ZoneId.systemDefault()).toInstant()
);
```

### 5.5 和数据库 Timestamp 互转

MyBatis、JDBC 有时会碰到 `java.sql.Timestamp`：

```java
import java.sql.Timestamp;
import java.time.LocalDateTime;

LocalDateTime now = LocalDateTime.now();

Timestamp timestamp = Timestamp.valueOf(now);
LocalDateTime localDateTime = timestamp.toLocalDateTime();
```

现代 Spring Boot + MyBatis 项目里，很多时候可以直接用 `LocalDateTime` 映射 MySQL 的 `datetime` 字段。

---

## 6. Spring 接口服务中的时间处理

### 6.1 GET 查询参数绑定 LocalDate

例如报表查询：

```http
GET /admin/report/turnoverStatistics?begin=2026-06-01&end=2026-06-29
```

Controller：

```java
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@GetMapping("/turnoverStatistics")
public Result<TurnoverReportVO> turnoverStatistics(
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate begin,
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate end) {
    return Result.success(reportService.turnoverStatistics(begin, end));
}
```

如果是日期时间：

```http
GET /admin/orders?beginTime=2026-06-01 00:00:00&endTime=2026-06-29 23:59:59
```

```java
@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime beginTime;

@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime endTime;
```

### 6.2 JSON 请求体里的 LocalDateTime

前端 JSON：

```json
{
  "estimatedDeliveryTime": "2026-06-29 14:30:00"
}
```

DTO：

```java
private LocalDateTime estimatedDeliveryTime;
```

这时需要 Jackson 知道怎么解析 `LocalDateTime`。你的项目里有类似 `JacksonObjectMapper` 的配置：

```java
new JavaTimeModule()
        .addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        .addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
```

心智模型：

```text
GET 查询参数  -> Spring ConversionService + @DateTimeFormat
JSON 请求体   -> Jackson + JavaTimeModule / @JsonFormat
JSON 响应体   -> Jackson 序列化 LocalDateTime
数据库字段    -> MyBatis/JDBC 类型映射
```

### 6.3 日期范围查询推荐写法

用户选择的是日期：

```text
begin = 2026-06-01
end   = 2026-06-29
```

数据库字段是日期时间：

```text
order_time = 2026-06-29 18:30:00
```

推荐转换：

```java
LocalDateTime beginTime = begin.atStartOfDay();
LocalDateTime endTimeExclusive = end.plusDays(1).atStartOfDay();
```

SQL：

```sql
where order_time >= #{beginTime}
  and order_time < #{endTimeExclusive}
```

不太推荐长期依赖：

```java
LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
```

原因：数据库精度可能是秒、毫秒、微秒，`LocalTime.MAX` 是 `23:59:59.999999999`，有时会引出边界问题。半开区间 `[begin, endExclusive)` 更稳定。

### 6.4 DTO、Entity、VO 里的时间怎么选

```text
DTO：前端传进来的数据
Entity：数据库表对应的数据
VO：返回给前端展示的数据
```

常见选择：

```java
// DTO
private LocalDate begin;
private LocalDate end;
private LocalDateTime estimatedDeliveryTime;

// Entity
private LocalDateTime createTime;
private LocalDateTime updateTime;
private LocalDateTime orderTime;

// VO
private LocalDateTime orderTime;
private String orderTimeText; // 如果前端强要求某种展示格式，也可以额外给 String
```

通常建议：后端内部尽量用时间类型，别太早转成字符串。字符串更适合放在接口边界和展示层。

---

## 7. String 的常用能力

### 7.1 String 是不可变对象

```java
String name = "Tom";
String upper = name.toUpperCase();

// name 还是 "Tom"
// upper 是 "TOM"
```

频繁拼接字符串时，优先用 `StringBuilder`：

```java
StringBuilder sb = new StringBuilder();
sb.append("订单号：");
sb.append(orderNo);
sb.append("，金额：");
sb.append(amount);

String result = sb.toString();
```

### 7.2 判断空字符串

Java 8 没有 `String.isBlank()`，这是 Java 11 才有的。

Java 8 常用：

```java
String text = "  ";

if (text == null || text.trim().isEmpty()) {
    // null、空字符串、全空格
}
```

如果只判断长度为 0：

```java
if (text != null && text.isEmpty()) {
    // ""
}
```

### 7.3 比较字符串

不要用 `==` 比较内容：

```java
String a = new String("admin");
String b = new String("admin");

System.out.println(a == b);      // false，比较引用地址
System.out.println(a.equals(b)); // true，比较内容
```

推荐常量放前面，避免空指针：

```java
if ("admin".equals(username)) {
    // ...
}
```

忽略大小写：

```java
if ("admin".equalsIgnoreCase(username)) {
    // ...
}
```

### 7.4 查找、截取、替换

```java
String text = "order:202606290001";

boolean hasOrder = text.contains("order");
boolean starts = text.startsWith("order");
boolean ends = text.endsWith("0001");

int index = text.indexOf(":");
String prefix = text.substring(0, index);
String orderNo = text.substring(index + 1);

String replaced = text.replace("order", "ORDER");
```

### 7.5 分割和拼接

```java
String ids = "1,2,3";
String[] array = ids.split(",");
```

拼接数组或列表：

```java
String[] names = {"Tom", "Jerry", "Alice"};
String text = String.join(",", names);
```

`String.join` 也支持 `Iterable<String>`：

```java
List<String> names = Arrays.asList("Tom", "Jerry", "Alice");
String text = String.join(",", names);
```

### 7.6 类型转字符串

```java
String s1 = String.valueOf(123);
String s2 = String.valueOf(LocalDateTime.now());
String s3 = String.valueOf(null); // "null"
```

如果是时间，建议用 `format` 控制格式：

```java
String text = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
```

---

## 8. 数组的常用能力

### 8.1 数组是什么

数组是固定长度容器：

```java
String[] names = new String[3];
names[0] = "Tom";
names[1] = "Jerry";
names[2] = "Alice";
```

也可以直接初始化：

```java
String[] names = {"Tom", "Jerry", "Alice"};
int[] nums = {1, 2, 3};
```

特点：

```text
长度固定：创建后不能变长
访问很快：通过下标访问
功能偏少：增删不方便
```

### 8.2 常用操作

```java
String[] names = {"Tom", "Jerry", "Alice"};

int length = names.length;
String first = names[0];
names[1] = "Jack";
```

遍历：

```java
for (int i = 0; i < names.length; i++) {
    System.out.println(names[i]);
}

for (String name : names) {
    System.out.println(name);
}
```

### 8.3 Arrays 工具类

```java
import java.util.Arrays;

int[] nums = {3, 1, 2};

Arrays.sort(nums);
System.out.println(Arrays.toString(nums)); // [1, 2, 3]
```

常用能力：

```java
Arrays.sort(nums);              // 排序
Arrays.copyOf(nums, 5);         // 复制并指定新长度
Arrays.fill(nums, 0);           // 填充
Arrays.toString(nums);          // 一维数组转可读字符串
Arrays.deepToString(objects);   // 多维数组转可读字符串
```

### 8.4 基本类型数组和对象数组不同

```java
int[] nums = {1, 2, 3};             // 基本类型数组
Integer[] integers = {1, 2, 3};     // 对象数组
String[] names = {"Tom", "Jerry"};  // 对象数组
```

这个区别会影响 `Arrays.asList` 和 Stream 转换，后面会讲。

---

## 9. List 的常用能力

### 9.1 List 是什么

`List<T>` 是有序、可重复、长度可变的集合。

```java
import java.util.ArrayList;
import java.util.List;

List<String> names = new ArrayList<>();
names.add("Tom");
names.add("Jerry");
names.add("Tom");
```

特点：

```text
有顺序：第 0 个、第 1 个
可重复：可以有两个 Tom
可变长：可以 add/remove
泛型约束：List<String> 只能放 String
```

后端业务中，`List` 比数组更常用。

### 9.2 常用方法

```java
List<String> names = new ArrayList<>();

names.add("Tom");
names.add("Jerry");

String first = names.get(0);
names.set(0, "Alice");
names.remove("Jerry");

boolean contains = names.contains("Alice");
boolean empty = names.isEmpty();
int size = names.size();
```

遍历：

```java
for (String name : names) {
    System.out.println(name);
}
```

### 9.3 List 排序

```java
List<Integer> nums = Arrays.asList(3, 1, 2);
nums.sort(Integer::compareTo);
```

对象排序：

```java
orders.sort(Comparator.comparing(Orders::getOrderTime));
```

倒序：

```java
orders.sort(Comparator.comparing(Orders::getOrderTime).reversed());
```

### 9.4 Stream 是批量转换工具

Java 8 的 Stream 很适合处理 `List<T> -> List<R>`：

```java
List<OrderDetail> orderDetailList = orderDetailsMapper.list(orderId);

List<String> names = orderDetailList.stream()
        .map(OrderDetail::getName)
        .collect(Collectors.toList());
```

常用能力：

```java
list.stream()
    .filter(...)   // 过滤
    .map(...)      // 转换
    .sorted(...)   // 排序
    .collect(...)  // 收集成 List、Map、String 等
```

Java 8 里没有 `stream().toList()`，要写：

```java
.collect(Collectors.toList())
```

### 9.5 List 转 Map

按 id 查对象时很常见：

```java
Map<Long, Dish> dishMap = dishList.stream()
        .collect(Collectors.toMap(Dish::getId, dish -> dish));
```

如果 key 可能重复，需要处理冲突：

```java
Map<Long, Dish> dishMap = dishList.stream()
        .collect(Collectors.toMap(
                Dish::getId,
                dish -> dish,
                (oldValue, newValue) -> newValue
        ));
```

### 9.6 分组

例如把口味按菜品 id 分组：

```java
Map<Long, List<DishFlavor>> flavorMap = dishFlavors.stream()
        .collect(Collectors.groupingBy(DishFlavor::getDishId));
```

能力边界：

```text
List<T> 可以很方便转成 List<R>
List<T> 可以很方便转成 Map<K, T>
List<T> 可以按某个字段分组成 Map<K, List<T>>
List<T> 可以过滤、排序、去重、求最大最小
```

---

## 10. String、数组、List 之间的转换

这一节是后端接口里最常用的转换套路。

### 10.1 String -> String[]

```java
String text = "1,2,3";
String[] array = text.split(",");
```

注意：`split` 参数是正则表达式。

如果按点号分割，要转义：

```java
String ip = "127.0.0.1";
String[] parts = ip.split("\\.");
```

### 10.2 String[] -> String

```java
String[] array = {"1", "2", "3"};
String text = String.join(",", array);
```

### 10.3 String[] -> List<String>

```java
String[] array = {"1", "2", "3"};
List<String> list = Arrays.asList(array);
```

注意：`Arrays.asList(array)` 返回的 List 长度固定，不能 add/remove：

```java
List<String> list = Arrays.asList(array);
list.add("4"); // 运行时报错 UnsupportedOperationException
```

如果要可变 List：

```java
List<String> list = new ArrayList<>(Arrays.asList(array));
list.add("4");
```

### 10.4 List<String> -> String[]

```java
List<String> list = Arrays.asList("1", "2", "3");
String[] array = list.toArray(new String[0]);
```

### 10.5 List<String> -> String

```java
List<String> list = Arrays.asList("1", "2", "3");
String text = String.join(",", list);
```

如果不是 `List<String>`，先 `map` 成字符串：

```java
List<Long> ids = Arrays.asList(1L, 2L, 3L);

String text = ids.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
```

### 10.6 String -> List<String>

```java
String text = "1,2,3";

List<String> list = Arrays.asList(text.split(","));
```

如果要可变：

```java
List<String> list = new ArrayList<>(Arrays.asList(text.split(",")));
```

### 10.7 String -> List<Long>

接口里经常有 `"1,2,3"` 这种 id 字符串：

```java
String text = "1,2,3";

List<Long> ids = Arrays.stream(text.split(","))
        .map(Long::valueOf)
        .collect(Collectors.toList());
```

如果可能有空格：

```java
List<Long> ids = Arrays.stream(text.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Long::valueOf)
        .collect(Collectors.toList());
```

### 10.8 List<Long> -> String

```java
List<Long> ids = Arrays.asList(1L, 2L, 3L);

String text = ids.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
```

### 10.9 数组 -> List

对象数组：

```java
String[] array = {"Tom", "Jerry"};
List<String> list = Arrays.asList(array);
```

基本类型数组要特别注意：

```java
int[] nums = {1, 2, 3};

List<int[]> wrong = Arrays.asList(nums); // 不是 List<Integer>
```

正确写法：

```java
int[] nums = {1, 2, 3};

List<Integer> list = Arrays.stream(nums)
        .boxed()
        .collect(Collectors.toList());
```

`long[]` 同理：

```java
long[] ids = {1L, 2L, 3L};

List<Long> list = Arrays.stream(ids)
        .boxed()
        .collect(Collectors.toList());
```

### 10.10 List -> 数组

对象数组：

```java
List<String> list = Arrays.asList("Tom", "Jerry");
String[] array = list.toArray(new String[0]);
```

`List<Integer> -> int[]`：

```java
List<Integer> list = Arrays.asList(1, 2, 3);

int[] array = list.stream()
        .mapToInt(Integer::intValue)
        .toArray();
```

`List<Long> -> long[]`：

```java
List<Long> list = Arrays.asList(1L, 2L, 3L);

long[] array = list.stream()
        .mapToLong(Long::longValue)
        .toArray();
```

---

## 11. List\<LocalDateTime\> 这种组合类型怎么处理

`List<LocalDateTime>` 的本质是：

```text
一个有序集合，里面每个元素都是一个日期时间对象
```

所以你可以同时使用：

```text
List 的能力：遍历、过滤、排序、map、collect
LocalDateTime 的能力：parse、format、plus、minus、isBefore、toLocalDate
```

### 11.1 List<String> -> List<LocalDateTime>

前端传来一组时间字符串：

```java
List<String> timeTexts = Arrays.asList(
        "2026-06-29 10:00:00",
        "2026-06-29 11:00:00",
        "2026-06-29 12:00:00"
);

DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

List<LocalDateTime> times = timeTexts.stream()
        .map(text -> LocalDateTime.parse(text, formatter))
        .collect(Collectors.toList());
```

### 11.2 List<LocalDateTime> -> List<String>

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

List<String> timeTexts = times.stream()
        .map(time -> time.format(formatter))
        .collect(Collectors.toList());
```

### 11.3 String -> List<LocalDateTime>

如果字符串是逗号分隔：

```java
String text = "2026-06-29 10:00:00,2026-06-29 11:00:00,2026-06-29 12:00:00";
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

List<LocalDateTime> times = Arrays.stream(text.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> LocalDateTime.parse(s, formatter))
        .collect(Collectors.toList());
```

### 11.4 List<LocalDateTime> -> String

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

String text = times.stream()
        .map(time -> time.format(formatter))
        .collect(Collectors.joining(","));
```

### 11.5 排序

```java
times.sort(LocalDateTime::compareTo);
```

倒序：

```java
times.sort(Comparator.reverseOrder());
```

如果不想修改原 List：

```java
List<LocalDateTime> sorted = times.stream()
        .sorted()
        .collect(Collectors.toList());
```

### 11.6 过滤时间范围

```java
LocalDateTime begin = LocalDateTime.of(2026, 6, 29, 10, 0);
LocalDateTime end = LocalDateTime.of(2026, 6, 29, 12, 0);

List<LocalDateTime> result = times.stream()
        .filter(time -> !time.isBefore(begin))
        .filter(time -> time.isBefore(end))
        .collect(Collectors.toList());
```

这里是半开区间：

```text
[begin, end)
包含 begin
不包含 end
```

### 11.7 找最早和最晚时间

```java
Optional<LocalDateTime> min = times.stream().min(LocalDateTime::compareTo);
Optional<LocalDateTime> max = times.stream().max(LocalDateTime::compareTo);
```

使用：

```java
if (min.isPresent()) {
    LocalDateTime earliest = min.get();
}
```

业务里也可以给默认值：

```java
LocalDateTime earliest = times.stream()
        .min(LocalDateTime::compareTo)
        .orElse(null);
```

### 11.8 List<LocalDateTime> -> List<LocalDate>

只保留日期：

```java
List<LocalDate> dates = times.stream()
        .map(LocalDateTime::toLocalDate)
        .collect(Collectors.toList());
```

去重：

```java
List<LocalDate> dates = times.stream()
        .map(LocalDateTime::toLocalDate)
        .distinct()
        .collect(Collectors.toList());
```

### 11.9 按日期分组

```java
Map<LocalDate, List<LocalDateTime>> groupByDate = times.stream()
        .collect(Collectors.groupingBy(LocalDateTime::toLocalDate));
```

含义：

```text
2026-06-29 -> [2026-06-29 10:00:00, 2026-06-29 11:00:00]
2026-06-30 -> [2026-06-30 09:00:00]
```

如果是订单列表，按下单日期分组：

```java
Map<LocalDate, List<Orders>> orderMap = ordersList.stream()
        .collect(Collectors.groupingBy(order -> order.getOrderTime().toLocalDate()));
```

### 11.10 生成一段日期列表

报表里经常需要从 `begin` 到 `end` 生成每天的日期：

```java
List<LocalDate> dateList = new ArrayList<>();

LocalDate current = begin;
while (!current.isAfter(end)) {
    dateList.add(current);
    current = current.plusDays(1);
}
```

如果要把日期列表转成前端图表需要的字符串：

```java
String dateText = dateList.stream()
        .map(date -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        .collect(Collectors.joining(","));
```

---

## 12. API 接口开发中的典型转换场景

### 12.1 批量删除：请求参数转 List<Long>

接口：

```http
DELETE /admin/dish?ids=1,2,3
```

Controller：

```java
@DeleteMapping
public Result delete(@RequestParam List<Long> ids) {
    dishService.deleteBatch(ids);
    return Result.success();
}
```

Spring 通常可以把 `ids=1,2,3` 或 `ids=1&ids=2&ids=3` 绑定成 `List<Long>`。

如果你手动处理：

```java
String idsText = "1,2,3";

List<Long> ids = Arrays.stream(idsText.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Long::valueOf)
        .collect(Collectors.toList());
```

### 12.2 报表接口：LocalDate -> 日期列表 -> 字符串

前端传：

```http
GET /admin/report/turnoverStatistics?begin=2026-06-01&end=2026-06-07
```

后端做三件事：

```text
1. begin/end 绑定为 LocalDate
2. 生成 begin 到 end 的 List<LocalDate>
3. 每天查营业额，生成 List<Double>
4. 两个 List 转成逗号分隔字符串给前端图表
```

示例：

```java
List<LocalDate> dateList = new ArrayList<>();
LocalDate current = begin;

while (!current.isAfter(end)) {
    dateList.add(current);
    current = current.plusDays(1);
}

List<Double> turnoverList = new ArrayList<>();

for (LocalDate date : dateList) {
    LocalDateTime beginTime = date.atStartOfDay();
    LocalDateTime endTime = date.plusDays(1).atStartOfDay();

    Double turnover = orderMapper.sumByMap(beginTime, endTime);
    turnoverList.add(turnover == null ? 0.0 : turnover);
}

String dateText = dateList.stream()
        .map(date -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        .collect(Collectors.joining(","));

String turnoverText = turnoverList.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
```

不要先把整个 List 变成一个字符串再 join：

```java
// 不推荐
String.valueOf(dateList); // 得到类似 [2026-06-01, 2026-06-02]
```

推荐逐个元素转换：

```java
dateList.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
```

### 12.3 订单超时：LocalDateTime 比较

```java
LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);

List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(
        Orders.PENDING_PAYMENT,
        deadline
);

for (Orders order : ordersList) {
    order.setStatus(Orders.CANCELLED);
    order.setCancelTime(LocalDateTime.now());
    order.setCancelReason("订单超时，自动取消");
    orderMapper.update(order);
}
```

这里的能力链路是：

```text
当前时间 LocalDateTime.now()
减去 15 分钟 minusMinutes(15)
传给 Mapper 做数据库条件
查出 List<Orders>
遍历 List，修改每个订单
```

### 12.4 List<Entity> -> List<VO>

这是后端最常见的集合转换。

```java
List<Orders> ordersList = orderMapper.list();

List<OrderVO> voList = ordersList.stream()
        .map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);
            return vo;
        })
        .collect(Collectors.toList());
```

如果每个 VO 还要塞详情列表：

```java
List<OrderVO> voList = ordersList.stream()
        .map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);

            List<OrderDetail> detailList = orderDetailsMapper.list(order.getId());
            vo.setOrderDetailList(detailList);

            return vo;
        })
        .collect(Collectors.toList());
```

这个模式你要非常熟：

```text
List<A> -> stream -> map 每个 A 变 B -> collect -> List<B>
```

### 12.5 MyBatis foreach：List 传给 SQL

Mapper：

```java
void deleteBatch(List<Long> ids);
```

XML：

```xml
<delete id="deleteBatch">
    delete from dish
    where id in
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</delete>
```

能力边界：

```text
Java 里是 List<Long>
SQL 里需要变成 in (1, 2, 3)
MyBatis foreach 负责把 List 展开
```

---

## 13. 常见坑位

### 13.1 `LocalDateTime` 不是时间戳

```text
LocalDateTime: 2026-06-29 14:30:00，没有时区
Instant: 全球统一时间线上的一个点
long: 毫秒时间戳
```

如果要转时间戳，必须指定时区：

```java
long millis = localDateTime
        .atZone(ZoneId.of("Asia/Shanghai"))
        .toInstant()
        .toEpochMilli();
```

### 13.2 `yyyy` 不要写成 `YYYY`

```java
DateTimeFormatter.ofPattern("yyyy-MM-dd"); // 推荐
DateTimeFormatter.ofPattern("YYYY-MM-dd"); // 容易出跨年周问题
```

### 13.3 `MM` 和 `mm` 不是一回事

```text
MM = 月
mm = 分钟
```

错误：

```java
DateTimeFormatter.ofPattern("yyyy-mm-dd HH:MM:ss");
```

正确：

```java
DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
```

### 13.4 `Arrays.asList` 得到的 List 不能增删

```java
List<String> list = Arrays.asList("a", "b");
list.add("c"); // 报错
```

需要可变：

```java
List<String> list = new ArrayList<>(Arrays.asList("a", "b"));
list.add("c");
```

### 13.5 基本类型数组不能直接 asList 成包装类型列表

```java
int[] nums = {1, 2, 3};
List<int[]> wrong = Arrays.asList(nums);
```

正确：

```java
List<Integer> list = Arrays.stream(nums)
        .boxed()
        .collect(Collectors.toList());
```

### 13.6 Java 8 没有 `List.of` 和 `Stream.toList`

这两个是更高版本 Java 的 API。

Java 8 写法：

```java
List<String> list = Arrays.asList("a", "b", "c");

List<String> result = list.stream()
        .filter(s -> s.startsWith("a"))
        .collect(Collectors.toList());
```

### 13.7 字符串内容比较不要用 `==`

```java
if (username == "admin") {
    // 不推荐
}

if ("admin".equals(username)) {
    // 推荐
}
```

### 13.8 `split` 是正则

```java
"127.0.0.1".split(".");   // 错误含义，. 在正则里表示任意字符
"127.0.0.1".split("\\."); // 正确
```

### 13.9 边遍历边 remove 容易出问题

不要这样：

```java
for (String name : names) {
    if (name.startsWith("A")) {
        names.remove(name);
    }
}
```

推荐 Java 8：

```java
names.removeIf(name -> name.startsWith("A"));
```

或者生成新 List：

```java
List<String> result = names.stream()
        .filter(name -> !name.startsWith("A"))
        .collect(Collectors.toList());
```

### 13.10 不要太早把时间转成 String

不推荐：

```java
String orderTime = LocalDateTime.now().format(formatter);
// 后面还要比较、加减、查数据库，就麻烦了
```

推荐：

```java
LocalDateTime orderTime = LocalDateTime.now();
// 业务内部一直用 LocalDateTime
// 到返回前端或写日志时再格式化
```

---

## 14. 能力边界速查表

### 14.1 时间能力

| 我想做什么 | 用什么类型/方法 |
| --- | --- |
| 当前日期 | `LocalDate.now()` |
| 当前日期时间 | `LocalDateTime.now()` |
| 今天 00:00:00 | `LocalDate.now().atStartOfDay()` |
| 某天开始 | `date.atStartOfDay()` |
| 某天结束边界 | `date.plusDays(1).atStartOfDay()` 作为不包含的右边界 |
| 下单时间加 30 分钟 | `orderTime.plusMinutes(30)` |
| 当前时间减 15 分钟 | `LocalDateTime.now().minusMinutes(15)` |
| 判断 A 是否早于 B | `a.isBefore(b)` |
| 判断 A 是否晚于 B | `a.isAfter(b)` |
| 计算两个时间差多少分钟 | `Duration.between(a, b).toMinutes()` |
| 字符串转日期 | `LocalDate.parse(text, formatter)` |
| 字符串转日期时间 | `LocalDateTime.parse(text, formatter)` |
| 日期时间转字符串 | `time.format(formatter)` |
| LocalDateTime 转 LocalDate | `time.toLocalDate()` |
| LocalDate 转 LocalDateTime | `date.atStartOfDay()` / `date.atTime(...)` |
| LocalDateTime 转时间戳 | `time.atZone(zone).toInstant().toEpochMilli()` |
| 时间戳转 LocalDateTime | `LocalDateTime.ofInstant(instant, zone)` |

### 14.2 String / 数组 / List 转换能力

| 从哪里到哪里 | 典型写法 |
| --- | --- |
| `String -> String[]` | `text.split(",")` |
| `String[] -> String` | `String.join(",", array)` |
| `String[] -> List<String>` | `Arrays.asList(array)` |
| `String[] -> 可变 List<String>` | `new ArrayList<>(Arrays.asList(array))` |
| `List<String> -> String[]` | `list.toArray(new String[0])` |
| `List<String> -> String` | `String.join(",", list)` |
| `String -> List<String>` | `Arrays.asList(text.split(","))` |
| `String -> List<Long>` | `Arrays.stream(text.split(",")).map(Long::valueOf).collect(Collectors.toList())` |
| `List<Long> -> String` | `ids.stream().map(String::valueOf).collect(Collectors.joining(","))` |
| `int[] -> List<Integer>` | `Arrays.stream(nums).boxed().collect(Collectors.toList())` |
| `List<Integer> -> int[]` | `list.stream().mapToInt(Integer::intValue).toArray()` |
| `List<A> -> List<B>` | `list.stream().map(a -> b).collect(Collectors.toList())` |
| `List<T> -> Map<K, T>` | `list.stream().collect(Collectors.toMap(...))` |
| `List<T> 按字段分组` | `list.stream().collect(Collectors.groupingBy(...))` |

### 14.3 List\<LocalDateTime\> 能力

| 我想做什么 | 典型写法 |
| --- | --- |
| 字符串列表转时间列表 | `texts.stream().map(t -> LocalDateTime.parse(t, formatter)).collect(Collectors.toList())` |
| 时间列表转字符串列表 | `times.stream().map(t -> t.format(formatter)).collect(Collectors.toList())` |
| 时间列表转逗号字符串 | `times.stream().map(t -> t.format(formatter)).collect(Collectors.joining(","))` |
| 排序 | `times.stream().sorted().collect(Collectors.toList())` |
| 过滤某个时间范围 | `filter(t -> !t.isBefore(begin) && t.isBefore(end))` |
| 找最早时间 | `times.stream().min(LocalDateTime::compareTo)` |
| 找最晚时间 | `times.stream().max(LocalDateTime::compareTo)` |
| 提取日期列表 | `times.stream().map(LocalDateTime::toLocalDate).collect(Collectors.toList())` |
| 按日期分组 | `times.stream().collect(Collectors.groupingBy(LocalDateTime::toLocalDate))` |

---

## 15. 最后给你的学习抓手

你不需要把所有 API 都背下来，先记住这几条就够你判断“能不能实现”：

1. **时间类型能做创建、解析、格式化、加减、比较、拆分、组合。**
   - `String <-> LocalDateTime`
   - `LocalDate <-> LocalDateTime`
   - `LocalDateTime <-> Instant/Date`

2. **集合类型能做遍历、过滤、转换、排序、分组、聚合。**
   - `List<A> -> List<B>` 用 `map`
   - 只保留一部分用 `filter`
   - 拼成字符串用 `Collectors.joining`
   - 按字段分组用 `groupingBy`

3. **字符串适合接口边界和展示，不适合长期承载业务含义。**
   - 进入后端后，尽早转成 `Long`、`BigDecimal`、`LocalDateTime`
   - 返回前端前，再按需要转成字符串

4. **数组更底层，List 更业务。**
   - 接口、Mapper、Service 里优先想 `List<T>`
   - 和老 API、工具类、底层数据结构对接时才经常用数组

5. **组合类型不用怕，拆开看。**
   - `List<LocalDateTime>` = `List` 的批量能力 + `LocalDateTime` 的时间能力
   - `List<OrderVO>` = `List` 的批量能力 + `OrderVO` 的字段结构
   - `Map<LocalDate, List<Orders>>` = 按日期索引的一批订单

当你看到一个业务需求时，可以先用这句话拆：

```text
这是一个值，还是一组值？
这个值要计算，还是展示？
这组值要过滤、转换、排序、分组，还是拼接？
这个时间有没有时区和边界问题？
```

只要这个判断清楚，具体 API 名称忘了也没关系，AI 可以帮你补齐代码细节。
