# 上下文敏感指针分析

前面的指针分析默认是 context-insensitive：同一个方法无论从哪里被调用，都只维护一份 points-to 结果。

上下文敏感（Context-Sensitive）指针分析的核心想法是：

> 同一个方法在不同调用上下文中，应该拥有不同的变量、对象和 points-to 结果。

它通常能显著提高精度，但代价是分析状态数量变多。

## 1. 为什么需要上下文敏感

看一个经典例子：

```java
Object id(Object p) {
    return p;
}

void main() {
    Object a = new A(); // o1
    Object b = new B(); // o2

    Object x = id(a);
    Object y = id(b);
}
```

如果做 context-insensitive 分析，`id` 只有一份参数 `p` 和返回值：

- `a` 和 `b` 都流入 `id.p`
- `id.ret` 同时包含 $o_1$ 和 $o_2$
- 最后得到 $pt(x)=pt(y)=\{o_1,o_2\}$

但真实语义上，第一次调用返回 `a`，第二次调用返回 `b`。如果区分上下文，就可以得到更精确的结果：

- 第一次调用上下文：$pt(x)=\{o_1\}$
- 第二次调用上下文：$pt(y)=\{o_2\}$

## 2. 上下文是什么

上下文可以理解成“方法调用发生在什么环境下”的摘要。常见表示方式有：

- call-site sensitivity：用调用点序列作为上下文
- object sensitivity：用 receiver object 序列作为上下文
- type sensitivity：用 receiver type 序列作为上下文

上下文通常不会无限长，而是使用长度为 $k$ 的截断序列，也就是常说的 `k-CFA`、`k-object-sensitive` 等。

例如 `2-call-site-sensitive` 中，上下文可以写成 `[l1, l2]`，表示当前方法是沿着最近两个调用点 `l1 -> l2` 进入的。

## 3. 上下文敏感的基本建模

context-insensitive 中，一个变量只有一份 `x`。context-sensitive 中，一个变量要带上上下文，写成 `(c, x)`，其中 `c` 是 context。类似地，方法也可以变成上下文化方法 `(c, m)`。

points-to 函数也随之变成 `pt(c, x)`，意思是：在上下文 `c` 中，变量 `x` 可能指向哪些对象。

## 4. 上下文化对象

除了变量和方法，堆对象也可以上下文化。

如果只使用 allocation-site abstraction，那么循环或不同调用上下文里创建的对象可能被合并。例如：

```java
Object make() {
    return new Object(); // o
}

Object x = make();
Object y = make();
```

context-insensitive heap 只会产生一个抽象对象 `o`，于是 `x` 和 `y` 可能混在一起。

context-sensitive heap 可以把对象表示成 `(hctx, o)`，其中 `hctx` 是创建该对象时的 heap context。这样同一个 allocation site 在不同调用上下文下可以产生不同抽象对象。

## 5. Context Selector 与 Heap Context Selector

课程里常把上下文选择逻辑抽象成两个函数：`Select(callerCtx, callSite, receiverObject, callee)` 决定调用进入目标方法时使用什么上下文；`SelectHeap(methodCtx, allocationSite)` 决定新建对象使用什么 heap context。

不同上下文敏感策略的区别，主要就在这两个函数如何定义。

## 6. Call-Site Sensitivity

call-site sensitivity 用调用点作为上下文。

例如：

```java
l1: x = id(a);
l2: y = id(b);
```

在 `1-call-site-sensitive` 中：

- `l1` 调用 `id` 时，`id` 的上下文是 `[l1]`
- `l2` 调用 `id` 时，`id` 的上下文是 `[l2]`

于是会有两份参数和返回值：`([l1], id.p)`、`([l1], id.ret)`、`([l2], id.p)`、`([l2], id.ret)`。

这能解决很多普通函数调用造成的混淆。

## 7. Object Sensitivity

object sensitivity 用 receiver object 作为上下文，特别适合面向对象语言。

```java
class Box {
    Object id(Object p) { return p; }
}

void main() {
    Box b1 = new Box(); // o1
    Box b2 = new Box(); // o2

    Object x = b1.id(new A()); // o3
    Object y = b2.id(new B()); // o4
}
```

在 `1-object-sensitive` 中：

- `b1.id` 的上下文由 receiver object $o_1$ 决定
- `b2.id` 的上下文由 receiver object $o_2$ 决定

因此 `Box.id` 会被分析两份，分别对应两个 receiver 对象。对 Java 这类大量通过对象分派行为的语言，object sensitivity 往往比 call-site sensitivity 更有效。

## 8. Type Sensitivity

type sensitivity 用 receiver object 的类型作为上下文。

如果 receiver object 是 $o_i$，其类型是 `A`，那么上下文可以使用 `A` 而不是 $o_i$。

它通常比 object sensitivity 更粗，但状态数量也更少。适合在精度和性能之间折中。

## 9. 上下文敏感规则的变化

在 context-insensitive 中，赋值规则可以写成：

```text
x = y
-----
y -> x
```

在 context-sensitive 中，边需要带上下文，即 `(c, y) -> (c, x)`。

字段读写也类似，只是对象可能也有 heap context：

```java
x.f = y;
z = x.f;
```

如果 $(h,o)\in pt(c,x)$，那么加入 `(c, y) -> (h,o).f` 和 `(h,o).f -> (c, z)`。

也就是说，PFG 的节点从普通 pointer 变成上下文化 pointer。

## 10. 上下文敏感调用处理

对实例调用：

```java
l: r = x.k(a1, ..., an)
```

如果在调用者上下文 `c` 中发现 $(h,o)\in pt(c,x)$，则：

1. 用 `Dispatch(o, k)` 找到目标方法 `m`
2. 用 `Select(c, l, (h,o), m)` 生成被调方法上下文 `c'`
3. 加入上下文化调用边 `(c,l) -> (c',m)`
4. 建立 PFG 边：

```text
(c, x)       -> (c', m_this)
(c, a1)      -> (c', m_p1)
...
(c', m_ret)  -> (c, r)
```

这和 context-insensitive 的调用规则很像，只是每个变量和方法都带上了上下文。

## 11. 精度与代价

上下文敏感能减少不同调用之间的污染，但会带来更多分析状态：

- 方法从 `m` 变成多个 `(c,m)`
- 变量从 `x` 变成多个 `(c,x)`
- 对象可能从 `o` 变成多个 `(h,o)`
- PFG、Call Graph、points-to set 都会变大

因此实际分析器通常会设置 `k` 的长度，或对部分库代码使用更粗的策略。

## 12. 总结

上下文敏感指针分析可以概括为：

1. 用上下文区分同一个方法的不同调用
2. 用上下文化变量维护更精确的 points-to set
3. 可选地用 heap context 区分不同上下文下创建的对象
4. 通过 context selector 决定调用进入哪个上下文
5. 在精度提升和状态膨胀之间做权衡

## 参考资料

- [南京大学软件分析课程主页](https://tai-e.pascal-lab.net/lectures.html)
- [NJU Software Analysis: Context Sensitivity](https://cs.nju.edu.cn/tiantan/software-analysis/PTA-CS.pdf)
- [Java 指针分析综述](https://cs.nju.edu.cn/yueli/papers/crad2023.pdf)
