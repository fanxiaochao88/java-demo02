# 03 - Java 基础、集合、异常面试题详解

> 复习目标：回答 Java 基础题时，不只是说出定义，还要能解释底层原因、使用场景和常见坑。

## 1. Java 基础怎么准备

Java 基础题通常看三件事：

1. 你是否理解语言特性，而不是只会写业务代码。
2. 你是否知道常见类库的使用边界。
3. 你是否能从项目中举例说明。

推荐回答结构：

```text
先说结论 -> 解释原理 -> 举代码/项目例子 -> 说常见坑
```

## 2. String、StringBuilder、StringBuffer 有什么区别？

### 答案

`String` 是不可变字符串；`StringBuilder` 是可变字符串，线程不安全；`StringBuffer` 也是可变字符串，但方法加了同步，线程安全，性能通常比 `StringBuilder` 低。

### 讲解

`String` 不可变的意思是：一旦创建，字符串内容不能被修改。看起来像修改：

```java
String s = "abc";
s = s + "d";
```

实际上是创建了新的字符串对象，然后让变量 `s` 指向新对象。

不可变带来的好处：

1. 可以安全地放进字符串常量池。
2. 可以缓存 hash 值，适合作为 HashMap 的 key。
3. 多线程共享时天然安全。
4. 不容易被恶意修改，比如 URL、文件路径、数据库连接参数。

大量字符串拼接时，不建议在循环中直接使用 `+`：

```java
String result = "";
for (String item : list) {
    result += item;
}
```

这样可能产生很多临时对象。更好的写法：

```java
StringBuilder builder = new StringBuilder();
for (String item : list) {
    builder.append(item);
}
String result = builder.toString();
```

### 项目联系

生成缓存 key、拼接日志、拼接导出内容时，适合用 `StringBuilder` 或清晰的模板方式。订单号、用户 token、Redis key 这类字符串不要随意拼接，最好统一放到常量类或工具方法里。

## 3. `==` 和 `equals` 的区别？

### 答案

`==` 比较的是两个变量保存的值。对于基本类型，它比较具体数值；对于引用类型，它比较对象地址。`equals` 是对象方法，默认也是比较地址，但很多类会重写它，比如 `String` 重写后比较字符串内容。

### 讲解

