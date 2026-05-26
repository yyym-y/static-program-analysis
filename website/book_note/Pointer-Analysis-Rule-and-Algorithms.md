# 指针分析规则和算法

这一节关注的问题是：**如何把指针相关语句转化成约束，并用算法求出 points-to set**。

前面已经知道，指针分析维护的是：

$$
pt(p)
$$

也就是指针 $p$ 可能指向的抽象对象集合。  
现在要做的是把 `new`、赋值、字段读写、函数调用都转化成 points-to set 之间的传播关系。


## 指针分析规则

课程中给出的规则可以理解为推导规则：

> 横线上的内容是前提 `premises`，横线下的内容是结论 `conclusion`。

只要前提成立，就可以把结论中的 points-to fact 加入分析结果。


### New

```text
i: x = new T()
---------------
o_i ∈ pt(x)
```

第 $i$ 条 `new` 语句对应一个抽象对象 $o_i$。  
所以：

```java
x = new A(); // i
```

会直接产生：

$$
o_i \in pt(x)
$$


### Assign

```text
x = y      o_i ∈ pt(y)
----------------------
o_i ∈ pt(x)
```

`x = y` 表示 `y` 可能指向的对象也会流到 `x`。

也就是：

$$
pt(y) \subseteq pt(x)
$$


### Store

```text
x.f = y      o_i ∈ pt(x)      o_j ∈ pt(y)
-----------------------------------------
o_j ∈ pt(o_i.f)
```

`x.f = y` 是字段写入。  
如果 `x` 可能指向 $o_i$，那么这条语句实际写入的是 $o_i.f$。

因此：

$$
\forall o_i \in pt(x), \quad pt(y) \subseteq pt(o_i.f)
$$


### Load

```text
y = x.f      o_i ∈ pt(x)      o_j ∈ pt(o_i.f)
---------------------------------------------
o_j ∈ pt(y)
```

`y = x.f` 是字段读取。  
如果 `x` 可能指向 $o_i$，那么读取的是 $o_i.f$，其中的对象会继续流入 `y`。

因此：

$$
\forall o_i \in pt(x), \quad pt(o_i.f) \subseteq pt(y)
$$

这里可以看到，`Assign` 的传播边一开始就能确定，但 `Store` 和 `Load` 依赖 $pt(x)$，所以它们要等 base pointer 的 points-to set 被发现之后才能展开。


## Pointer Flow Graph

为了统一处理这些传播关系，可以构造 **Pointer Flow Graph, PFG**。

PFG 的节点是 pointer，包括：

- 局部变量，例如 `x`、`y`
- 抽象对象字段，例如 $o_i.f$

PFG 的边表示 points-to set 的包含关系。  
如果有边：

$$
p \rightarrow q
$$

就表示：

$$
pt(p) \subseteq pt(q)
$$

也就是 `p` 指向的对象会继续传播到 `q`。

例如：

```java
x = y;
```

会产生边：

$$
y \rightarrow x
$$

字段相关语句则需要结合 base pointer：

```java
x.f = y;
```

如果 $o_i \in pt(x)$，就加入：

$$
y \rightarrow o_i.f
$$

对于：

```java
y = x.f;
```

如果 $o_i \in pt(x)$，就加入：

$$
o_i.f \rightarrow y
$$

因此 PFG 不是完全一次性建好的。  
随着 points-to set 不断扩大，新的字段节点和字段边也会被逐步加入。


## 为什么是 $o_i.f$ 而不是 `x.f`

这个问题本质上和别名有关。

```java
1: x = new T(); // o1
2: y = x;
3: x.f = z;
4: w = y.f;
```

如果通过 `x.f` 来传播，那么第 3 行只能得到：

$$
pt(z) \subseteq pt(x.f)
$$

但是第 4 行读的是 `y.f`，分析器还需要额外知道 `x` 和 `y` 是不是别名。

正确的做法是把字段挂在对象上：

1. 因为 $pt(x) = \{o_1\}$，所以 `x.f = z` 写入 $o_1.f$
2. 因为 $pt(y) = \{o_1\}$，所以 `w = y.f` 读取 $o_1.f$
3. 于是 $pt(z)$ 可以通过 $o_1.f$ 传播到 $pt(w)$

这里 $o_1$ 充当了连接 `x` 和 `y` 的中间对象。  
所以字段节点一般写成 $o_i.f$，而不是 `x.f`。


## Worklist Algorithm

PFG 建好之后，points-to set 可以沿着边传播。  
课程中的算法使用 worklist，核心思想是：每次只传播新增的 points-to facts。

整体结构可以写成：

```text
foreach statement i: x = new T() do
    WL.add(<x, {o_i}>)

foreach statement x = y do
    AddEdge(y, x)

while WL is not empty do
    <n, pts> = WL.remove()
    Δ = pts - pt(n)

    if Δ is not empty then
        pt(n) = pt(n) ∪ Δ

        foreach edge n -> s in PFG do
            WL.add(<s, Δ>)

        if n is a variable x then
            foreach o_i in Δ do
                foreach statement x.f = y do
                    AddEdge(y, o_i.f)

                foreach statement y = x.f do
                    AddEdge(o_i.f, y)
```

其中 `AddEdge(p, q)` 表示往 PFG 中加入一条边：

```text
if edge p -> q is new then
    add p -> q to PFG
    WL.add(<q, pt(p)>)
```

这里要注意，新边加入之后，要把 `pt(p)` 里已经存在的对象也传播到 `q`。  
否则这条边只会收到后面新增的对象，漏掉之前已经算出来的结果。


## 为什么只传播 $\Delta$

设当前已经有：

$$
pt(x) = \{o_1, o_2\}
$$

这次从 worklist 里取出：

