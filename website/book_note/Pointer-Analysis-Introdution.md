# 指针分析简介

指针分析关注的问题是：**一个指针变量在程序运行时可能指向哪些对象**。

例如：

```java
void main() {
    A x = new A(); // o1
    A y = x;
    A z = new A(); // o2
}
```

这里 `x` 和 `y` 都可能指向 `o1`，`z` 可能指向 `o2`。  
于是可以写成：

$$
pt(x) = \{o_1\}, \quad pt(y) = \{o_1\}, \quad pt(z) = \{o_2\}
$$

其中 $pt(p)$ 被称为 **Points-to Set（指向集）**。

它表示指针 $p$ 在程序执行过程中，**可能**指向的所有对象集合。


## Pointer Analysis 的基本形式

指针分析通常是 `may analysis`。  
也就是说，如果分析结果里有：

$$
o \in pt(x)
$$

它表示 `x` **可能**指向对象 $o$，而不是一定指向 $o$。

所以指针分析的结果一般是 over-approximation：

- 可以多算一些实际上不会发生的指向关系
- 但是不能漏掉真实执行中可能发生的指向关系

如果程序中的抽象对象集合是：

$$
O = \{o_1, o_2\}
$$

那么某个指针变量的 points-to set 就是 $O$ 的一个子集：

$$
pt(x) \in P(O)
$$

例如：

$$
P(O) = \{\emptyset, \{o_1\}, \{o_2\}, \{o_1, o_2\}\}
$$

这里的 $P(O)$ 是幂集。  
所以从格的角度看，指针分析就是在 powerset lattice 上不断做集合合并，直到结果不再变化。


## Heap Abstraction

静态分析不可能真的把运行时创建出来的每一个对象都区分开。  
因此需要先做 **heap abstraction**，把运行时对象映射到有限个抽象对象上。

课程中最常见的是 **allocation-site abstraction**：

- 每一条 `new` 语句对应一个抽象对象
- 即使这条 `new` 在循环里执行很多次，也仍然只对应一个抽象对象

例如：

```java
while (...) {
    x = new A(); // o1
}
```

这条 `new A()` 在运行时可能创建很多个真实对象，但在静态分析里统一抽象成 $o_1$。

这样做会损失精度，不过它把对象集合变成了有限集合，后面的 points-to set 才能被算法求出来。


## 指针分析关心的语句

为了先把核心问题讲清楚，课程中会把 Java 程序简化成几类和引用传播有关的语句：

```java
x = new T();  // New
x = y;        // Assign
x.f = y;      // Store
y = x.f;      // Load
```

这四类语句分别描述了对象如何在程序中流动：

- `new`：创建一个新的抽象对象
- `assign`：对象从一个变量流向另一个变量
- `store`：对象被写入某个字段
- `load`：对象从某个字段读出

例如：

```java
x = new A(); // o1
y = new B(); // o2
x.f = y;
z = x.f;
```

这里 `x` 指向 $o_1$，`y` 指向 $o_2$。  
因此 `x.f = y` 实际上是把 $pt(y)$ 写入 $o_1.f$，后面的 `z = x.f` 又从 $o_1.f$ 读出对象。

最后可以得到：

$$
pt(z) = \{o_2\}
$$


## 字段为什么写成 $o_i.f$

这里要注意，字段信息一般不直接记成 `x.f`，而是记成 $o_i.f$。

看这个例子：

```java
1: x = new T(); // o1
2: y = x;
3: x.f = z;
4: w = y.f;
```

如果我们把第 3 行的信息记在 `x.f` 上，那么第 4 行读的是 `y.f`，分析器还需要额外判断 `x.f` 和 `y.f` 是否是同一个字段。

但如果先根据 points-to set 展开：

$$
pt(x) = \{o_1\}, \quad pt(y) = \{o_1\}
$$

那么：

- `x.f = z` 实际写入 $o_1.f$
- `w = y.f` 实际读取 $o_1.f$

于是字段信息就可以通过同一个抽象对象 $o_1$ 连接起来。

简单来说：

- `x.f` 依赖变量名，而变量 `x` 后面可能指向别的对象
- $o_i.f$ 依赖抽象对象，而 $o_i$ 是 allocation site 给出的稳定标识


## 精度维度

指针分析有几个常见的精度开关。不同选择会直接影响结果精度和算法开销。


### Context Sensitivity

`context-sensitive` 会区分同一个方法在不同调用上下文中的分析结果。  
`context-insensitive` 则把所有调用混在一起。

例如：

```java
Object id(Object p) {
    return p;
}

void main() {
    A a = new A(); // o1
    B b = new B(); // o2
    Object x = id(a);
    Object y = id(b);
}
```

如果不区分上下文，`id` 的参数 `p` 会同时收到 $o_1$ 和 $o_2$，返回值也会混在一起。  
如果区分上下文，就可以知道第一次调用返回 $o_1$，第二次调用返回 $o_2$。


### Flow Sensitivity

`flow-sensitive` 会考虑语句顺序。  
`flow-insensitive` 不考虑语句顺序，只根据程序中出现过的约束求一个整体结果。

例如：

```java
x = new A(); // o1
x = new B(); // o2
```

如果是 flow-sensitive，那么第二行之后 `x` 只指向 $o_2$。  
如果是 flow-insensitive，则通常得到：

$$
pt(x) = \{o_1, o_2\}
$$

课程中后面的基础算法一般采用 flow-insensitive，因此更像是在整段程序上收集约束并求解。


### Field Sensitivity

`field-sensitive` 会区分不同字段：

```java
x.f = a;
x.g = b;
```

它会分别维护 $o_i.f$ 和 $o_i.g$。  
如果是 `field-insensitive`，则可能把一个对象的所有字段放在一起，精度会更低。


### Heap Abstraction

heap abstraction 决定运行时对象如何变成静态分析中的抽象对象。

例如：

```java
x = new A(); // o1
y = new A(); // o2
```

这里两条 `new` 出现在不同 allocation site，所以会被抽象成两个对象。  
如果同一条 `new` 在循环里执行很多次，则仍然是同一个抽象对象。


## 本章默认设定

后面的指针分析规则通常采用：

- context-insensitive
- flow-insensitive
- field-sensitive
- allocation-site heap abstraction

也就是说，暂时不区分调用上下文，不区分语句顺序，但是会区分字段，并且用 `new` 语句位置来抽象堆对象。

在这个设定下，指针分析的目标就是：

> 对程序中的每个指针变量和每个抽象字段 $o_i.f$，求出它们的 points-to set。

后面就可以把不同语句翻译成约束，再通过 Pointer Flow Graph 和 worklist 算法求解。
