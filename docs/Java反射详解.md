# Java 反射详解

## 前言：为什么需要反射？

假设你写了一段代码：

```java
User user = new User();
user.setName("张三");
```

这段代码的每一步——`User` 类、`setName` 方法——**在编译时就已经确定**。编译器知道 `User` 长什么样，知道它有哪些方法，代码不写完你连编译都过不了。

但问题来了：如果程序**运行时**，你才拿到一个类名、方法名，甚至根本不知道这个对象是什么类型，你怎么去调用它的方法？

这就是反射要解决的问题：**在运行时，动态地获取类的信息、创建对象、调用方法、访问字段。**

---

## 一、什么是反射

> 反射（Reflection）是 Java 提供的一种机制，允许程序在**运行期间**检查或修改自身的行为——包括类的结构、方法、字段、注解等，即使这些信息在编译时是未知的。

通俗地说：**反射让你在程序跑起来之后，还能像照镜子一样看清一个类长什么样，并且操控它。**

---

## 二、反射的核心入口：Class 对象

### 2.1 什么是 Class 对象？

JVM 中，每一个被加载的类，JVM 都会为它创建一个唯一的 `Class` 对象。这个 `Class` 对象就是这个类的"说明书"，里面记录了：

- 这个类叫什么名字
- 有哪些字段（Field）
- 有哪些方法（Method）
- 有哪些构造器（Constructor）
- 有哪些注解（Annotation）
- 实现了哪些接口

**`Class` 对象就是反射的大门。拿到它，才能做后续的一切操作。**

### 2.2 如何获取 Class 对象？

三种方式：

```java
// 方式1：通过类名.class（编译时就知道类名）
Class<User> clazz1 = User.class;

// 方式2：通过对象的 getClass()（已有对象实例）
User user = new User();
Class<?> clazz2 = user.getClass();

// 方式3：通过 Class.forName("全限定类名")（只知道类名的字符串）
Class<?> clazz3 = Class.forName("com.example.User");
```

三种方式最终拿到的是**同一个 Class 对象**：

```java
System.out.println(clazz1 == clazz2); // true
System.out.println(clazz2 == clazz3); // true
```

> **注意：** 方式3 要求类必须在 classpath 中，否则抛 `ClassNotFoundException`。正是这种方式，让 Spring 能够通过配置文件中的字符串类名来加载和装配 Bean。

---

## 三、反射能做什么

拿到 `Class` 对象之后，就像拿到了类的说明书，你可以做四件核心事情：

| 操作 | 对应 API | 返回类型 |
|------|----------|----------|
| 创建对象 | `clazz.newInstance()` / `getConstructor().newInstance()` | Object |
| 调用方法 | `clazz.getMethod("方法名", 参数类型...)` + `invoke()` | Method |
| 访问字段 | `clazz.getField("字段名")` / `getDeclaredField()` | Field |
| 读取注解 | `method.getAnnotation(注解类.class)` | Annotation |

### 3.1 创建对象

```java
// 获取 Class 对象
Class<User> clazz = User.class;

// 调用无参构造器创建实例
User user = clazz.getDeclaredConstructor().newInstance();

// 或者调用有参构造器
Constructor<User> constructor = clazz.getConstructor(String.class, Integer.class);
User user2 = constructor.newInstance("张三", 25);
```

### 3.2 调用方法

这是反射最核心的用途，也是你项目里 `AutoFillAspect` 做的事。

```java
// 假设有一个 user 对象
User user = new User();

// 通过反射调用 setName("张三")
Method setNameMethod = user.getClass().getMethod("setName", String.class);
setNameMethod.invoke(user, "张三");

// 此时 user.getName() 返回 "张三"
```

拆解这段代码：

```java
Method method = clazz.getMethod("方法名", 参数1的类型, 参数2的类型, ...);
Object result = method.invoke(对象, 参数1的值, 参数2的值, ...);
```

- `getMethod("setName", String.class)` —— 去类里找名为 `setName`、接受一个 `String` 参数的方法
- `invoke(user, "张三")` —— 在 `user` 这个对象上执行这个方法，传入参数 `"张三"`

### 3.3 访问字段

```java
// 获取公共字段
Field nameField = clazz.getField("name");

// 获取任意字段（包括 private）
Field nameField = clazz.getDeclaredField("name");
nameField.setAccessible(true); // 绕过 private 限制
nameField.set(user, "李四");    // 给 user 的 name 字段赋值为 "李四"
Object value = nameField.get(user); // 读取 user 的 name 字段
```

