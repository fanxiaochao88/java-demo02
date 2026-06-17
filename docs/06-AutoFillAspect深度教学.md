# AutoFillAspect 深度教学——从零理解 AOP + 注解 + 反射

> **写给谁看？** Java 新手、Spring Boot 新手，刚接触注解和反射感到"太复杂了"的同学。
>
> **讲什么？** 以 `AutoFillAspect.java` 为案例，系统地拆解里面涉及的每一个知识点。
>
> **怎么讲？** 从"为什么要这么做"出发，先理解背景问题，再逐层深入到技术细节。

---

## 目录

1. [先讲故事：为什么需要这个功能？](#1-先讲故事为什么需要这个功能)
2. [梳理参与角色：4 个文件各司其职](#2-梳理参与角色4-个文件各司其职)
3. [知识点1：自定义注解——AutoFill](#3-知识点1自定义注解autofill)
4. [知识点2：枚举——OperationType](#4-知识点2枚举operationtype)
5. [知识点3：AOP 切面编程——AutoFillAspect](#5-知识点3aop-切面编程autofillaspect)
6. [知识点4：切入点表达式——@Pointcut](#6-知识点4切入点表达式pointcut)
7. [知识点5：通知类型——@Before](#7-知识点5通知类型before)
8. [知识点6：JoinPoint——连接点对象](#8-知识点6joinpoint连接点对象)
9. [知识点7：Java 反射——getDeclaredMethod + invoke](#9-知识点7java-反射getdeclaredmethod--invoke)
10. [知识点8：MethodSignature——方法签名](#10-知识点8methodsignature方法签名)
11. [知识点9：BaseContext——ThreadLocal 线程上下文](#11-知识点9basecontextthreadlocal-线程上下文)
12. [完整流程串联：一次 insert 请求的完整旅程](#12-完整流程串联一次-insert-请求的完整旅程)
13. [动手实验：自己做一遍](#13-动手实验自己做一遍)
14. [常见问题 FAQ](#14-常见问题-faq)
15. [速查表](#15-速查表)

---

## 1. 先讲故事：为什么需要这个功能？

### 没有自动填充之前

假设你做一个后台管理系统，数据库表都有这些字段：

| 字段 | 含义 | 什么时候赋值 |
|------|------|-------------|
| `create_time` | 创建时间 | 新增时 |
| `update_time` | 修改时间 | 新增 + 修改时 |
| `create_user` | 创建人ID | 新增时 |
| `update_user` | 修改人ID | 新增 + 修改时 |

**每个表都有这 4 个字段，每个新增/修改的地方都要赋值。**

你可能会这样写：

```java
// EmployeeServiceImpl.java - 新增员工
public void save(Employee employee) {
    // 每次都要手动 set 这 4 个字段
    employee.setCreateTime(LocalDateTime.now());
    employee.setUpdateTime(LocalDateTime.now());
    employee.setCreateUser(currentUserId);
    employee.setUpdateUser(currentUserId);
    employeeMapper.insert(employee);
}

// CategoryServiceImpl.java - 新增分类
public void save(Category category) {
    // 又来一遍...
    category.setCreateTime(LocalDateTime.now());
    category.setUpdateTime(LocalDateTime.now());
    category.setCreateUser(currentUserId);
    category.setUpdateUser(currentUserId);
    categoryMapper.insert(category);
}

// DishServiceImpl.java - 新增菜品
public void save(Dish dish) {
    // 还要来...
    dish.setCreateTime(LocalDateTime.now());
    // ... 无限重复
}
```

**问题很明显：**

1. **代码重复**——每个 Service 方法里都写一样的赋值逻辑
2. **容易漏**——万一哪个新人忘了写，数据就不完整
3. **职责不清**——Service 层本应专注业务逻辑，不该操心"填充字段"这种事

### 理想方案

你心里想的是：**能不能在 Mapper 方法执行前，自动帮你把这些字段填好？**

```
调用 employeeMapper.insert(employee) 之前
    ↓ 自动拦截
    ↓ 自动填充 employee 的 createTime, updateTime, createUser, updateUser
    ↓
执行真正的 insert
```

**Spring AOP 就是干这个的。** 这个项目的 `AutoFillAspect` 完美实现了这个想法。

---

## 2. 梳理参与角色：4 个文件各司其职

在做任何技术分析之前，先把"有哪些角色、各自干什么"搞清楚。

```
┌─────────────────────────────────────────────────────────────┐
│                      参与角色全景图                          │
├──────────────┬──────────────────────────────────────────────┤
│              │                                              │
│  1. @AutoFill │  自定义注解，像一张"标签"                   │
│     (注解)    │  贴在 Mapper 方法上，说"这个方法需要自动填充" │
│              │                                              │
│  2. OperationType │  枚举，只有两个值                       │
│     (枚举)       │  INSERT = 新增时填 4 个字段              │
│                  │  UPDATE = 修改时填 2 个字段               │
│              │                                              │
│  3. AutoFillConstant │  常量类，存 setter 方法名            │
│     (常量)           │  用字符串常量代替硬编码              │
│              │                                              │
│  4. AutoFillAspect │  切面类，核心执行者                     │
│     (切面)        │  拦截 → 反射获取注解值 → 反射调用setter  │
│              │                                              │
└──────────────┴──────────────────────────────────────────────┘
```

**类比理解：**

- `@AutoFill` 像快递包裹上的"易碎品"标签
- `OperationType` 像"易碎品等级：1级 / 2级"
- `AutoFillAspect` 像快递分拣员，看到标签后执行特殊处理
- `AutoFillConstant` 像操作手册上的步骤编号

先从最简单的开始——自定义注解。

---

## 3. 知识点1：自定义注解——@AutoFill

### 3.1 注解是什么？

**注解就是给代码贴标签。** 标签本身不干任何事，但别人看到标签后可以做出反应。

你已经在用了——`@Override`、`@Test`、`@Autowired` 都是注解。

```java
@Override                    // 贴在方法上，告诉编译器："这个方法是重写父类的"
public String toString() {
    return "...";
}
```

### 3.2 自定义一个注解

```java
// AutoFill.java
@Target(ElementType.METHOD)       // 这个注解只能贴在方法上
@Retention(RetentionPolicy.RUNTIME) // 这个注解在运行时保留（程序运行时能读到）
public @interface AutoFill {
    OperationType value();         // 注解的属性，使用时必须指定 INSERT 还是 UPDATE
}
```

**逐行解读：**

#### `@Target(ElementType.METHOD)`

**Target** = 目标，定义你的注解能贴在什么地方。

| ElementType 值 | 能贴在 |
|---------------|--------|
| `METHOD` | 方法上 |
| `TYPE` | 类/接口上 |
| `FIELD` | 字段上 |
| `PARAMETER` | 方法参数上 |
| `CONSTRUCTOR` | 构造方法上 |

```java
// 如果 @Target(ElementType.METHOD)，下面这样用就对了：
@AutoFill(value = OperationType.INSERT)  // ✅ 贴在方法上
void insert(Employee e);

// 这样用就会报错：
@AutoFill(value = OperationType.INSERT)  // ❌ 不能贴在字段上
private String name;
```

#### `@Retention(RetentionPolicy.RUNTIME)`

**Retention** = 保留，定义注解在什么阶段还存在。

| RetentionPolicy | 含义 | 类比 |
|----------------|------|------|
| `SOURCE` | 只在源代码里有，编译时扔掉 | 草稿纸，写完就扔 |
| `CLASS` | 编译进 `.class` 文件，但运行时没有 | 存档，但不翻阅 |
| `RUNTIME` | **运行时还在**，可以通过反射读到 | 工作手册，随时查阅 |

**这里必须用 `RUNTIME`**，因为 AutoFillAspect 是在程序运行时通过反射读取注解的。

#### `public @interface AutoFill`

`@interface` 是定义注解的关键字（不是普通的 `interface`）。

#### `OperationType value();`

注解的属性（也叫"成员"）。使用时必须赋值：

```java
@AutoFill(value = OperationType.INSERT)   // value = INSERT
void insert(Employee employee);

@AutoFill(value = OperationType.UPDATE)   // value = UPDATE
void update(Employee employee);
```

因为属性名叫 `value`，可以省略属性名：

```java
@AutoFill(OperationType.INSERT)   // 等价于 value = OperationType.INSERT
```

### 3.3 注解是怎么被读到的？

注解自己不会动，需要有人去读它。AutoFillAspect 里这行代码就是读取注解：

```java
AutoFill value = signature.getMethod()
    .getAnnotation(AutoFill.class)    // 从方法上读取 @AutoFill 注解
    .value();                          // 取出注解的 value 值
```

**思考题：** 如果你把 `@Retention` 改成 `SOURCE`，这行代码还能读到吗？

<details>
<summary>点击看答案</summary>
不能。`SOURCE` 级别的注解在编译后就被丢弃了，运行时反射读不到，`getAnnotation()` 会返回 `null`，接着就空指针异常。
</details>

---

## 4. 知识点2：枚举——OperationType

```java
public enum OperationType {
    INSERT,   // 新增操作
    UPDATE    // 修改操作
}
```

**枚举为什么比 int/String 好？**

```java
// ❌ 坏做法：用整数，谁知道 1 代表什么？
if (type == 1) { ... }

// ❌ 坏做法：用字符串，容易打错
if (type.equals("insert")) { ... }   // 万一打成 "Insert" 就挂了

// ✅ 好做法：用枚举，IDE 有提示，不会打错
if (value == OperationType.INSERT) { ... }
```

枚举把你可选的值限定在一个集合里，编译器帮你检查，不会出现"手滑打错"的问题。

---

## 5. 知识点3：AOP 切面编程——AutoFillAspect

### 5.1 什么是 AOP？

**AOP = Aspect Oriented Programming = 面向切面编程。**

先理解 OOP 的局限：

```
EmployeeService        CategoryService        DishService
    ↓ save()               ↓ save()              ↓ save()
    ↓ 填 createTime        ↓ 填 createTime       ↓ 填 createTime
    ↓ 填 updateTime        ↓ 填 updateTime       ↓ 填 updateTime
    ↓ 填 createUser        ↓ 填 createUser       ↓ 填 createUser
    ↓ 填 updateUser        ↓ 填 updateUser       ↓ 填 updateUser
    ↓ 调 mapper.insert()   ↓ 调 mapper.insert()  ↓ 调 mapper.insert()
```

这些"填字段"的代码横着散落在各个 Service 中，像切西瓜一样横切一刀：

```
      EmployeeService ──── CategoryService ──── DishService
        │                      │                     │
  ══════╪══════════════════════╪═════════════════════╪════════   ← 切面（Aspect）
        │                      │                     │
     填充公共字段             填充公共字段           填充公共字段
        │                      │                     │
     调 mapper.insert()      调 mapper.insert()    调 mapper.insert()
```

**AOP 的思想是：** 把横切关注点（比如填充公共字段）抽出来，放到一个地方统一处理。

### 5.2 AOP 的核心术语

> 新手别纠结术语，先大致知道，写多了自然记住。

| 术语 | 英文 | 大白话解释 | 本项目对应 |
|------|------|-----------|-----------|
| 切面 | Aspect | 横切逻辑的"容器类" | `AutoFillAspect` 类 |
| 切入点 | Pointcut | "在哪些方法上生效"的规则 | `@Pointcut(...)` 表达式 |
| 通知 | Advice | 具体要干什么 + 什么时候干 | `@Before` + `autoFill()` 方法 |
| 连接点 | JoinPoint | 被拦截到的具体一个方法调用 | `joinPoint` 参数 |
| 目标对象 | Target | 被拦截的那个对象 | `EmployeeMapper`、`CategoryMapper` |

### 5.3 看代码结构

把 AutoFillAspect 拆成三个层次来看：

```java
@Aspect        // ← "我是一个切面类"
@Component     // ← "让 Spring 管理我"
@Slf4j         // ← "给我一个 log 对象"
public class AutoFillAspect {

    // 第 1 层：定义规则——"我要拦截谁？"
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut() {}

    // 第 2 层：定义动作——"拦截之后干什么？"
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        // 第 3 层：具体逻辑——读取注解 → 反射调用 setter
    }
}
```

**层次关系图：**

```
@Pointcut("...")
    ↓ 定义了规则：所有 mapper 包下的方法 + 带 @AutoFill 注解
    ↓
@Before("autoFillPointCut()")
    ↓ 定义了时机：在方法执行之前
    ↓
autoFill(JoinPoint joinPoint)
    ↓ 具体逻辑：获取注解的 value → 反射调用 setter 方法
```

---

## 6. 知识点4：切入点表达式——@Pointcut

### 6.1 这个表达式在说什么

```java
@Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
```

这是一个**组合条件**，两个条件用 `&&` 连接，**必须同时满足**：

```
条件1：execution(* com.sky.mapper.*.*(..))
       ↓
     "com.sky.mapper 包下的任意类的任意方法"

                      &&
                     (并且)

条件2：@annotation(com.sky.annotation.AutoFill)
       ↓
     "方法上贴了 @AutoFill 注解"
```

**不满足条件的不会被拦截：**

```java
// ✅ 同时满足两个条件，会被拦截
@AutoFill(value = OperationType.INSERT)
void insert(Employee employee);

// ❌ 满足条件1，但不满足条件2（没贴 @AutoFill），不会被拦截
@Select("select * from employee where id = #{id}")
Employee getById(Long id);
```

### 6.2 execution 表达式拆解

```
execution(* com.sky.mapper.*.*(..))
          │           │     │ │  │
          │           │     │ │  └── (..)  = 任意参数（不限类型和数量）
          │           │     │ └── * = 任意方法名
          │           │     └── * = 任意类名
          │           └── com.sky.mapper = 包名
          └── * = 任意返回值类型
```

| 部分 | 含义 |
|------|------|
| 第一个 `*` | 返回值类型任意（void、int、Employee 都行） |
| `com.sky.mapper` | 限定在哪个包 |
| 第二个 `*` | 包下的任意类 |
| 第三个 `*` | 类的任意方法 |
| `(..)` | 方法参数任意（0 个、1 个、N 个都行） |

### 6.3 execution 表达式速查

| 写法 | 拦截范围 |
|------|---------|
| `* com.sky.mapper.*.*(..)` | mapper 包下所有类的所有方法 |
| `* com.sky.mapper.EmployeeMapper.*(..)` | 只拦截 EmployeeMapper |
| `* com.sky..*.*(..)` | `..` 代表所有子包——mapper 包及子包 |
| `void com.sky.mapper.*.*(..)` | 只拦截返回值是 void 的方法 |
| `* com.sky.mapper.*.insert*(..)` | 只拦截方法名以 `insert` 开头的方法 |

### 6.4 @annotation 表达式

```
@annotation(com.sky.annotation.AutoFill)
```

**含义：** 方法上贴了 `@AutoFill` 注解。

这里写的是注解的**全限定类名**（包名 + 类名）。

---

## 7. 知识点5：通知类型——@Before

通知（Advice）定义了"什么时候执行 + 执行什么"。

### 7.1 五种通知类型

```
调用目标方法前 → 调用目标方法 → 调用目标方法后
     ↓                           ↓
  @Before                     @After
                      @AfterReturning（正常返回后）
                      @AfterThrowing（抛异常后）
                      @Around（包裹整个方法，最强但最复杂）
```

| 注解 | 执行时机 | 能阻止目标方法执行吗？ | 常用度 |
|------|---------|---------------------|--------|
| `@Before` | 目标方法执行**前** | ❌ 不能 | ⭐⭐⭐⭐⭐ |
| `@After` | 目标方法执行**后**（无论成功/异常） | ❌ 不能 | ⭐⭐⭐ |
| `@AfterReturning` | 目标方法**正常返回后** | ❌ 不能 | ⭐⭐⭐ |
| `@AfterThrowing` | 目标方法**抛异常后** | ❌ 不能 | ⭐⭐ |
| `@Around` | **包裹**目标方法（前+后都能控制） | ✅ 能 | ⭐⭐⭐⭐ |

### 7.2 为什么这里用 @Before？

因为需求是"在 insert/update 执行前把字段填好"，自然是 `@Before`。

### 7.3 通知方法的写法

```java
@Before("autoFillPointCut()")        // 引用上面定义的切入点
public void autoFill(JoinPoint joinPoint) {  // JoinPoint 是"当前被拦截的方法"的信息
    // 你的逻辑
}
```

关键点：
- `@Before` 的括号里写的是**切入点方法名**（带括号），对应上面的 `autoFillPointCut()`
- 通知方法的参数可以写 `JoinPoint`（Spring 会自动注入）

---

## 8. 知识点6：JoinPoint——连接点对象

### 8.1 JoinPoint 是什么？

**连接点 = 被拦截到的方法调用这一刻的快照。** 包含了"谁调用了我"、"方法名叫什么"、"参数是什么"等信息。

把它想象成一个快递包裹的运单信息：

| JoinPoint 提供的信息 | 类比 |
|---------------------|------|
| `getSignature()` | 包裹的"品名"——方法名 + 返回类型 |
| `getArgs()` | 包裹的"内容物"——方法参数 |
| `getTarget()` | "收件人"——被代理的对象 |
| `getThis()` | "寄件人"——代理对象 |

### 8.2 本项目用到了什么

```java
// 1. 获取方法签名（方法名、返回类型、参数类型等）
MethodSignature signature = (MethodSignature) joinPoint.getSignature();

// 2. 获取方法调用时的实际参数值
Object[] args = joinPoint.getArgs();
```

---

## 9. 知识点7：Java 反射——getDeclaredMethod + invoke

### 9.1 反射是什么？为什么需要它？

**正常调用方法：**
```java
Employee e = new Employee();
e.setCreateTime(LocalDateTime.now());  // 你在写代码时就知道方法名叫 setCreateTime
```

**问题来了：** AutoFillAspect 拦截的不一定是 `Employee`——可能是 `Category`、`Dish`、`Setmeal` 等等。**你在写 AutoFillAspect 时根本不知道将来会传入什么类型的对象。**

```java
Object entry = args[0];  // 可能是 Employee，也可能是 Category
// 你怎么调用它的 setCreateTime？你连它是什么类型都不知道！
```

**反射就是解决这个问题的。** 反射让你在运行时动态地获取类的信息、调用方法，不需要在编译时知道类型。

### 9.2 反射三步走

> 反射 = 拿到蓝图（Method 对象）→ 拿着蓝图去操作具体对象

```java
// 第 1 步：获取 Method 对象（"方法蓝图"）
//        在 entry 的类里，找一个叫 "setCreateTime" 的方法，参数类型是 LocalDateTime
Method setCreateTime = entry.getClass().getDeclaredMethod("setCreateTime", LocalDateTime.class);

// 第 2 步：用 Method 对象去调用具体对象的方法
//        对 entry 这个对象，调用 setCreateTime，传参数 now
setCreateTime.invoke(entry, now);
```

**逐行对应看：**

```java
// 正常写法 vs 反射写法

// 正常写法（你必须在编译时知道 entry 是 Employee 类型）
((Employee) entry).setCreateTime(now);

// 反射写法（运行时动态处理，entry 是什么类型都能 d iyong）
entry.getClass()                          // 获取 entry 的真实类型（运行时才知道）
     .getDeclaredMethod(                  // 在这个类型中查找方法
         "setCreateTime",                 //   方法名（字符串）
         LocalDateTime.class)             //   参数类型
     .invoke(entry, now);                 // 在这个对象上调用方法，传参数 now
```

### 9.3 getDeclaredMethod 详解

```java
Method m = entry.getClass().getDeclaredMethod("setCreateTime", LocalDateTime.class);
//              ↑               ↑                   ↑                  ↑
//              │               │                   │                  │
//        获取当前对象      获取 Method 对象       方法名          参数类型
//        的真实 Class
```

**Q: 为什么后面要写 `LocalDateTime.class`？**

A: 因为 Java 支持方法重载——同一个类里可能有多个同名方法：

```java
public void setCreateTime(LocalDateTime time) { ... }   // 参数是 LocalDateTime
public void setCreateTime(String time) { ... }          // 参数是 String
```

不指定参数类型的话，Java 不知道你要找哪一个。**方法名 + 参数类型列表** 才能唯一确定一个方法。

### 9.4 invoke 详解

```java
setCreateTime.invoke(entry, now);
//    ↑            ↑       ↑
//  Method对象   调用对象   参数值
```

等价于：`entry.setCreateTime(now);`

**注意：** invoke 的第一个参数是"在哪个对象上调用"，后面的参数才是方法参数。

### 9.5 反射 API 速查

| API | 用途 | 示例 |
|-----|------|------|
| `obj.getClass()` | 获取对象的类 | `entry.getClass()` |
| `Class.forName("...") ` | 通过类名加载类 | `Class.forName("com.sky.entity.Employee")` |
| `clazz.getDeclaredMethod(name, paramTypes...)` | 获取类中声明的方法（包括 private） | `clazz.getDeclaredMethod("setName", String.class)` |
| `clazz.getMethod(name, paramTypes...)` | 获取类中的 public 方法（包括继承的） | `clazz.getMethod("toString")` |
| `method.invoke(obj, args...)` | 在 obj 上调用 method | `setName.invoke(entry, "张三")` |
| `clazz.getDeclaredField(name)` | 获取字段 | `clazz.getDeclaredField("name")` |
| `method.getAnnotation(Anno.class)` | 获取方法上的注解 | `method.getAnnotation(AutoFill.class)` |

---

## 10. 知识点8：MethodSignature——方法签名

### 10.1 什么是方法签名？

**方法签名 = 方法的身份证。** 包含方法名、返回类型、参数类型等元信息。

```java
public void insert(Employee e) throws Exception
  ↑    ↑     ↑        ↑           ↑
  │    │     │     参数类型       异常
  │    │   方法名
  │  返回类型
 修饰符
```

### 10.2 为什么需要强转？

```java
MethodSignature signature = (MethodSignature) joinPoint.getSignature();
```

`joinPoint.getSignature()` 返回的是 `Signature` 接口（父类型），但我们需要 `MethodSignature` 的 `getMethod()` 方法。所以要向下转型：

```
Signature（接口——只有基础方法）
    ├── MethodSignature（方法签名——能获取 Method 对象）    ← 我们需要这个
    └── FieldSignature（字段签名）
```

```java
// 转型后就能用了
signature.getMethod()    // 获取 java.lang.reflect.Method 对象
          .getAnnotation(AutoFill.class)  // 获取方法上的 @AutoFill 注解
          .value();      // 获取注解的 value 值
```

---

## 11. 知识点9：BaseContext——ThreadLocal 线程上下文

### 11.1 问题：AutoFillAspect 怎么知道"当前用户是谁"？

```java
Long currentId = BaseContext.getCurrentId();   // 拿到当前登录用户的 ID
```

这行代码只有一个疑问：**BaseContext 怎么知道当前请求是哪个用户？**

### 11.2 答案：拦截器在请求进来时就存好了

```
HTTP 请求（带 token）
    ↓
拦截器 JwtTokenAdminInterceptor.preHandle()
    ↓ 解析 token 拿到用户 ID
    ↓ BaseContext.setCurrentId(empId);     ← 存入
    ↓
Controller → Service → Mapper → AutoFillAspect
                                ↓
                      BaseContext.getCurrentId()  ← 取出
```

### 11.3 ThreadLocal 原理（简化版）

```java
// BaseContext 内部长这样（简化）：
public class BaseContext {
    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);    // 存在当前线程的"口袋"里
    }

    public static Long getCurrentId() {
        return threadLocal.get();  // 从当前线程的"口袋"里取
    }
}
```

**关键：每个请求是一个独立线程，ThreadLocal 的值在线程之间隔离。**

```
线程1（处理用户A的请求）：threadLocal.get() → 1
线程2（处理用户B的请求）：threadLocal.get() → 2
线程3（处理用户A的另一个请求）：threadLocal.get() → 1
```

互不干扰。这就是为什么 AutoFillAspect 能"神奇地"知道当前用户是谁。

---

## 12. 完整流程串联：一次 insert 请求的完整旅程

现在把所有知识点串起来，看一个完整的新增员工请求。

### 流程图

```
① 前端发 POST 请求：新增员工
   POST /admin/employee
   Body: { "name": "张三", "username": "zhangsan", ... }


② JwtTokenAdminInterceptor.preHandle()
   ├── 从请求头取 token
   ├── 解析 token 拿到 empId = 1
   └── BaseContext.setCurrentId(1)          ← 存入线程上下文


③ EmployeeController.save()
   └── employeeService.save(employeeDTO)


④ EmployeeServiceImpl.save()
   ├── 业务校验...（不做填充了！）
   └── employeeMapper.insert(employee)     ← 只传了业务字段


⑤ ⚡ AOP 拦截点 ⚡
   employeeMapper.insert() 即将执行时...


⑥ AutoFillAspect.autoFill(JoinPoint joinPoint)
   │
   ├── Step 1: 获取注解的值
   │   MethodSignature signature = (MethodSignature) joinPoint.getSignature();
   │   OperationType value = signature.getMethod()
   │       .getAnnotation(AutoFill.class).value();   → OperationType.INSERT
   │
   ├── Step 2: 获取方法参数
   │   Object[] args = joinPoint.getArgs();
   │   Object entry = args[0];           → 得到 Employee 对象
   │
   ├── Step 3: 准备数据
   │   LocalDateTime now = LocalDateTime.now();      → 2026-06-03 10:30:00
   │   Long currentId = BaseContext.getCurrentId();  → 1
   │
   ├── Step 4: value == INSERT，执行 4 个字段赋值
   │   Method setCreateTime = entry.getClass()
   │       .getDeclaredMethod("setCreateTime", LocalDateTime.class);
   │   setCreateTime.invoke(entry, now);      → employee.setCreateTime(now)
   │
   │   Method setUpdateTime = entry.getClass()
   │       .getDeclaredMethod("setUpdateTime", LocalDateTime.class);
   │   setUpdateTime.invoke(entry, now);      → employee.setUpdateTime(now)
   │
   │   Method setCreateUser = entry.getClass()
   │       .getDeclaredMethod("setCreateUser", Long.class);
   │   setCreateUser.invoke(entry, currentId); → employee.setCreateUser(1)
   │
   │   Method setUpdateUser = entry.getClass()
   │       .getDeclaredMethod("setUpdateUser", Long.class);
   │   setUpdateUser.invoke(entry, currentId); → employee.setUpdateUser(1)
   │
   └── 填充完成，放行


⑦ employeeMapper.insert(employee) 真正执行
   此时 employee 的 4 个字段已经全部填好了


⑧ BaseContext.removeCurrentId()     ← 请求结束，清除 ThreadLocal
```

### 时序图

```
Client        Interceptor      Controller      Service      AutoFillAspect      Mapper
  │                │                │              │              │                │
  ├─POST──────────→│                │              │              │                │
  │                ├─setCurrentId─→ │              │              │                │
  │                ├───────────────→│              │              │                │
  │                │                ├─────────────→│              │                │
  │                │                │              ├─────────────→│                │
  │                │                │              │  autoFill()  │                │
  │                │                │              │  填充 createTime, etc.        │
  │                │                │              │──────────────→│                │
  │                │                │              │              insert()         │
  │                │                │              │              ├───────────────→│
  │                │                │              │              │←───────────────┤
  │                │    ←───────────┤←─────────────┤←─────────────┤                │
  │←───────────────┤                │              │              │                │
```

---

## 13. 动手实验：自己做一遍

### 实验 1：自定义一个注解

```java
// 1. 定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMe {
    String value() default "";   // default "" 表示可以不填，默认空字符串
}

// 2. 使用注解
@LogMe("开始处理订单")
public void processOrder(Order order) {
    // ...
}

// 3. 读取注解
public class Main {
    public static void main(String[] args) throws Exception {
        Method method = Main.class.getDeclaredMethod("processOrder", Order.class);
        LogMe annotation = method.getAnnotation(LogMe.class);
        System.out.println(annotation.value());  // 输出：开始处理订单
    }
}
```

### 实验 2：反射调用 setter

```java
public class ReflectionDemo {
    public static void main(String[] args) throws Exception {
        // 创建一个对象
        Person p = new Person();

        // 正常写法
        p.setName("张三");

        // 反射写法（效果完全一样）
        Method setName = p.getClass().getDeclaredMethod("setName", String.class);
        setName.invoke(p, "张三");

        System.out.println(p.getName());  // 输出：张三
    }
}

class Person {
    private String name;
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }
}
```

### 实验 3：写一个简单的 AOP 切面

```java
@Aspect
@Component
public class LogAspect {

    // 拦截所有 Controller 的方法
    @Pointcut("execution(* com.example.controller.*.*(..))")
    public void controllerPointcut() {}

    @Before("controllerPointcut()")
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        System.out.println("调用方法：" + methodName + "，参数：" + Arrays.toString(args));
    }

    @AfterReturning(pointcut = "controllerPointcut()", returning = "result")
    public void logAfter(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        System.out.println("方法返回：" + methodName + "，结果：" + result);
    }
}
```

### 实验 4：理解 ThreadLocal

```java
public class ThreadLocalDemo {
    // 共享的 ThreadLocal（static）
    static ThreadLocal<String> user = new ThreadLocal<>();

    public static void main(String[] args) {
        // 线程 1
        new Thread(() -> {
            user.set("用户A");
            System.out.println(Thread.currentThread().getName() + ": " + user.get());
            // 输出：Thread-0: 用户A
        }).start();

        // 线程 2
        new Thread(() -> {
            user.set("用户B");
            System.out.println(Thread.currentThread().getName() + ": " + user.get());
            // 输出：Thread-1: 用户B
        }).start();

        // 主线程
        System.out.println("main: " + user.get());
        // 输出：main: null（因为主线程没 set 过）
    }
}
```

---

## 14. 常见问题 FAQ

### Q1: 为什么用常量类 AutoFillConstant，直接写字符串不行吗？

**可以，但不好。** IDE 不会检查字符串拼写：

```java
// ❌ 如果打错字，编译不报错，运行才报错
Method m = entry.getClass().getDeclaredMethod("setCreateTiem", LocalDateTime.class);
//                                                         ↑ typo! 运行时异常

// ✅ 用常量，如果 AutoFillConstant.SET_CREATE_TIEM 打错了，编译就报错
Method m = entry.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
```

### Q2: getDeclaredMethod 和 getMethod 有什么区别？

| 方法 | 能拿到的 |
|------|---------|
| `getDeclaredMethod()` | 当前类**自己声明**的方法（包括 private） |
| `getMethod()` | 当前类及**所有父类**的 **public** 方法 |

`@Data`（Lombok）生成的是 public 方法，两个都能用，但 `getDeclaredMethod` 更精确。

### Q3: 如果我的实体类没有 setCreateTime 方法会怎样？

运行时会抛 `NoSuchMethodException`。所以所有需要自动填充的实体类必须遵循相同的命名约定——都有 `setCreateTime`、`setUpdateUser` 等 setter 方法。

这就是为什么项目中用 `@Data` 注解——Lombok 自动生成所有 setter，保证一致性。

### Q4: 为什么不直接在 Service 层填充？搞这么复杂？

**权衡：**

| 方案 | 优点 | 缺点 |
|------|------|------|
| Service 层手动填充 | 简单直观 | 重复代码多，容易漏，改起来要改 N 处 |
| AOP 自动填充 | 一处定义，全局生效，不会漏 | 概念多，对新手不友好 |

项目越来越大时，AOP 方案的优势会越来越明显。你现在觉得复杂，但以后管理 50+ 个实体类时就会感谢这个设计。

### Q5: @Before 和 @Around 有什么区别？

```java
// @Before：只能在目标方法执行前做事情
@Before("pointcut()")
public void before(JoinPoint jp) {
    // 目标方法前执行
}
// 目标方法自动执行

// @Around：完全控制目标方法——可以决定执行不执行、什么时候执行
@Around("pointcut()")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    // 目标方法执行前
    System.out.println("前");

    Object result = pjp.proceed();  // 手动调用目标方法

    // 目标方法执行后
    System.out.println("后");
    return result;
}
```

这里用 `@Before` 就够了——只需要在 insert/update 执行前填充字段，不需要包裹整个方法。

### Q6: 为什么 entry.getClass() 得到的是代理类？

Spring AOP 默认使用 JDK 动态代理或 CGLIB 代理。如果你 `entry.getClass().getName()` 打印出来，可能看到类似 `com.sky.entity.Employee$$EnhancerBySpringCGLIB$$...` 的东西。

但放心——**代理类是原始类的子类**，`getDeclaredMethod("setCreateTime", ...)` 在子类中也能找到（继承来的），所以代码正常工作。

### Q7: 多个参数时 joinPoint.getArgs() 返回什么？

```java
// 如果 mapper 方法长这样：
void updateWithCondition(Employee e, String condition, int status);

// 那么：
Object[] args = joinPoint.getArgs();
// args[0] = Employee 对象
// args[1] = "someCondition"
// args[2] = 1
```

**约定：本项目所有需要自动填充的方法，第一个参数就是实体对象。** 所以用 `args[0]`。

---

## 15. 速查表

### 注解相关

| 注解 | 作用 |
|------|------|
| `@Target` | 定义注解能贴在哪里（方法、类、字段...） |
| `@Retention` | 定义注解保留到什么时候（源码、字节码、运行时） |
| `@interface` | 定义注解的关键字 |
| `@Aspect` | 声明这是切面类 |
| `@Pointcut` | 定义切入点表达式 |
| `@Before` | 前置通知 |
| `@After` | 后置通知 |
| `@Around` | 环绕通知 |
| `@Component` | 让 Spring 管理当前类 |

### 反射 API

| API | 作用 |
|-----|------|
| `obj.getClass()` | 获取对象的 Class |
| `clazz.getDeclaredMethod(name, paramTypes...)` | 获取方法（包括 private） |
| `method.invoke(obj, args...)` | 调用方法 |
| `method.getAnnotation(AnnoClass.class)` | 获取方法上的指定注解 |
| `field.setAccessible(true)` | 使 private 字段可访问 |

### ThreadLocal

| API | 作用 |
|-----|------|
| `threadLocal.set(value)` | 给当前线程存值 |
| `threadLocal.get()` | 从当前线程取值 |
| `threadLocal.remove()` | 清除当前线程的值（防止内存泄漏） |

### JoinPoint

| API | 作用 |
|-----|------|
| `joinPoint.getSignature()` | 获取方法签名 |
| `joinPoint.getArgs()` | 获取方法参数 |
| `joinPoint.getTarget()` | 获取目标对象 |

### MethodSignature

| API | 作用 |
|-----|------|
| `signature.getMethod()` | 获取 `java.lang.reflect.Method` 对象 |
| `signature.getName()` | 获取方法名 |
| `signature.getReturnType()` | 获取返回类型 |

---

## 总结：如果你只能记住一件事

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  @AutoFill 注解 = 标签（"我需要自动填充"）                 │
│  @Pointcut = 规则（"拦截 com.sky.mapper 包下带标签的方法"） │
│  @Before = 时机（"目标方法执行前"）                        │
│  反射 = 手段（"我不知道对象是什么类型，但我知道它有         │
│         setCreateTime 方法，我反射去调用"）                 │
│                                                            │
│  四个概念合在一起：                                         │
│  在 mapper 方法执行前，拦截带 @AutoFill 注解的方法，        │
│  通过反射调用实体对象的 setter，填充公共字段。              │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**下一步建议：**

1. 去 IDE 里给 `autoFill()` 方法打一个断点
2. 发起一个新增员工的请求
3. 看变量 `joinPoint`、`signature`、`entry` 在断点时的真实值
4. 你会发现"哦，原来就是这么回事"

动手调试比看十遍教程都有效。

---

> 本文档专门为 `sky-take-out` 项目的 `AutoFillAspect.java` 编写。
> 如果你对项目中其他机制（JWT、拦截器、全局异常处理等）有疑问，参考 `docs/核心机制详解.md`。