```java
String a = new String("abc");
String b = new String("abc");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

因为 `a` 和 `b` 是两个不同对象，地址不同，但内容相同。

如果自定义类需要按业务字段比较，就要重写 `equals` 和 `hashCode`。例如用户实体如果以 `id` 判断是否同一个用户，就要明确写出比较规则。

### 常见坑

1. 包装类型用 `==` 比较可能出错，尤其是 `Integer` 超过缓存范围。
2. 重写 `equals` 不重写 `hashCode`，会导致 HashMap、HashSet 行为异常。
3. `BigDecimal` 的 `equals` 会比较精度，`new BigDecimal("1.0").equals(new BigDecimal("1.00"))` 是 false；金额比较通常用 `compareTo`。

## 4. 为什么重写 equals 必须重写 hashCode？

### 答案

因为基于哈希的集合，比如 `HashMap`、`HashSet`，会先根据 `hashCode` 定位桶，再用 `equals` 判断对象是否相等。如果两个对象 `equals` 相等，但 `hashCode` 不同，就可能被放到不同桶里，导致集合认为它们不是同一个对象。

### 讲解

约定是：

1. 如果两个对象 `equals` 相等，它们的 `hashCode` 必须相等。
2. 如果两个对象 `hashCode` 相等，它们不一定 `equals` 相等，因为可能发生哈希冲突。

错误示例：

```java
class User {
    private Long id;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof User)) {
            return false;
        }
        return id.equals(((User) obj).id);
    }
}
```

这个类只重写了 `equals`，没有重写 `hashCode`。放入 `HashSet` 时可能出现重复数据。

### 面试扩展

如果对象要作为 Map 的 key，必须保证参与 `equals/hashCode` 的字段在放入 Map 后不要变化。否则对象所在桶不变，但 hash 值变了，后续可能查不到。

## 5. HashMap 底层原理是什么？

### 答案

JDK 8 之后，HashMap 底层主要是“数组 + 链表 + 红黑树”。put 时先根据 key 的 hash 值计算数组下标；如果位置为空直接放入；如果已有元素，就通过 equals 判断是否同一个 key，是则覆盖，不是则挂到链表或红黑树上。当链表过长且数组容量达到一定条件时，链表会树化为红黑树，提高查询效率。

### 讲解

核心流程：

1. 计算 key 的 hash。
2. 根据数组长度计算桶下标。
3. 如果桶为空，直接插入。
4. 如果桶不为空，比较 key。
5. key 相同，覆盖 value。
6. key 不同，放到链表或红黑树。
7. 元素数量超过阈值，触发扩容。

HashMap 快，是因为大多数情况下能通过 hash 直接定位到数组位置，时间复杂度接近 O(1)。但如果 hash 冲突严重，多个元素落到同一个桶里，就会退化成链表查询。红黑树是为了解决极端冲突下查询过慢的问题。

### 为什么容量是 2 的幂？

这样可以用位运算 `(n - 1) & hash` 代替取模，性能更好；同时在 hash 分布较均匀时，也有利于元素分散。

### 为什么扩容影响性能？

扩容不是简单把数组变大，还需要把原有元素重新分布到新数组中。元素越多，扩容成本越高。所以如果能预估数量，可以初始化容量。

```java
Map<Long, String> map = new HashMap<>(1024);
```

### 项目联系

在项目中，如果把订单 id、菜品 id 映射到对象，可以使用 HashMap 做内存临时映射。但不能把它当作缓存长期存储，因为它没有过期、淘汰、分布式共享和持久化能力。

## 6. HashMap 为什么线程不安全？

### 答案

HashMap 没有同步控制，并发 put、resize、遍历时可能出现数据覆盖、数据丢失、结构不一致或遍历异常。多线程场景应该使用 `ConcurrentHashMap` 或在外部加锁。

### 讲解

典型问题：

1. 两个线程同时 put 同一个桶，可能互相覆盖。
2. 一个线程扩容时，另一个线程读写，可能读到不完整状态。
3. 遍历时另一个线程修改，可能触发 fail-fast。

### 项目联系

当前项目的 WebSocket 会话使用静态 `HashMap` 存储 session。由于连接建立、断开、消息发送可能并发发生，生产环境应改成 `ConcurrentHashMap`。

## 7. ConcurrentHashMap 怎么保证线程安全？

### 答案

JDK 8 的 ConcurrentHashMap 通过 CAS、synchronized 和分段思想降低锁粒度。put 时，如果桶为空，使用 CAS 插入；如果桶不为空，只锁当前桶的头节点，不会锁整个 Map。扩容时多个线程可以协助迁移数据。

### 讲解

它不是所有操作都加一把大锁，而是尽量只锁冲突位置。因此并发性能比 `Hashtable` 或 `Collections.synchronizedMap` 更好。

注意：

1. ConcurrentHashMap 不允许 key 或 value 为 null。
2. 它的迭代器是弱一致性的，遍历时其他线程修改不一定马上可见，但不会像 HashMap 那样容易抛并发修改异常。

## 8. ArrayList 和 LinkedList 有什么区别？

### 答案

`ArrayList` 底层是动态数组，支持快速随机访问，适合按下标查询和尾部追加；`LinkedList` 底层是双向链表，插入删除节点本身成本低，但查找指定位置需要遍历，随机访问慢。

### 讲解

不要简单说“LinkedList 插入删除快”。如果你要删除第 10000 个元素，LinkedList 需要先遍历找到它，这一步就是 O(n)。只有在已经拿到节点位置，或者经常在头尾插入删除时，链表优势才明显。

大多数业务场景优先用 ArrayList，因为：

1. 内存连续，CPU 缓存友好。
2. 随机访问快。
3. 实际业务常见操作是遍历和追加。

### 项目联系

订单明细、菜品列表、报表列表通常用 `ArrayList` 即可。除非明确需要队列/双端队列语义，否则没必要优先用 `LinkedList`。

## 9. ArrayList 扩容机制是什么？

### 答案

ArrayList 底层是数组，数组容量不够时会创建一个更大的新数组，再把旧数组元素复制过去。JDK 常见实现中扩容约为原容量的 1.5 倍。

### 讲解

扩容需要复制元素，所以如果能预估大小，最好指定初始容量：

```java
List<OrderDetail> details = new ArrayList<>(shoppingCartList.size());
```

这在下单时把购物车转换为订单明细很适合，能避免不必要扩容。

## 10. fail-fast 和 fail-safe 是什么？

### 答案

fail-fast 指集合遍历过程中，如果检测到结构被并发修改，会快速失败，抛出 `ConcurrentModificationException`。典型集合是 ArrayList、HashMap。fail-safe 通常指遍历的是副本或弱一致视图，遍历过程中修改不会直接抛异常，比如 CopyOnWriteArrayList。

### 讲解

错误写法：

```java
for (String item : list) {
    if (item.startsWith("x")) {
        list.remove(item);
    }
}
```

正确写法：

```java
Iterator<String> iterator = list.iterator();
while (iterator.hasNext()) {
    String item = iterator.next();
    if (item.startsWith("x")) {
        iterator.remove();
    }
}
```

或者使用 `removeIf`：

```java
list.removeIf(item -> item.startsWith("x"));
```

## 11. 接口和抽象类有什么区别？

### 答案

接口更强调能力和规范，抽象类更强调一类对象的共同父类。Java 类只能继承一个抽象类，但可以实现多个接口。接口中的方法默认是 public，现代 Java 接口也支持 default 方法和 static 方法；抽象类可以有成员变量、构造方法和普通方法。

### 讲解

在项目中：

1. `Service` 接口定义业务能力。
2. `ServiceImpl` 提供具体实现。
3. Mapper 接口定义数据访问方法，由 MyBatis 生成代理实现。

如果你要表达“这个类具备某种能力”，优先用接口；如果你要复用一组状态和通用行为，可以考虑抽象类。

## 12. final 关键字有什么用？

### 答案

`final` 可以修饰类、方法、变量。修饰类表示不能被继承；修饰方法表示不能被重写；修饰变量表示只能赋值一次。

### 讲解

`final` 修饰引用变量时，表示引用不能再指向别的对象，但对象内部状态仍可能变化。

```java
final List<String> list = new ArrayList<>();
list.add("a");       // 可以
// list = new ArrayList<>(); // 不可以
```

### 项目联系

常量类里的字段通常用 `public static final`。例如状态码、消息常量、Redis key 前缀等。

## 13. Java 异常体系怎么理解？

### 答案

Java 异常顶层是 `Throwable`，下面分为 `Error` 和 `Exception`。`Error` 表示严重错误，通常程序无法处理，比如 OOM；`Exception` 表示程序可以处理的异常。Exception 又分为受检异常和运行时异常。

### 讲解

受检异常：编译器要求处理，比如 `IOException`。  
运行时异常：编译器不强制处理，比如 `NullPointerException`、`IllegalArgumentException`。

业务项目中通常会自定义运行时异常，比如：

```java
throw new ShoppingCartBusinessException("购物车为空");
```

然后由全局异常处理器统一转换成接口响应。

### 面试注意

不要在业务代码里到处 `try-catch` 后返回 null。这样会隐藏错误，导致调用方不知道失败原因。更好的方式是抛业务异常，统一处理。

## 14. throw 和 throws 的区别？

### 答案

`throw` 用在方法体内，表示主动抛出一个异常对象；`throws` 用在方法声明上，表示这个方法可能抛出某些异常，调用方需要处理或继续抛出。

```java
public void readFile(String path) throws IOException {
    if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
    }
}
```

## 15. 泛型有什么作用？什么是类型擦除？

### 答案

泛型让集合和方法在编译期具备类型检查能力，减少强制类型转换和运行时类型错误。类型擦除是指 Java 泛型主要存在于编译期，编译后很多泛型信息会被擦除为原始类型或边界类型。

### 讲解

没有泛型：

```java
List list = new ArrayList();
list.add("abc");
Integer value = (Integer) list.get(0); // 运行时报错
```

有泛型：

```java
List<String> list = new ArrayList<>();
list.add("abc");
// list.add(123); // 编译期报错
```

### 常见追问

为什么不能 `new T()`？

因为运行时类型被擦除，JVM 不知道 T 具体是什么类型。如果需要创建对象，可以传入 `Class<T>` 或 Supplier。

## 16. 反射是什么？项目里哪里用到？

### 答案

反射允许程序在运行时获取类的信息，并动态调用方法、访问字段、创建对象。它提高了框架的灵活性，但性能和安全性比直接调用差。

### 项目联系

当前项目的 AOP 自动填充使用反射调用实体对象的 setter 方法：

```java
Method setUpdateTime = entity.getClass()
    .getDeclaredMethod("setUpdateTime", LocalDateTime.class);