### 3.4 读取注解

这是你项目中切面代码用到的关键机制：

```java
// 获取方法上的 @AutoFill 注解
Method method = clazz.getMethod("someMethod");
AutoFill annotation = method.getAnnotation(AutoFill.class);
OperationType type = annotation.value(); // 读取注解中的值
```

---

## 四、完整示例：用反射"抄"一遍普通代码

### 普通写法

```java
public class Main {
    public static void main(String[] args) {
        User user = new User();
        user.setName("张三");
        user.setAge(25);
        System.out.println(user.getName()); // 输出：张三
    }
}
```

### 反射写法

```java
public class Main {
    public static void main(String[] args) throws Exception {
        // 1. 获取 Class 对象（相当于知道有 User 这个类）
        Class<?> clazz = Class.forName("com.example.User");

        // 2. 获取无参构造器并创建对象（相当于 new User()）
        Object user = clazz.getDeclaredConstructor().newInstance();

        // 3. 获取方法（相当于找到 setName 方法）
        Method setName = clazz.getMethod("setName", String.class);
        Method setAge = clazz.getMethod("setAge", Integer.class);
        Method getName = clazz.getMethod("getName");

        // 4. 调用方法（相当于 user.setName("张三")）
        setName.invoke(user, "张三");
        setAge.invoke(user, 25);

        // 5. 调用方法获取结果（相当于 user.getName()）
        Object name = getName.invoke(user);
        System.out.println(name); // 输出：张三
    }
}
```

对比可以看到：反射做的事情和直接写代码完全一样，只是每一步都变成了"字符串+API调用"，**把编译时确定的事情推迟到了运行时**。

---

## 五、核心 API 速查

### 5.1 Class 核心方法

```java
Class<?> clazz = User.class;

// —— 基本信息 ——
clazz.getName();              // 全限定类名，如 "com.example.User"
clazz.getSimpleName();        // 简单类名，如 "User"
clazz.getPackage().getName(); // 包名，如 "com.example"
clazz.getSuperclass();        // 父类的 Class

// —— 构造器 ——
clazz.getConstructor(参数类型...);        // 获取 public 构造器
clazz.getDeclaredConstructor(参数类型...); // 获取任意构造器（含 private）

// —— 方法 ——
clazz.getMethod("方法名", 参数类型...);             // 获取 public 方法（含继承的）
clazz.getDeclaredMethod("方法名", 参数类型...);      // 获取本类声明的任意方法（不含继承的）
clazz.getMethods();                                // 获取所有 public 方法（含继承的）
clazz.getDeclaredMethods();                        // 获取本类声明的所有方法

// —— 字段 ——
clazz.getField("字段名");          // 获取 public 字段（含继承的）
clazz.getDeclaredField("字段名");  // 获取本类声明的任意字段
clazz.getFields();               // 获取所有 public 字段
clazz.getDeclaredFields();       // 获取本类声明的所有字段

// —— 注解 ——
clazz.getAnnotation(注解类.class);        // 获取类上的指定注解
clazz.getAnnotations();                  // 获取类上的所有注解
```

### 5.2 Method 核心方法

```java
Method method = clazz.getMethod("setName", String.class);

method.getName();               // 方法名
method.getReturnType();         // 返回类型
method.getParameterTypes();     // 参数类型数组
method.getAnnotation(注解.class); // 方法上的指定注解
method.invoke(对象, 参数...);     // 调用这个方法
```

### 5.3 Field 核心方法

```java
Field field = clazz.getDeclaredField("name");

field.getName();               // 字段名
field.getType();               // 字段类型
field.setAccessible(true);     // 绕过 private 限制
field.set(对象, 值);            // 给字段赋值
field.get(对象);               // 读取字段值
```

---

## 六、getDeclaredXxx vs getXxx 的区别

这是一个非常容易混淆的点，一句话说明白：

| 方法前缀 | 范围 | 继承 | 可见性 |
|----------|------|------|--------|
| `getXxx()` | 只返回 **public** 的 | **包含父类继承的** | public only |
| `getDeclaredXxx()` | 返回**本类声明的所有** | **不包含继承的** | public/protected/private 全部 |

```java
// 例子：获取 setName 方法
clazz.getMethod("setName", String.class);          // setName 是 public → ✅ 能获取到
clazz.getDeclaredMethod("setName", String.class);  // setName 是本类声明的 → ✅ 能获取到

// 区别场景：获取 toString 方法
clazz.getMethod("toString");          // ✅ 能获取到（从 Object 继承来的 public 方法）
clazz.getDeclaredMethod("toString");  // ❌ 抛异常（本类没声明 toString，这是 Object 的）
```

