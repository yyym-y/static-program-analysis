# 指针分析简介

指针分析（Pointer Analysis / Points-to Analysis）回答的问题是：

> 一个指针变量或对象字段，在运行时可能指向哪些对象？

在 Java 这样的面向对象语言里，“指针”通常就是引用变量、静态字段、实例字段、数组元素等引用位置。

```java
void main() {
    A x = new A(); // o1
    A y = x;
    A z = new A(); // o2
}
```

如果用 allocation site 表示抽象对象，那么结果可以写成：

$$
pt(x)=\{o_1\},\quad pt(y)=\{o_1\},\quad pt(z)=\{o_2\}
$$

其中 $pt(p)$ 是 pointer $p$ 的 points-to set，也就是 $p$ 可能指向的抽象对象集合。

## 1. 为什么需要指针分析

过程间分析里，调用图的精度经常受虚调用影响。以：

```java
abstract class Num {
    abstract int get();
}

class Zero extends Num {
    int get() { return 0; }
}

class One extends Num {
    int get() { return 1; }
}

class Two extends Num {
    int get() { return 2; }
}

void main() {
    Num n = new One();
    int x = n.get();
}
```

为例，CHA 只根据类层次结构解析 `n.get()`，可能把 `Zero.get`、`One.get`、`Two.get` 都当作目标；指针分析知道 `n` 来自 `new One()`，就可以把调用目标缩小到 `One.get`。

因此指针分析常用于：

- 构建更精确的 Call Graph
- 推导 alias 信息
- 支持编译优化，例如虚调用内联
- 支持缺陷检测，例如空指针、资源泄露
- 支持安全分析，例如污点分析的信息流传播

## 2. May Analysis 与过近似

指针分析通常是 may analysis。若分析结果中有 $o \in pt(x)$，意思是：`x` 可能指向 $o$，不代表每次运行一定指向 $o$。

为了 sound，静态分析宁可多算，也不能漏算：

- 多算：出现 false positive，精度下降
- 漏算：可能错过真实执行行为，soundness 被破坏

所以 points-to set 往往是一个 over-approximation。

从格的角度看，如果抽象对象集合为 $O=\{o_1,o_2,\dots,o_n\}$，那么每个 pointer 的值都来自幂集 $pt(p)\in \mathcal{P}(O)$。

分析过程就是不断把新的对象加入集合，直到所有 points-to set 都不再变化。

## 3. 堆抽象

运行时对象可能无限多，例如循环或递归中反复执行 `new`：

```java
while (...) {
    x = new A();
}
```

静态分析必须终止，因此需要把无限的 concrete objects 抽象成有限的 abstract objects。

课程里最常用的是 allocation-site abstraction：

- 每个 `new` 语句位置对应一个抽象对象
- 同一条 `new` 即使运行很多次，也只对应同一个抽象对象

例如：

```java
1: x = new A(); // o1
2: y = new A(); // o2
```

第 1 行和第 2 行是两个 allocation sites，所以得到两个抽象对象 $o_1$ 和 $o_2$。

## 4. 指针分析关心的语句

课程先把程序简化成 pointer-affecting statements：

```java
x = new T();  // New
x = y;        // Assign
x.f = y;      // Store
y = x.f;      // Load
r = x.k(a);   // Call
```

前四类语句描述对象在变量和字段之间如何传播；`Call` 语句在过程间指针分析中处理。

对应的直觉是：

- `new`：产生新的抽象对象
- `assign`：`y` 指向的对象流入 `x`
- `store`：`y` 指向的对象写入 `x` 指向对象的字段
- `load`：从 `x` 指向对象的字段读出对象
- `call`：receiver、实参、返回值在调用者和被调方法之间传播

## 5. 字段为什么写成 $o_i.f$

实例字段不直接记成 `x.f`，而是记成抽象对象字段 $o_i.f$。

```java
1: x = new T(); // o1
2: y = x;
3: x.f = z;
4: w = y.f;
```

因为 $pt(x)=\{o_1\},\ pt(y)=\{o_1\}$，所以第 3 行写入的是 $o_1.f$，第 4 行读取的也是 $o_1.f$。这样 `x` 和 `y` 的别名关系就通过同一个抽象对象连接起来。

简言之：

- `x.f` 依赖变量名，变量后续可能指向别的对象
- $o_i.f$ 依赖抽象对象，字段信息更稳定

## 6. 常见精度维度

指针分析是一个精度和效率之间的权衡系统，常见维度包括：

### Context Sensitivity

是否区分同一个方法在不同调用上下文里的结果。

```java
Object id(Object p) { return p; }

Object x = id(new A()); // o1
Object y = id(new B()); // o2
```

context-insensitive 会把两次调用的参数和返回值混在一起；context-sensitive 可以把两次调用分开，通常更精确但更慢。

### Flow Sensitivity

是否区分语句执行顺序。

```java
x = new A(); // o1
x = new B(); // o2
```

flow-sensitive 在第二行之后可得到 $pt(x)=\{o_2\}$；flow-insensitive 把程序看成无序语句集合，通常得到 $pt(x)=\{o_1,o_2\}$。

### Field Sensitivity

是否区分不同字段。

```java
x.f = a;
x.g = b;
```

field-sensitive 分别维护 $o_i.f$ 和 $o_i.g$；field-insensitive 可能把一个对象的字段合并，速度更快但精度更低。

### Analysis Scope

分析整个程序，还是按需求只分析与某个查询相关的部分。课程基础算法主要采用 whole-program 的思路。

## 7. 本章默认设定

后续规则与算法默认采用：

- context-insensitive
- flow-insensitive
- field-sensitive
- allocation-site heap abstraction

在这个设定下，目标是：

> 对每个变量和每个抽象字段 $o_i.f$，求出它们的 points-to set。

下一节会把语句翻译成包含约束，并用 Pointer Flow Graph 和 worklist 算法求解。

