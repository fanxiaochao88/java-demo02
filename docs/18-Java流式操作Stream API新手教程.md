# 18 - Java 流式操作 Stream API 新手教程

> 这篇是写给“看着 Stream 很方便，但脑子里还没有图”的阶段。
>
> 你不需要一开始就背完所有 API。先抓住一个核心：**Stream 不是一种新集合，而是一条处理数据的流水线。**

---

## 目录

1. [先给结论：Stream 到底解决什么问题](#1-先给结论stream-到底解决什么问题)
2. [先建立一张心智图](#2-先建立一张心智图)
3. [Stream 和 List、Map 有什么区别](#3-stream-和-listmap-有什么区别)
4. [一条 Stream 代码怎么读](#4-一条-stream-代码怎么读)
5. [最常用的三类操作](#5-最常用的三类操作)
6. [filter：过滤数据](#6-filter过滤数据)
7. [map：把一种对象变成另一种对象](#7-map把一种对象变成另一种对象)
8. [collect：把流水线结果收集回来](#8-collect把流水线结果收集回来)
9. [Collectors.toMap：List 转 Map](#9-collectorstomaplist-转-map)
10. [Collectors.joining：拼接字符串](#10-collectorsjoining拼接字符串)
11. [mapToInt、sum、count：统计类操作](#11-maptointsumcount统计类操作)
12. [sorted、distinct、limit、skip：排序去重分页](#12-sorteddistinctlimitskip排序去重分页)
13. [flatMap：把多层集合压平](#13-flatmap把多层集合压平)
14. [Optional：处理可能不存在的结果](#14-optional处理可能不存在的结果)
15. [reduce：能不用就先少用](#15-reduce能不用就先少用)
16. [项目里的 Stream 代码怎么读](#16-项目里的-stream-代码怎么读)
17. [什么时候适合用 Stream，什么时候别硬用](#17-什么时候适合用-stream什么时候别硬用)
18. [常见坑位](#18-常见坑位)
19. [API 速查表](#19-api-速查表)
20. [练习题：照着项目业务练](#20-练习题照着项目业务练)
21. [最后给你的学习抓手](#21-最后给你的学习抓手)

---

## 1. 先给结论：Stream 到底解决什么问题

后端业务代码里经常要处理一批数据：

```text
一批订单
一批菜品
一批日期
一批统计结果
一批用户
```

拿到这批数据以后，常见动作无非是这些：

```text
过滤：只要已完成订单
转换：OrderDetail 转 ShoppingCart
提取：从 DishVO 里只拿 id
补齐：某天没有数据就补 0
分组：按状态、日期、分类分组
转 Map：按 id 或日期快速查询
拼接：把日期列表拼成 "2026-07-01,2026-07-02"
统计：求和、计数、最大值、最小值
```

以前用 `for` 循环当然也能写：

```java
List<Long> dishIds = new ArrayList<>();
for (DishVO dish : records) {
    dishIds.add(dish.getId());
}
```

Stream 写法是：

```java
List<Long> dishIds = records.stream()
        .map(DishVO::getId)
        .collect(Collectors.toList());
```

它不是“更高级的 for 循环”这么简单。

Stream 的价值在于：**你可以把一批数据的处理过程写成一条从左到右的流水线。**

```text
records
  -> 变成流
  -> 每个 DishVO 提取 id
  -> 收集成 List<Long>
```

所以你看 Stream 代码时，不要一开始纠结语法，先问一句：

```text
这批数据从哪里来？
中间经历了哪些处理？
最后变成了什么？
```

---

## 2. 先建立一张心智图

一条 Stream 通常分三段：

```text
数据源
  .stream()
  .中间操作1()
  .中间操作2()
  .终止操作()
```

例如：

```java
List<String> names = users.stream()
        .filter(user -> user.getStatus() == 1)
        .map(User::getName)
        .collect(Collectors.toList());
```

可以翻译成：

```text
数据源：users
中间操作1：filter，过滤出 status == 1 的用户
中间操作2：map，把 User 转成 name
终止操作：collect，收集成 List<String>
```

再换成更像流水线的图：

```text
List<User>
   |
   | stream()
   v
Stream<User>
   |
   | filter(user -> user.getStatus() == 1)
   v
Stream<User>
   |
   | map(User::getName)
   v
Stream<String>
   |
   | collect(Collectors.toList())
   v
List<String>
```

关键点：

1. `stream()` 之后，数据进入流水线。
2. `filter`、`map`、`sorted` 这类是中间操作，还在流水线里。
3. `collect`、`count`、`sum`、`findFirst` 这类是终止操作，流水线结束并产出结果。

---

## 3. Stream 和 List、Map 有什么区别

初学时最容易把 Stream 当成 List 的升级版，这是不准确的。

| 类型 | 它是什么 | 能不能存数据 | 常见作用 |
| --- | --- | --- | --- |
| `List<T>` | 集合容器 | 能 | 存一批有顺序的数据 |
| `Set<T>` | 集合容器 | 能 | 存一批不重复的数据 |
| `Map<K, V>` | 键值容器 | 能 | 按 key 快速找 value |
| `Stream<T>` | 数据处理流水线 | 不能长期存 | 过滤、转换、统计、收集 |

一句话：

```text
List / Set / Map 是仓库。
Stream 是传送带。
```

你从仓库里拿出一批数据，放上传送带，经过过滤、转换、统计，最后再装回某个容器，或者得到一个统计结果。

```java
List<Order> orders = orderMapper.list();

List<Order> completedOrders = orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
        .collect(Collectors.toList());
```

这里：

```text
orders 是 List，是原始数据仓库。
orders.stream() 是把数据放到流水线上。
filter 是流水线中的筛选工位。
collect(Collectors.toList()) 是把结果重新装回 List。
```

---

## 4. 一条 Stream 代码怎么读

读 Stream 代码时，建议按四步读。

### 4.1 第一步：看数据源

```java
orderDetailList.stream()
```

先确认这批数据是什么类型。

```text
orderDetailList 是 List<OrderDetail>
所以 stream() 之后是 Stream<OrderDetail>
```

### 4.2 第二步：看每一步输入和输出

```java
orderDetailList.stream()
        .map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        })
```

这里 `map` 做的是：

```text
输入一个 OrderDetail
输出一个 ShoppingCart
```

所以：

```text
Stream<OrderDetail>
  -> map(...)
  -> Stream<ShoppingCart>
```

### 4.3 第三步：看最后收集成什么

```java
.collect(Collectors.toList())
```

表示最后收集成：

```text
List<ShoppingCart>
```

完整读法：

```text
把订单详情列表 orderDetailList 放入流水线，
每个 OrderDetail 转成一个 ShoppingCart，
最后收集成购物车列表 shoppingCartList。
```

### 4.4 第四步：再看业务意图

不要只翻译语法，还要翻译业务：

```text
再来一单：
  查询原订单详情
  把每个订单详情转换为购物车项
  批量插入购物车
```

Stream 最终还是为业务服务。你能把它读成业务动作，才算真的看懂。

---

## 5. 最常用的三类操作

Stream API 很多，但后端业务最常用的先记住这些就够用。

### 5.1 过滤类

```java
filter
```

作用：

```text
从一批数据中筛选出符合条件的数据。
```

### 5.2 转换类

```java
map
flatMap
```

作用：

```text
把一种数据变成另一种数据。
```

### 5.3 收集和统计类

```java
collect
count
sum
max
min
joining
toMap
groupingBy
```

作用：

```text
把流水线结果变成 List、Set、Map、字符串，或者统计值。
```

初学阶段先盯住一个公式：

```text
stream()
  .filter(...)
  .map(...)
  .collect(...)
```

大部分业务代码都是这个公式的变体。

---

## 6. filter：过滤数据

`filter` 的作用是保留满足条件的数据。

### 6.1 基础写法

```java
List<Order> completedOrders = orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
        .collect(Collectors.toList());
```

读法：

```text
遍历 orders 中的每个 order，
只保留状态为已完成的订单，
最后收集成 List<Order>。
```

### 6.2 多条件过滤

```java
List<Order> result = orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
        .filter(order -> order.getAmount().compareTo(BigDecimal.ZERO) > 0)
        .collect(Collectors.toList());
```

也可以写成一个 `filter`：

```java
List<Order> result = orders.stream()
        .filter(order ->
                order.getStatus().equals(Orders.COMPLETED)
                        && order.getAmount().compareTo(BigDecimal.ZERO) > 0)
        .collect(Collectors.toList());
```

怎么选：

```text
条件简单：写在一个 filter 里。
条件较多：拆成多个 filter，可读性更好。
```

### 6.3 过滤 null

```java
List<String> names = users.stream()
        .map(User::getName)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
```

需要导入：

```java
import java.util.Objects;
```

读法：

```text
先提取所有用户名，
再过滤掉 null，
最后收集成 List<String>。
```

---

## 7. map：把一种对象变成另一种对象

`map` 是 Stream 里最重要的方法之一。

它的核心不是“遍历”，而是“转换”。

```text
T -> R
```

表示：

```text
输入一种类型 T，
输出另一种类型 R。
```

### 7.1 提取字段

```java
List<Long> dishIds = records.stream()
        .map(DishVO::getId)
        .collect(Collectors.toList());
```

读法：

```text
把 List<DishVO>
转换成 List<Long>
```

其中：

```java
DishVO::getId
```

等价于：

```java
dish -> dish.getId()
```

### 7.2 对字段做格式转换

```java
List<String> dateStringList = dateList.stream()
        .map(LocalDate::toString)
        .collect(Collectors.toList());
```

读法：

```text
把 List<LocalDate>
转换成 List<String>
```

### 7.3 对象转对象

项目里“再来一单”的代码就是典型对象转换：

```java
List<ShoppingCart> shoppingCartList = orderDetailList.stream()
        .map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        })
        .collect(Collectors.toList());
```

这里的类型变化是：

```text
OrderDetail -> ShoppingCart
```

也就是：

```text
List<OrderDetail>
  -> Stream<OrderDetail>
  -> Stream<ShoppingCart>
  -> List<ShoppingCart>
```

### 7.4 map 里不要写太多复杂业务

如果转换逻辑很长，可以提取成方法：

```java
private ShoppingCart toShoppingCart(OrderDetail orderDetail) {
    ShoppingCart shoppingCart = new ShoppingCart();
    BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
    shoppingCart.setUserId(BaseContext.getCurrentId());
    shoppingCart.setCreateTime(LocalDateTime.now());
    return shoppingCart;
}
```

然后 Stream 写成：

```java
List<ShoppingCart> shoppingCartList = orderDetailList.stream()
        .map(this::toShoppingCart)
        .collect(Collectors.toList());
```

这样读起来更像业务：

```text
把每个订单详情转换成购物车项。
```

---

## 8. collect：把流水线结果收集回来

Stream 的中间操作不会直接给你 List。

比如：

```java
orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
```

它的结果仍然是：

```text
Stream<Order>
```

如果你想拿到一个集合，需要终止操作：

```java
List<Order> completedOrders = orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
        .collect(Collectors.toList());
```

常见收集方式：

```java
collect(Collectors.toList())
collect(Collectors.toSet())
collect(Collectors.toMap(...))
collect(Collectors.joining(","))
collect(Collectors.groupingBy(...))
```

### 8.1 为什么本项目常用 Collectors.toList()

这个项目是 Spring Boot 2.7.x，学习资料里通常按 Java 8 写法讲。

Java 8 标准写法是：

```java
.collect(Collectors.toList())
```

Java 16 之后有：

```java
.toList()
```

但为了兼容 Java 8 和当前项目写法，建议你现在统一使用：

```java
.collect(Collectors.toList())
```

---

## 9. Collectors.toMap：List 转 Map

后端业务里，`List` 转 `Map` 非常常见。

原因是：

```text
List 适合遍历。
Map 适合按 key 快速查找。
```

### 9.1 基础写法

项目报表里有类似代码：

```java
Map<LocalDate, BigDecimal> turnoverMap = list.stream()
        .collect(Collectors.toMap(
                TurnoverReportDTO::getLocalDate,
                TurnoverReportDTO::getTurnover
        ));
```

读法：

```text
把 List<TurnoverReportDTO>
转换成 Map<LocalDate, BigDecimal>

key：每条记录的 localDate
value：每条记录的 turnover
```

这样后面就可以按日期取营业额：

```java
BigDecimal turnover = turnoverMap.get(date);
```

### 9.2 为什么报表里要先转 Map

数据库查询出来的数据可能是：

```text
2026-07-01 -> 100
2026-07-03 -> 200
```

但是前端图表需要连续日期：

```text
2026-07-01,2026-07-02,2026-07-03
```

所以要：

```text
先把数据库结果转成 Map
再遍历完整日期列表 dateList
有值就取值
没值就补 0
```

代码结构就是：

```java
Map<LocalDate, BigDecimal> turnoverMap = list.stream()
        .collect(Collectors.toMap(
                TurnoverReportDTO::getLocalDate,
                TurnoverReportDTO::getTurnover
        ));

List<String> turnOverStringList = dateList.stream()
        .map(date -> {
            BigDecimal turnover = turnoverMap.get(date);
            if (turnover == null) {
                turnover = BigDecimal.ZERO;
            }
            return turnover.toString();
        })
        .collect(Collectors.toList());
```

这段代码的业务含义：

```text
把数据库查到的每日营业额做成日期索引，
再按完整日期范围逐天取值，
没有数据的日期补 0，
最后得到前端图表需要的字符串列表。
```

### 9.3 toMap 的重复 key 坑

下面这种写法有一个隐藏风险：

```java
Map<Long, DishVO> dishMap = dishList.stream()
        .collect(Collectors.toMap(DishVO::getId, dish -> dish));
```

如果 `dishList` 里有两个相同的 id，会直接抛异常：

```text
IllegalStateException: Duplicate key
```

更稳的写法是加第三个参数，说明重复 key 时怎么处理：

```java
Map<Long, DishVO> dishMap = dishList.stream()
        .collect(Collectors.toMap(
                DishVO::getId,
                dish -> dish,
                (oldValue, newValue) -> oldValue
        ));
```

意思是：

```text
如果 key 重复，保留旧值。
```

如果你想保留新值：

```java
(oldValue, newValue) -> newValue
```

### 9.4 value 为对象本身的简写

```java
Map<LocalDate, OrderReportDTO> orderMap = list.stream()
        .collect(Collectors.toMap(
                OrderReportDTO::getLocalDate,
                v -> v
        ));
```

这里：

```java
v -> v
```

意思是：

```text
value 就是当前对象本身。
```

也可以写成：

```java
Function.identity()
```

但初学阶段 `v -> v` 更直观。

---

## 10. Collectors.joining：拼接字符串

前端图表经常需要这种字符串：

```text
2026-07-01,2026-07-02,2026-07-03
```

或者：

```text
宫保鸡丁,鱼香肉丝,米饭
```

Stream 可以这样拼：

```java
String dateListString = dateList.stream()
        .map(LocalDate::toString)
        .collect(Collectors.joining(","));
```

读法：

```text
先把 LocalDate 转成 String，
再用逗号拼接。
```

项目里销量 Top10 有类似代码：

```java
SalesTop10ReportVO res = SalesTop10ReportVO.builder()
        .nameList(list.stream()
                .map(GoodsSalesDTO::getName)
                .collect(Collectors.joining(",")))
        .numberList(list.stream()
                .map(GoodsSalesDTO::getNumber)
                .map(String::valueOf)
                .collect(Collectors.joining(",")))
        .build();
```

这里要注意：

```java
.map(GoodsSalesDTO::getNumber)
.map(String::valueOf)
```

第一步：

```text
GoodsSalesDTO -> Integer
```

第二步：

```text
Integer -> String
```

因为 `joining(",")` 只能拼接字符串流：

```text
Stream<String>
```

不能直接拼：

```text
Stream<Integer>
```

---

## 11. mapToInt、sum、count：统计类操作

### 11.1 count：计数

```java
long completedCount = orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
        .count();
```

读法：

```text
统计已完成订单数量。
```

### 11.2 mapToInt + sum：整数求和

项目里订单统计有类似代码：

```java
Integer totalOrderCount = orderList.stream()
        .mapToInt(OrderReportDTO::getTotalOrderCount)
        .sum();
```

读法：

```text
从每个 OrderReportDTO 中取出 totalOrderCount，
转换成 IntStream，
然后求和。
```

为什么不是：

```java
.map(OrderReportDTO::getTotalOrderCount).sum()
```

因为普通 `Stream<Integer>` 没有直接的 `sum()`。

要先变成专门处理数字的流：

```text
IntStream
LongStream
DoubleStream
```

常见写法：

```java
int total = list.stream()
        .mapToInt(Item::getCount)
        .sum();

long total = list.stream()
        .mapToLong(Item::getCount)
        .sum();

double total = list.stream()
        .mapToDouble(Item::getAmount)
        .sum();
```

### 11.3 BigDecimal 求和

金额一般不要用 `double`，业务里常用 `BigDecimal`。

```java
BigDecimal totalAmount = orders.stream()
        .map(Order::getAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
```

读法：

```text
提取每个订单金额，
过滤掉 null，
从 BigDecimal.ZERO 开始累加。
```

---

## 12. sorted、distinct、limit、skip：排序去重分页

### 12.1 sorted：排序

按金额从小到大：

```java
List<Order> sortedOrders = orders.stream()
        .sorted(Comparator.comparing(Order::getAmount))
        .collect(Collectors.toList());
```

按金额从大到小：

```java
List<Order> sortedOrders = orders.stream()
        .sorted(Comparator.comparing(Order::getAmount).reversed())
        .collect(Collectors.toList());
```

需要导入：

```java
import java.util.Comparator;
```

### 12.2 distinct：去重

```java
List<Long> userIds = orders.stream()
        .map(Order::getUserId)
        .distinct()
        .collect(Collectors.toList());
```

读法：

```text
从订单中提取用户 id，并去重。
```

注意：

```text
distinct 对普通值类型很好用。
如果是对象去重，依赖对象的 equals 和 hashCode。
```

### 12.3 limit：取前 N 个

```java
List<Order> top10Orders = orders.stream()
        .sorted(Comparator.comparing(Order::getAmount).reversed())
        .limit(10)
        .collect(Collectors.toList());
```

读法：

```text
按金额倒序排序，取前 10 个。
```

### 12.4 skip：跳过前 N 个

```java
List<Order> secondPage = orders.stream()
        .skip(10)
        .limit(10)
        .collect(Collectors.toList());
```

读法：

```text
跳过前 10 条，再取 10 条。
```

不过真实后端分页一般交给 SQL 和 PageHelper，不建议把数据库全查出来再用 Stream 分页。

---

## 13. flatMap：把多层集合压平

`flatMap` 是初学时最容易懵的一个。

先看场景：

```java
List<List<Long>> allDishIdGroups = Arrays.asList(
        Arrays.asList(1L, 2L),
        Arrays.asList(3L, 4L),
        Arrays.asList(5L)
);
```

现在想变成：

```java
List<Long> allDishIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
```

用 `flatMap`：

```java
List<Long> allDishIds = allDishIdGroups.stream()
        .flatMap(List::stream)
        .collect(Collectors.toList());
```

心智图：

```text
List<List<Long>>
  -> stream()
  -> Stream<List<Long>>
  -> flatMap(List::stream)
  -> Stream<Long>
  -> collect(...)
  -> List<Long>
```

### 13.1 map 和 flatMap 的区别

用 `map`：

```java
Stream<List<Long>>
```

每个元素还是一个 List。

用 `flatMap`：

```java
Stream<Long>
```

把里面的小 List 打开，压成一层。

一句话：

```text
map 是一对一转换。
flatMap 是一对多转换后再压平。
```

---

## 14. Optional：处理可能不存在的结果

有些终止操作不一定有结果，比如：

```java
findFirst()
max()
min()
```

因为集合可能为空。

所以返回的是 `Optional<T>`。

### 14.1 findFirst

```java
Optional<Order> firstCompletedOrder = orders.stream()
        .filter(order -> order.getStatus().equals(Orders.COMPLETED))
        .findFirst();
```

你可以这样取：

```java
Order order = firstCompletedOrder.orElse(null);
```

或者：

```java
Order order = orders.stream()
        .filter(item -> item.getStatus().equals(Orders.COMPLETED))
        .findFirst()
        .orElse(null);
```

### 14.2 不建议直接 get

不要这样写：

```java
Order order = orders.stream()
        .filter(item -> item.getStatus().equals(Orders.COMPLETED))
        .findFirst()
        .get();
```

如果没有符合条件的数据，会抛异常：

```text
NoSuchElementException
```

更稳的写法：

```java
Order order = orders.stream()
        .filter(item -> item.getStatus().equals(Orders.COMPLETED))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("没有已完成订单"));
```

---

## 15. reduce：能不用就先少用

`reduce` 表示归约，也就是把一批数据合成一个结果。

比如整数求和：

```java
Integer total = counts.stream()
        .reduce(0, Integer::sum);
```

读法：

```text
从 0 开始，把 counts 中的每个数字累加起来。
```

但是初学阶段，很多场景有更直观的写法。

比如整数求和，优先写：

```java
int total = counts.stream()
        .mapToInt(Integer::intValue)
        .sum();
```

金额求和才常见用 `reduce`：

```java
BigDecimal totalAmount = orders.stream()
        .map(Order::getAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
```

你现在对 `reduce` 的掌握标准：

```text
知道它是“把一批值合成一个值”。
遇到 BigDecimal 求和能看懂。
复杂 reduce 先不要主动写。
```

---

## 16. 项目里的 Stream 代码怎么读

这一节直接拿当前项目里的几类写法拆开。

### 16.1 提取 id 列表

项目里有类似：

```java
List<Long> dishIds = records.stream()
        .map(DishVO::getId)
        .collect(Collectors.toList());
```

业务读法：

```text
从分页查出的菜品记录中，提取每个菜品 id，组成 id 列表。
```

类型变化：

```text
List<DishVO>
  -> Stream<DishVO>
  -> Stream<Long>
  -> List<Long>
```

### 16.2 报表日期补 0

项目报表里有：

```java
Map<LocalDate, Long> userMap = list.stream()
        .collect(Collectors.toMap(
                UserReportDTO::getLocalDate,
                UserReportDTO::getUserCount
        ));
```

业务读法：

```text
数据库查出来的是某些日期有新增用户数。
把它转成 Map，方便后面按日期取。
```

然后：

```java
List<String> userList = dateList.stream()
        .map(date -> {
            Long count = userMap.get(date);
            if (count == null) {
                count = 0L;
                userMap.put(date, count);
            }
            return count.toString();
        })
        .collect(Collectors.toList());
```

业务读法：

```text
遍历完整日期列表。
每一天都去 userMap 里找新增用户数。
找不到就补 0。
最后转成字符串列表，给前端图表使用。
```

这段如果用 for 循环写，大概是：

```java
List<String> userList = new ArrayList<>();
for (LocalDate date : dateList) {
    Long count = userMap.get(date);
    if (count == null) {
        count = 0L;
        userMap.put(date, count);
    }
    userList.add(count.toString());
}
```

Stream 版本本质上不是魔法，只是写成了流水线。

### 16.3 拼接 ECharts 需要的字符串

项目里有：

```java
String dateListString = dateList.stream()
        .map(LocalDate::toString)
        .collect(Collectors.joining(","));
```

业务读法：

```text
把日期列表转成字符串，并用逗号拼接。
```

如果 `dateList` 是：

```text
2026-07-01
2026-07-02
2026-07-03
```

结果就是：

```text
2026-07-01,2026-07-02,2026-07-03
```

### 16.4 统计订单总数

项目里有：

```java
Integer totalOrderCount = orderList.stream()
        .mapToInt(OrderReportDTO::getTotalOrderCount)
        .sum();
```

业务读法：

```text
从每天的订单统计对象中取出总订单数，然后累加。
```

类型变化：

```text
List<OrderReportDTO>
  -> Stream<OrderReportDTO>
  -> IntStream
  -> int
```

### 16.5 再来一单：订单详情转购物车

项目里有：

```java
List<ShoppingCart> shoppingCartList = orderDetailList.stream()
        .map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        })
        .collect(Collectors.toList());
```

业务读法：

```text
用户点击再来一单。
系统查询原订单的订单明细。
每条订单明细都转换成一条购物车记录。
最后批量插入购物车。
```

这就是 `map` 的典型用途：

```text
把 A 对象转换成 B 对象。
```

---

## 17. 什么时候适合用 Stream，什么时候别硬用

### 17.1 适合用 Stream 的场景

适合：

```text
从列表中提取某个字段
过滤符合条件的数据
把 DTO 转成 VO
List 转 Map
List 拼接成字符串
简单求和、计数、最大最小值
分组统计
```

例子：

```java
List<Long> ids = users.stream()
        .map(User::getId)
        .collect(Collectors.toList());
```

这种非常适合 Stream。

### 17.2 不适合硬用 Stream 的场景

不适合：

```text
中间有复杂 if else
需要频繁修改外部变量
需要提前 break 并做复杂处理
每一步都有副作用，比如写数据库、发消息、改缓存
代码写完比 for 循环更难读
```

比如：

```java
for (Order order : orders) {
    if (order.getStatus().equals(Orders.CANCELLED)) {
        continue;
    }
    if (order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        log.warn("订单金额异常：{}", order.getId());
        continue;
    }
    orderMapper.updateStatus(order.getId(), Orders.COMPLETED);
}
```

这种里面有日志、跳过、更新数据库，普通 `for` 循环更直接。

判断原则：

```text
Stream 适合表达“这一批数据怎么变成另一批数据”。
for 循环适合表达“每一步都要做复杂业务动作”。
```

---

## 18. 常见坑位

### 18.1 Stream 只能消费一次

错误写法：

```java
Stream<Order> stream = orders.stream();

long count = stream.count();
List<Order> list = stream.collect(Collectors.toList());
```

第二次使用会报错：

```text
stream has already been operated upon or closed
```

正确做法：

```java
long count = orders.stream().count();
List<Order> list = orders.stream().collect(Collectors.toList());
```

需要两次结果，就从集合重新开一条流。

### 18.2 中间操作是懒执行的

下面这段没有终止操作，所以不会真正执行：

```java
orders.stream()
        .filter(order -> {
            System.out.println(order.getId());
            return true;
        });
```

加上终止操作才会执行：

```java
orders.stream()
        .filter(order -> {
            System.out.println(order.getId());
            return true;
        })
        .collect(Collectors.toList());
```

记住：

```text
filter、map、sorted 是中间操作，先记账。
collect、count、sum、findFirst 是终止操作，真正干活。
```

### 18.3 toMap 重复 key 会报错

错误风险：

```java
Map<Long, Order> orderMap = orders.stream()
        .collect(Collectors.toMap(Order::getUserId, order -> order));
```

如果一个用户有多个订单，`userId` 就会重复。

应该明确重复时的处理规则：

```java
Map<Long, Order> orderMap = orders.stream()
        .collect(Collectors.toMap(
                Order::getUserId,
                order -> order,
                (oldValue, newValue) -> newValue
        ));
```

或者如果你本来就想一个用户对应多个订单，应该用分组：

```java
Map<Long, List<Order>> orderMap = orders.stream()
        .collect(Collectors.groupingBy(Order::getUserId));
```

### 18.4 注意 null

下面可能空指针：

```java
List<String> names = users.stream()
        .map(User::getName)
        .map(String::trim)
        .collect(Collectors.toList());
```

如果 `getName()` 返回 null，`String::trim` 会报错。

更稳：

```java
List<String> names = users.stream()
        .map(User::getName)
        .filter(Objects::nonNull)
        .map(String::trim)
        .collect(Collectors.toList());
```

### 18.5 不要在 Stream 里随便改外部变量

不推荐：

```java
List<Long> ids = new ArrayList<>();
users.stream()
        .forEach(user -> ids.add(user.getId()));
```

更推荐：

```java
List<Long> ids = users.stream()
        .map(User::getId)
        .collect(Collectors.toList());
```

原因：

```text
Stream 更适合声明“我要得到什么结果”，而不是在里面到处修改外部对象。
```

### 18.6 parallelStream 不要轻易用

不要因为看起来能并行就改成：

```java
orders.parallelStream()
```

原因：

```text
业务代码里可能有数据库访问、事务、ThreadLocal、上下文用户 ID、日志顺序等问题。
数据量不大时，并行反而更慢。
出了问题更难排查。
```

初学和常规业务开发中，默认使用：

```java
stream()
```

---

## 19. API 速查表

| API | 类型 | 作用 | 例子 |
| --- | --- | --- | --- |
| `stream()` | 创建流 | 把集合放入流水线 | `list.stream()` |
| `filter` | 中间操作 | 过滤 | `.filter(x -> x > 0)` |
| `map` | 中间操作 | 转换 | `.map(User::getName)` |
| `flatMap` | 中间操作 | 压平多层集合 | `.flatMap(List::stream)` |
| `sorted` | 中间操作 | 排序 | `.sorted(Comparator.comparing(User::getId))` |
| `distinct` | 中间操作 | 去重 | `.distinct()` |
| `limit` | 中间操作 | 取前 N 个 | `.limit(10)` |
| `skip` | 中间操作 | 跳过前 N 个 | `.skip(10)` |
| `peek` | 中间操作 | 调试查看，不建议写业务 | `.peek(System.out::println)` |
| `collect` | 终止操作 | 收集结果 | `.collect(Collectors.toList())` |
| `count` | 终止操作 | 计数 | `.count()` |
| `findFirst` | 终止操作 | 找第一个 | `.findFirst()` |
| `anyMatch` | 终止操作 | 是否任意一个满足 | `.anyMatch(x -> x > 0)` |
| `allMatch` | 终止操作 | 是否全部满足 | `.allMatch(x -> x > 0)` |
| `noneMatch` | 终止操作 | 是否全部不满足 | `.noneMatch(x -> x < 0)` |
| `max` | 终止操作 | 最大值 | `.max(Comparator.comparing(User::getId))` |
| `min` | 终止操作 | 最小值 | `.min(Comparator.comparing(User::getId))` |
| `mapToInt` | 中间操作 | 转成数字流 | `.mapToInt(User::getAge)` |
| `sum` | 终止操作 | 数字求和 | `.sum()` |

### 19.1 Collectors 速查表

| 写法 | 结果 | 用途 |
| --- | --- | --- |
| `Collectors.toList()` | `List<T>` | 收集成列表 |
| `Collectors.toSet()` | `Set<T>` | 收集成集合并去重 |
| `Collectors.toMap(k, v)` | `Map<K, V>` | List 转 Map |
| `Collectors.groupingBy(...)` | `Map<K, List<T>>` | 分组 |
| `Collectors.joining(",")` | `String` | 字符串拼接 |
| `Collectors.counting()` | `Long` | 分组后计数 |
| `Collectors.summingInt(...)` | `Integer` | 分组后整数求和 |

---

## 20. 练习题：照着项目业务练

下面这些练习不需要新技术，只练 Stream 心智模型。

### 20.1 练习一：提取 id

已知：

```java
List<DishVO> dishList = ...
```

目标：

```text
得到 List<Long> dishIds
```

参考写法：

```java
List<Long> dishIds = dishList.stream()
        .map(DishVO::getId)
        .collect(Collectors.toList());
```

### 20.2 练习二：过滤启售菜品

已知：

```java
List<DishVO> dishList = ...
```

目标：

```text
只保留 status == 1 的菜品。
```

参考写法：

```java
List<DishVO> enabledDishList = dishList.stream()
        .filter(dish -> dish.getStatus() == 1)
        .collect(Collectors.toList());
```

如果 `getStatus()` 返回的是 `Integer`，更稳写法：

```java
List<DishVO> enabledDishList = dishList.stream()
        .filter(dish -> Integer.valueOf(1).equals(dish.getStatus()))
        .collect(Collectors.toList());
```

### 20.3 练习三：日期列表转字符串

已知：

```java
List<LocalDate> dateList = ...
```

目标：

```text
"2026-07-01,2026-07-02,2026-07-03"
```

参考写法：

```java
String dateString = dateList.stream()
        .map(LocalDate::toString)
        .collect(Collectors.joining(","));
```

### 20.4 练习四：按日期转 Map

已知：

```java
List<TurnoverReportDTO> list = ...
```

目标：

```text
Map<LocalDate, BigDecimal>
key 是日期
value 是营业额
```

参考写法：

```java
Map<LocalDate, BigDecimal> turnoverMap = list.stream()
        .collect(Collectors.toMap(
                TurnoverReportDTO::getLocalDate,
                TurnoverReportDTO::getTurnover
        ));
```

如果担心日期重复：

```java
Map<LocalDate, BigDecimal> turnoverMap = list.stream()
        .collect(Collectors.toMap(
                TurnoverReportDTO::getLocalDate,
                TurnoverReportDTO::getTurnover,
                BigDecimal::add
        ));
```

意思是：

```text
如果同一天有多条营业额记录，就累加。
```

### 20.5 练习五：订单统计求和

已知：

```java
List<OrderReportDTO> orderList = ...
```

目标：

```text
统计总订单数。
```

参考写法：

```java
int totalOrderCount = orderList.stream()
        .mapToInt(OrderReportDTO::getTotalOrderCount)
        .sum();
```

### 20.6 练习六：分组

已知：

```java
List<Order> orders = ...
```

目标：

```text
按订单状态分组。
```

参考写法：

```java
Map<Integer, List<Order>> orderMap = orders.stream()
        .collect(Collectors.groupingBy(Order::getStatus));
```

结果形态：

```text
{
  1: [待付款订单列表],
  2: [待接单订单列表],
  3: [已接单订单列表]
}
```

### 20.7 练习七：分组后计数

已知：

```java
List<Order> orders = ...
```

目标：

```text
统计每种状态分别有多少订单。
```

参考写法：

```java
Map<Integer, Long> countMap = orders.stream()
        .collect(Collectors.groupingBy(
                Order::getStatus,
                Collectors.counting()
        ));
```

结果形态：

```text
{
  1: 10,
  2: 5,
  3: 8
}
```

---

## 21. 最后给你的学习抓手

你现在不要把 Stream 学成“API 背诵题”。

先记住这四句话：

```text
1. stream()：把集合放到流水线上。
2. filter：少一点，过滤掉不想要的。
3. map：变一下，把一种东西变成另一种东西。
4. collect / count / sum / findFirst：收尾，拿到最终结果。
```

读任何 Stream 代码，都按这个模板拆：

```text
原始数据是什么？
每一步输入是什么？
每一步输出是什么？
最后结果是什么？
这个结果服务哪个业务动作？
```

最常见的后端业务模式是：

```java
List<Result> resultList = sourceList.stream()
        .filter(item -> 条件)
        .map(item -> 转换结果)
        .collect(Collectors.toList());
```

你先把这个模式练熟，再去看 `toMap`、`joining`、`groupingBy`、`mapToInt`。

等你看到下面这类代码时，能自然翻译成业务语言，就算入门了：

```java
String nameList = list.stream()
        .map(GoodsSalesDTO::getName)
        .collect(Collectors.joining(","));
```

翻译：

```text
从销量排行数据中提取商品名称，并拼成前端图表需要的逗号字符串。
```

这就是 Stream 真正的用法：**让一批数据的处理过程更像业务描述。**
