# Interprocedural Analysis

前面的数据流分析大多默认：分析只在单个过程内部进行，函数调用点被当作某种“黑盒”处理。  
但真实程序的行为往往跨越多个方法传播，因此只做 `intraprocedural analysis` 往往不够。

## 1. Why Use Interprocedural Analysis

先看一个最简单的问题：

```java
int id(int x) {
    return x;
}

void main() {
    int a = 10;
    int b = id(a);
}
```

如果只做过程内分析，那么在 `main` 里看到 `b = id(a)` 时，我们通常只知道：

- 这里调用了某个函数
- 它可能读取参数
- 它会返回某个值

但如果完全不知道 `id` 的函数体，就很难推出 `b = 10`。  
也就是说，**信息在调用点被截断了**。

过程间分析要解决的，正是这种“信息跨过程传播”的问题：

- 实参如何流入被调函数
- 被调函数内部如何变换信息
- 返回值如何再流回调用点

因此我们需要一种比单个 `CFG` 更大的程序表示，把“方法之间如何调用”也建模出来。

## 2. Call Graph

`Call Graph` 是过程间分析最基础的结构。

它的节点是方法 `method`，边表示：

- 某个方法里存在一个调用点
- 这个调用点在运行时可能调用另一个方法

所以一条边

$$
m \rightarrow n
$$

表示：方法 `m` 里某个调用语句，可能把控制流转移到方法 `n`。

`Call Graph` 的作用至少有两个：

- 找出哪些方法是 `reachable` 的
- 找出每个调用点可能对应哪些目标方法

静态分析里构造的调用图通常不是“精确真实执行图”，而是一个 **sound over-approximation**：

- 宁可多算一些可能目标
- 也不能漏掉真正可能发生的调用

因为一旦漏边，后续分析就可能不 sound。

## 3. Java 的三种调用方式

在 Java 中，从过程间分析的角度，常把方法调用分成三类：

### Static Call

`static` 方法调用没有接收者对象，目标在编译期就已经确定，例如：

```java
A.f();
```

这类调用的目标通常只有一个，因此最容易解析。

### Special Call

`special` 调用包括：

- 构造函数调用 `constructor`
- 私有方法调用 `private method`
- `super` 调用

例如：

```java
super.f();
this.<init>();
```

这类调用也通常只有唯一目标，不需要像虚调用那样枚举很多运行时类型。

### Virtual Call

最麻烦的是 `virtual call`，例如：

```java
a.f();
```

这里真正被调用的方法，不仅取决于变量 `a` 的声明类型，还取决于 **运行时对象的实际类型**。

例如：

```java
A a = ...;
a.f();
```

若运行时 `a` 实际上可能指向 `A`、`B`、`C` 的对象，并且这些类都对 `f()` 有不同实现，那么这一次调用就可能落到多个不同目标上。

所以：

- `static call` 往往只有一个目标
- `special call` 往往只有一个目标
- `virtual call` 可能对应多个目标

也正因此，构造 `Call Graph` 的核心难点几乎都集中在 `virtual call` 上。

## 4. dispatch 函数

为了描述“一个调用最终会落到哪个方法实现”，课程里通常定义 `dispatch(c, m)`。

它的含义是：

- 给定一个类 `c`
- 给定一个方法签名 `m`（更准确地说，通常是方法名加参数类型形成的子签名）
- 从类 `c` 开始向父类链不断查找
- 返回第一个真正包含该方法实现的方法

可以写成下面这种逻辑：

```text
dispatch(c, m):
    if c declares method m:
        return that method
    else:
        return dispatch(superclass(c), m)
```

它实际上回答的问题是：

> 如果接收者对象的运行时类型是 `c`，那么这次方法调用最终会执行哪一个实现？

举个例子：

```java
class A {
    void f() { ... }
}

class B extends A { }

class C extends B {
    void f() { ... }
}
```

那么：

- `dispatch(A, f) = A.f`
- `dispatch(B, f) = A.f`
- `dispatch(C, f) = C.f`

因为 `B` 没有自己实现 `f`，所以它会沿继承链继续向上找到 `A.f`。

## 5. CHA 算法

`CHA` 是 `Class Hierarchy Analysis`。

它的核心思想非常直接：

- 利用类继承结构
- 对虚调用做保守枚举
- 把所有可能的目标方法都加进调用图

### 对不同调用类型的处理

对于 `static call` 和 `special call`：

- 目标通常是唯一的
- 直接解析即可

对于 `virtual call`：

假设调用点是

```java
r = x.k(...)
```

并且 `x` 的声明类型是 `T`，那么 `CHA` 会：