$$
pts = \{o_2, o_3\}
$$

那么真正新增的只有：

$$
\Delta = pts - pt(x) = \{o_3\}
$$

因此算法只需要把 $o_3$ 继续向外传播。  
已经传播过的 $o_1$ 和 $o_2$ 不需要反复处理。


## 一个简单例子

```java
1: a = new A(); // o1
2: b = new B(); // o2
3: c = a;
4: c.f = b;
5: d = a.f;
```

初始化时：

```text
WL = <a, {o1}>, <b, {o2}>
PFG has edge a -> c
```

先处理 `a`：

$$
pt(a) = \{o_1\}
$$

沿着边 $a \rightarrow c$ 传播：

$$
pt(c) = \{o_1\}
$$

此时第 4 行 `c.f = b` 可以展开。  
因为 $o_1 \in pt(c)$，所以加入字段边：

$$
b \rightarrow o_1.f
$$

再结合：

$$
pt(b) = \{o_2\}
$$

可以得到：

$$
pt(o_1.f) = \{o_2\}
$$

第 5 行 `d = a.f` 也可以展开。  
因为 $o_1 \in pt(a)$，所以加入：

$$
o_1.f \rightarrow d
$$

最后得到：

$$
pt(d) = \{o_2\}
$$

这说明算法不是按源程序顺序执行，而是在 PFG 上不断传播指向关系。


## 处理函数调用

到过程间之后，指针分析还要处理函数调用，尤其是 virtual call：

```java
r = x.k(a1, a2, ...)
```

这类调用的目标方法取决于 `x` 指向对象的运行时类型。  
因此指针分析和 call graph 构建会互相依赖：

- 要解析 virtual call，需要知道 $pt(x)$
- 要分析被调用方法，需要先把它加入 reachable methods
- 被调用方法中的语句又会产生新的 points-to facts

所以这里通常采用 on-the-fly 的方式：一边做指针分析，一边构建 Call Graph。


## this 变量

在静态分析建模中，`this` 可以看成实例方法中的一个特殊局部变量。

例如：

```java
class A {
    void foo(B p) {
        this.f = p;
    }
}
```

可以认为方法 `foo` 中有一个特殊变量：

$$
foo_{this}
$$

当调用点是：

```java
x.foo(y);
```

并且目标方法解析到 `A.foo` 时，就需要加入：

$$
x \rightarrow A.foo_{this}
$$

也就是 receiver 对象流入被调用方法的 `this`。  
同时，实参也要流入形参：

$$
y \rightarrow p
$$

如果有返回值，还要把被调用方法的返回变量连回调用点左侧变量。


## 过程间指针分析算法

算法中通常维护几类集合或图：

- `WL`：待传播的 points-to facts
- `PFG`：Pointer Flow Graph
- `CG`：Call Graph
- `RM`：reachable methods
- `S`：reachable methods 中的 statements

这里的 `S` 不是全程序语句集合，而是当前已经可达的方法体中的语句。  
当一个新方法加入 `RM` 后，它的方法体才会进入分析。

整体流程可以概括为：

```text
AddReachable(main)

while WL is not empty do
    <n, pts> = WL.remove()
    Δ = Propagate(n, pts)

    if n is a variable x then
        foreach o_i in Δ do
            foreach store statement x.f = y in S do
                AddEdge(y, o_i.f)

            foreach load statement y = x.f in S do
                AddEdge(o_i.f, y)

            foreach call site cs: r = x.k(a1, ..., an) in S do
                m = Dispatch(type(o_i), k)
                AddCallEdge(cs, m)
```

`AddReachable(m)` 负责把新发现的方法加入分析范围：

```text
if m not in RM then
    add m to RM
    add statements of m to S

    foreach statement i: x = new T() in m do
        WL.add(<x, {o_i}>)

    foreach statement x = y in m do
        AddEdge(y, x)

    foreach static/special call cs in m do
        resolve target method m'
        AddCallEdge(cs, m')
```

`AddCallEdge(cs, m)` 负责真正加入调用边，并建立参数、返回值的 PFG 边：

```text
if cs -> m is new in CG then
    add cs -> m to CG
    AddReachable(m)

    AddEdge(receiver, m_this)

    foreach actual argument a_i and formal parameter p_i do
        AddEdge(a_i, p_i)

    AddEdge(m_ret, lhs)
```

如果是 static call，没有 receiver，也就不需要传递 `this`。  
如果是 instance call，receiver 必须流入被调用方法的 `this`。


## Virtual Call 的解析

看一个例子：

```java
class A { void foo() {} }
class B extends A { void foo() {} }

void main() {
    A x;
    if (...) {
        x = new A(); // o1
    } else {
        x = new B(); // o2
    }
    x.foo();
}
```

对于调用点 `x.foo()`，如果分析得到：

$$
pt(x) = \{o_1, o_2\}
$$

那么需要分别根据对象类型进行 dispatch：

$$
Dispatch(type(o_1), foo)
$$

$$
Dispatch(type(o_2), foo)
$$

如果 $o_1$ 的类型是 `A`，$o_2$ 的类型是 `B`，那么 call graph 中可能加入两条边：

```text
x.foo() -> A.foo
x.foo() -> B.foo
```

这比单纯的 CHA 更精确。  
CHA 主要看类层次结构，而基于指针分析的做法会进一步看 receiver 在当前程序中到底可能指向哪些 allocation site。


## 最终结果

过程间指针分析最后会得到两类结果：

- 每个 pointer 的 points-to set
- 分析过程中 on-the-fly 构建出来的 Call Graph

这两个结果会继续服务后面的分析。  
例如字段传播需要 points-to set，过程间数据流分析需要 Call Graph，虚调用解析也依赖 receiver 的 points-to 信息。