> **实用口诀：想要父类继承的找 `getXxx`，只要本类的找 `getDeclaredXxx`。**

---

## 七、setAccessible(true) 是干什么的

```java
Field nameField = clazz.getDeclaredField("name"); // name 是 private 的
nameField.setAccessible(true); // 强行打开访问权限
nameField.set(user, "张三");   // 现在可以修改 private 字段了
```

Java 的访问控制（private、protected）在反射面前可以被**暴力绕过**。`setAccessible(true)` 就是那个"暴力"——关闭 Java 的访问检查。

**为什么要这样设计？** 因为有很多正当用途需要访问私有成员：
- 序列化框架（Jackson、Gson）通过反射读写私有字段
- ORM 框架（MyBatis、Hibernate）通过反射给 private 字段赋值
- Spring 通过反射给 `@Autowired` 的 private 字段注入依赖
- 单元测试中访问私有成员

> **注意：** Java 17+ 模块化系统增加了限制，某些核心 JDK 内部的 private 成员不再能被随意 `setAccessible`。但你自己写的类和第三方库不受影响。

---

## 八、反射为什么"感觉慢"

反射比直接调用慢，主要有两个原因：

1. **运行时解析**：每次反射调用，JVM 都需要去查找方法名、检查参数类型、验证访问权限。而直接调用在编译时就确定了字节码指令。
2. **不能 JIT 内联**：直接调用可以被即时编译器优化（比如方法内联），反射调用很难做这类优化。

但实际业务中，这个"慢"通常可以忽略不计。Spring 用了这么多反射，你的应用并没有因此变慢——因为：
- 反射通常只在**启动阶段**进行（Spring 初始化 Bean 时），运行期走的是初始化好的代理
- `setAccessible(true)` 在 Java 18+ 已经被大幅优化
- `MethodHandle`（Java 7+）提供了更快的替代方案

> **结论：不需要害怕反射的性能开销。你不应该在循环里反复反射调用，但业务代码中用反射完全没问题。**

---

## 九、Spring Boot 哪些地方用了反射

Spring Boot 几乎无处不在用反射，列举几个最常见的地方：

| Spring 功能 | 哪些反射操作 |
|-------------|-------------|
| **依赖注入 `@Autowired`** | 通过反射找到被标注的字段，`setAccessible(true)` 后注入实例 |
| **Bean 的创建** | `Class.forName()` 加载配置中的类，`newInstance()` 创建实例 |
| **`@Value` 注入配置值** | 反射找到 `@Value` 标注的字段，将配置文件的值注入 |
| **`@Transactional`** | AOP 切面通过反射获取方法上的注解，判断是否需要开启事务 |
| **AOP 切面** | 通过反射获取被拦截方法的注解信息（就像你项目里的 `AutoFill`） |
| **`@RequestMapping` / `@GetMapping`** | Spring MVC 启动时扫描所有 Controller 的方法，反射读取注解，建立 URL 到方法的映射 |
| **Jackson / JSON 序列化** | 通过反射获取对象的 getter/setter，将 Java 对象和 JSON 互相转换 |
| **MyBatis 映射** | 通过反射将查询结果 set 到实体类的 private 字段 |

> 可以这么说：**没有反射，就没有 Spring Boot。** 你享受的自动装配、注解驱动、AOP 切面，底层全是反射在支撑。

---

## 十、总结：一张图理解反射的本质

```
编译时已知（普通代码）                        运行时才知道（反射代码）
─────────────────────────                    ─────────────────────────
User user = new User();         ←→          Class<?> c = Class.forName("User");
                                             Object obj = c.newInstance();

user.setName("张三");           ←→          Method m = c.getMethod("setName", String.class);
                                             m.invoke(obj, "张三");

你写代码的时候就知道一切              运行起来了，才知道该调用哪个类的哪个方法
```

**反射 = 把代码的"确定性"从编译时推迟到运行时。**

初学觉得晦涩很正常的——因为你在 IDE 里写 `user.setName()` 会弹出智能提示，而反射写 `"setName"` 就是裸字符串，拼错了编译器也不报错。**这不代表反射复杂，你只是少了编辑器帮你**。

当你理解了反射，再去看 Spring 的源码、AOP 的原理、框架的底层机制，就会觉得"原来是这么回事"。