1. 找出 `T` 的所有可能子类
2. 对每个候选类 `c`
3. 计算 `dispatch(c, k)`
4. 把所有得到的方法都作为这次调用的可能目标

也就是说，`CHA` 对虚调用的策略本质上是：

> 只要某个类在类型层面上有可能成为接收者运行时类型，就把它对应的分派结果都算进去。

### 为什么它是 sound 的

如果运行时对象真的属于某个 `T` 的子类 `c`，那么实际目标方法一定是 `dispatch(c, k)`。  
而 `CHA` 恰好把所有这种 `c` 都枚举了一遍，因此不会漏掉真实目标。

### 为什么它不够精确

`CHA` 只看 **类层次**，不看 **对象实际会在哪些程序点被创建、流向哪里**。

所以它常常会把一些“类型上可能、但程序实际上永远不会发生”的目标也加入调用图。  
因此它的特点很典型：

- 优点：快、简单、sound
- 缺点：保守，可能引入很多假边

## 6. Call Graph 的构建

有了“如何解析一个调用点”的规则之后，就可以构建整张调用图了。

整体思路是一个从入口出发的可达性展开过程。

### 基本流程

通常从程序入口方法开始，例如 `main`：

- 先把入口方法加入 `reachable methods`
- 扫描这个方法中的每个调用点
- 解析每个调用点可能指向的目标方法
- 为调用图加入边
- 如果发现新的可达方法，就继续处理它

直到没有新方法被加入为止。

可以写成：

```text
CG = empty call graph
RM = empty set of reachable methods
WL = [entry method]

while WL is not empty:
    m = pop(WL)
    if m has not been processed:
        add m to RM
        for each callsite cs in m:
            T = Resolve(cs)
            for each target t in T:
                add edge m -> t to CG
                if t not in RM:
                    add t to WL
```

这里最关键的地方是 `Resolve(cs)`：

- 若 `cs` 是 `static call`，返回唯一静态目标
- 若 `cs` 是 `special call`，返回唯一 special 目标
- 若 `cs` 是 `virtual call`，则按 `CHA` 对所有候选类型做 `dispatch`

### 构建出来的结果是什么

最终我们得到两样东西：

- `reachable methods`
- `call edges`

前者告诉我们哪些方法可能真正参与执行；后者告诉我们跨过程控制流可能如何传播。

这就是后续一切过程间数据流分析的基础。

## 7. ICFG

只有 `Call Graph` 还不够，因为它只告诉我们“方法和方法之间可能有调用关系”，却没有把 **过程内控制流** 和 **过程间调用/返回关系** 统一起来。

为此我们需要 `ICFG`，即 `Interprocedural Control Flow Graph`。

### ICFG 是什么

`ICFG` 可以理解为：

$$
\text{ICFG} = \text{CFG} + \text{Call Edges} + \text{Return Edges}
$$

也就是说：

- 每个方法内部仍然有自己的 `CFG`
- 在调用点和被调方法入口之间加入 `call edge`
- 在被调方法出口和调用点返回后的位置之间加入 `return edge`

这样，原本分散在不同方法里的控制流就被连成一张更大的图。

### 边的含义

在 `ICFG` 中常见的边有：

- `intra-procedural edge`：方法内部普通控制流边
- `call edge`：调用点到被调方法入口
- `return edge`：被调方法出口到调用返回点

如果把一个调用点记为 `call m()`，返回后下一条语句记为 `ret-site`，那么结构大致是：

```text
caller CFG
   |
   v
callsite ------call------> callee entry
   |                           |
   |                           v
   |                      callee CFG
   |                           |
   <------return------- callee exit
   |
   v
return site
```

### 为什么 ICFG 重要

因为过程间数据流分析本质上也在传播信息，而信息传播必须知道：

- 怎样从调用者进入被调者
- 怎样把实参传进去
- 怎样把返回值再带回来

`ICFG` 恰好提供了这种统一视角。  
后面很多过程间分析框架，本质上都是在 `ICFG` 上重新定义“数据如何沿边传播”。

## 小结

这一章的主线可以压缩成一句话：

> 过程间分析就是把“只在单个方法内部传播的信息”，扩展成“能够穿过方法调用边界传播的信息”。

因此整个逻辑链条是：

- 先意识到仅做过程内分析不够
- 再用 `Call Graph` 表示方法之间可能的调用关系
- 用 `dispatch` 形式化动态分派
- 用 `CHA` 近似解析虚调用
- 从入口方法迭代构建整张 `Call Graph`
- 最后把各个方法的 `CFG` 与调用/返回边拼成 `ICFG`

这样，程序就不再是一堆孤立的方法，而是一张可以做过程间传播的大图。