setUpdateTime.invoke(entity, now);
```

Spring 创建 Bean、MyBatis 封装查询结果、JSON 序列化也会大量使用反射或类似机制。

### 常见坑

1. 方法名写错会运行时报错。
2. 参数类型必须匹配。
3. 反射绕过编译期检查。
4. 高频调用时要考虑性能，可缓存 Method 对象或使用框架能力。

## 17. Stream API 适合什么场景？

### 答案

Stream 适合集合数据的过滤、映射、分组、聚合等声明式处理。它能让代码更简洁，但不适合写过于复杂、有大量副作用的业务逻辑。

### 示例

把购物车转换为订单明细：

```java
List<OrderDetail> details = shoppingCartList.stream()
    .map(cart -> {
        OrderDetail detail = new OrderDetail();
        BeanUtils.copyProperties(cart, detail);
        detail.setOrderId(orderId);
        return detail;
    })
    .collect(Collectors.toList());
```

### 常见坑

1. Stream 不是一定比 for 循环快。
2. 不要在 stream 中修改外部共享变量。
3. 并行流要谨慎，涉及数据库、网络、事务上下文时更要慎用。
4. 复杂业务逻辑为了可读性可以用普通 for。

## 18. BigDecimal 为什么适合金额计算？

### 答案

`double` 和 `float` 是二进制浮点数，不能精确表示很多十进制小数，可能出现精度误差。金额计算需要精确性，所以通常使用 `BigDecimal`。

### 正确写法

```java
BigDecimal price = new BigDecimal("19.90");
BigDecimal count = new BigDecimal("3");
BigDecimal total = price.multiply(count);
```

不推荐：

```java
BigDecimal price = new BigDecimal(19.90);
```

因为传入 double 时误差已经产生了。

### 项目联系

订单金额、菜品价格、套餐价格都应该用 `BigDecimal`，不要用 `double`。

## 19. LocalDateTime 和 Date 有什么区别？

### 答案

`Date` 是老的日期时间类，API 设计不够清晰，很多方法已过时；`LocalDateTime` 是 Java 8 新时间 API，线程安全、语义清楚，适合表示不带时区的本地日期时间。

常用类型：

| 类型 | 适用场景 |
| --- | --- |
| LocalDate | 只表示日期，如生日、统计日期 |
| LocalTime | 只表示时间，如营业开始时间 |
| LocalDateTime | 日期 + 时间，如订单创建时间 |
| Instant | 时间戳，适合机器时间 |
| ZonedDateTime | 带时区的日期时间 |

### 项目联系

订单创建时间、支付时间、取消时间、更新时间适合用 `LocalDateTime`。如果系统涉及跨时区，再考虑 `Instant` 或 `ZonedDateTime`。

## 20. 包装类型和基本类型有什么区别？

### 答案

基本类型保存具体值，包装类型是对象，可以为 null，也可以用于泛型和集合。比如 `int` 不能放进 `List<int>`，必须用 `List<Integer>`。

### 常见坑

1. 自动拆箱可能空指针：

```java
Integer count = null;
int value = count; // NullPointerException
```

2. 包装类型用 `==` 比较可能受缓存影响：

```java
Integer a = 128;
Integer b = 128;
System.out.println(a == b); // false
```

### 项目建议

DTO 和 Entity 中数据库可为空字段通常用包装类型；业务计算时要注意 null 判断。

## 21. 面试自测

请闭卷回答：

1. HashMap put 的完整流程是什么？
2. HashMap 为什么容量是 2 的幂？
3. HashMap 和 ConcurrentHashMap 有什么区别？
4. equals 和 hashCode 为什么要一起重写？
5. ArrayList 扩容怎么发生？
6. 为什么金额用 BigDecimal？
7. Java 异常体系怎么分？
8. 反射在当前项目哪里用到？
9. Stream 的优缺点是什么？
10. ThreadLocal 为什么不能忘记 remove？

