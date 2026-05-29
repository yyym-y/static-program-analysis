# 污点分析

污点分析（Taint Analysis）是一类信息流安全分析，用来判断不可信数据是否可能流到危险位置。

它关注的问题是：

> 从 source 产生的数据，是否可能经过程序传播到 sink？

如果可能到达，就报告一条潜在安全风险。

## 1. 基本概念

污点分析通常包含三类配置：

- source：不可信输入的来源
- sink：危险操作的位置
- transfer：污点如何通过函数或语句传播

例如：

```java
String name = request.getParameter("name"); // source
String sql = "select * from user where name = '" + name + "'";
statement.execute(sql);                     // sink
```

如果 `request.getParameter` 返回的数据能传播到 `statement.execute`，就可能存在 SQL 注入风险。

## 2. Source 与 Sink

source 是污点的起点，例如：

- HTTP 参数：`request.getParameter`
- 文件内容：`readLine`
- 环境变量：`System.getenv`
- 网络输入：`socket.read`

sink 是敏感操作，例如：

- SQL 执行：`Statement.execute`
- 命令执行：`Runtime.exec`
- 文件写入：`FileOutputStream.write`
- 反射调用：`Method.invoke`
- 日志输出或响应输出

不同漏洞类型会有不同的 source 和 sink 配置。

## 3. Taint Object

课程里的指针分析已经能告诉我们对象如何在程序中流动。污点分析可以把某些抽象对象标记为 tainted object。

例如：

```java
String s = request.getParameter("id"); // source
String t = s;
sink(t);
```

如果 source 调用产生抽象对象 $o_s$，则把 $o_s$ 标记为 tainted。随后指针分析发现 $o_s\in pt(t)$，当 `t` 作为参数传给 sink 时，就报告风险。

因此污点分析可以看成：

> 在指针分析结果上追踪 tainted objects 的传播。

## 4. 传播规则

最基本的传播规则和指针传播类似。

```java
x = source(); // source
y = x;
sink(y);      // sink
```

可以理解为：

1. `source()` 产生 tainted object
2. `x = source()` 让 `x` 指向 tainted object
3. `y = x` 让 tainted object 从 `x` 流到 `y`
4. `sink(y)` 检查 `pt(y)` 中是否有 tainted object

如果有，就存在 source-to-sink flow。

## 5. 与指针分析结合

真实 Java 程序中，污点经常通过对象字段传播：

```java
class Box {
    Object v;
}

Box b = new Box();
b.v = request.getParameter("id"); // source
Object x = b.v;
sink(x);
```

这里必须依赖指针分析知道：

- `b` 指向哪个抽象对象
- `b.v` 对应哪个抽象字段
- source object 是否从字段流到 `x`

根据指针分析规则，数据会沿着 `source object -> o_b.v -> x -> sink argument` 传播。所以污点分析通常不是单独运行，而是和 points-to analysis 结合。

## 6. Taint Transfer

很多 API 不只是简单传递对象，还会从输入生成新的输出。

```java
String a = request.getParameter("id"); // tainted
String b = a.trim();
String c = encode(b);
sink(c);
```

如果 `trim` 返回的新字符串来自 tainted 输入，那么 `b` 也应该 tainted。这样的规则称为 transfer rule。

常见 transfer 形式：

- 参数到返回值：`ret` tainted if `arg` tainted
- 参数到 receiver：`this` tainted if `arg` tainted
- receiver 到返回值：`ret` tainted if `this` tainted
- 多参数合并：任一参数 tainted，则返回值 tainted

例如：

```text
String.concat(base, arg): ret tainted if base or arg tainted
StringBuilder.append(arg): receiver tainted if arg tainted
StringBuilder.toString(): ret tainted if receiver tainted
```

## 7. Sanitizer

sanitizer 是清洗函数，会降低或消除污点风险。

```java
String s = request.getParameter("id"); // tainted
String safe = escapeSql(s);            // sanitized
statement.execute(safe);               // usually safe
```

如果配置中认为 `escapeSql` 能正确清洗 SQL 特殊字符，那么 `safe` 可以不再视为 SQL 注入意义上的 tainted。

需要注意：sanitizer 通常和漏洞类型相关。

- HTML escape 对 XSS 有用，但不一定能防 SQL 注入
- URL encode 不一定能防命令注入
- 不完整或错误的 sanitizer 仍然可能留下漏洞

## 8. Taint Analysis 的形式化视角

可以把污点状态看成一个集合 `Tainted ⊆ O`，其中 `O` 是抽象对象集合。

当遇到 source 时，把新对象加入 `Tainted`。当指针分析传播对象时，污点标记随着对象一起流动。

在 sink 处检查 `pt(arg) ∩ Tainted ≠ ∅`。如果交集非空，说明 sink 的参数可能来自不可信输入。

## 9. 过程间污点分析

污点可以跨方法传播：

```java
String getName(HttpServletRequest req) {
    return req.getParameter("name"); // source
}

void handle(HttpServletRequest req) {
    String s = getName(req);
    sink(s);
}
```

过程间分析需要建立：

- 实参到形参的传播
- 返回值到调用点左侧变量的传播
- receiver 到 `this` 的传播

这和过程间指针分析中的 PFG 边一致，所以可以复用前面的调用建模。

## 10. 典型误报与漏报

污点分析通常也是 may analysis，因此容易有误报：

```java
String s = request.getParameter("id");
if (isValidId(s)) {
    query(s);
}
```

如果分析器不知道 `isValidId` 是有效校验，就可能仍然报告风险。

常见误报来源：

- sanitizer 配置不完整
- 路径条件没有精确建模
- 字符串约束没有精确建模
- 库函数摘要过粗

常见漏报来源：

- source 配置缺失
- sink 配置缺失
- transfer rule 缺失
- 反射、动态加载、native 方法建模不足

## 11. 总结

污点分析的主线可以概括为：

1. 标记 source 产生的 tainted objects
2. 通过赋值、字段、数组、调用关系传播污点
3. 用 transfer rule 处理库函数和字符串操作
4. 用 sanitizer rule 减少无效报警
5. 在 sink 处检查参数是否可能包含 tainted object

最终输出通常是一组 source-to-sink flows，每条 flow 表示一条潜在安全风险。

## 参考资料

- [南京大学软件分析课程主页](https://tai-e.pascal-lab.net/lectures.html)
- [NJU Software Analysis: Security Analysis](https://cs.nju.edu.cn/tiantan/software-analysis/Security.pdf)
- [Java 指针分析综述](https://cs.nju.edu.cn/yueli/papers/crad2023.pdf)
