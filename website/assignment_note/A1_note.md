# 作业1：活跃变量分析和迭代求解器

## 理论回顾

如果变量 $v$ 在点 $p$ 是“活跃”的，意味着 $v$ 在 $p$ 处的值在路径的后续节点中可能会被读取，**且在此之前没有被重新赋值**。

活跃变量分析的传递函数和控制流约束分别为：

* 传递约束： $\text{OUT[S1] = } \cup_{ \text{p of all S1 successor}} \text{IN[p]}$ 
* 控制流约束：$\text{IN[S1] =   } \text{use}_{\text{S1}} \cup (\text{OUT[S1] } \setminus \text{def}_{\text{S1}})$



## 代码实现

其实个人认为这个作业的完成顺序应该是先完成 `backward` 的框架，这样当你运行测试的时候可以通过输出语句来看到自己的中间输出结果



### `Solver.initializeBackward`

观察到 `Solver` 类里面有一个抽线的分析器：`protected final DataflowAnalysis<Node, Fact> analysis;` 

所以我们在 `Solver` 类里面都取调用这个抽象分析器的方法，而不是直接 new 一个 `LiveVarableAnalysis` 

在绝大多数情况下，IN 和 OUT 的初始化是一样的，所以我们对 IN 和 OUT 同时进行初始化，同时对于普通的BB块和 Enter/Exit 初始情况不一样，所以我们调用不同的函数

> 伪代码中只对 IN 进行初始化，但代码中应该 IN， OUT 都初始化，不然会产生空指针异常

```java
protected void initializeBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
    // TODO - finish me
    for(Node node : cfg) {
        result.setInFact(node, analysis.newInitialFact());
        result.setOutFact(node, analysis.newInitialFact());
    }
    Node exit = cfg.getExit();
    result.setInFact(exit, analysis.newBoundaryFact(cfg));
    result.setOutFact(exit, analysis.newBoundaryFact(cfg));
}
```



### `IterativeSolver.doSolveBackward`

这里的实现和伪代码一致，如果 IN 没有发生变化，那么就终止循环

注意，我先统一更新完 OUT 之后在更新 IN，从算法逻辑来理解：IN的更新应该以最新版的OUT来决定

```java
@Override
protected void doSolveBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
    // TODO - finish me
    boolean changed = false;

    do {
        changed = false;
        for(Node node : cfg) {
            // OUT = union IN of successor
            Fact tem = analysis.newInitialFact();
            for(Node suc : cfg.getSuccsOf(node)) {
                analysis.meetInto(result.getInFact(suc), tem);
            }
            result.setOutFact(node, tem);
        }
        for(Node node : cfg) {
            // IN = use union (OUT \ def) [check IN if change]
            boolean trans = analysis.transferNode(node , result.getInFact(node), result.getOutFact(node));
            changed |= trans;
        }
    } while (changed);
}
```



### `LiveVariableAnalysis`

> 初始化

我们使用作者提供的 `SetFact` 来表示 IN 和 OUT, 活跃变量分析中，所有的初始化均为空集

```java
@Override
public SetFact<Var> newBoundaryFact(CFG<Stmt> cfg) {
    // TODO - finish me
    return new SetFact<>();
}

@Override
public SetFact<Var> newInitialFact() {
    // TODO - finish me
    return new SetFact<>();
}
```



> 传递函数

传递函数为是进行并集操作操作，活跃变量分析是对所有的后继进行并集操作，代码逻辑为两两进行合并、

```java
@Override
public void meetInto(SetFact<Var> fact, SetFact<Var> target) {
    // TODO - finish me
    target.union(fact);
}
```



> 控制流约束

我们分为两步，第一步 ： 生成集合： $\text{OUT[S1] } \setminus \text{def}_{\text{S1}}$ , 第二步： 生成集合：$\text{use}_{\text{S1}}$

注意：`stmt.getDef()` 返回的是 `Optional` 类型的数据， 其中返回的数据类型是 `LValue` , 所以要判断是否能将 `LValue` 是否能转 `Var` 类型， 同理： `getUses` 的返回值也需要进行确认

```java
@Override
public boolean transferNode(Stmt stmt, SetFact<Var> in, SetFact<Var> out) {
    // TODO - finish me
    SetFact<Var> temOut = out.copy();
    stmt.getDef().ifPresent(def -> {
        if (def instanceof Var var) {
            temOut.remove(var);
        }
    });
    SetFact<Var> used = new SetFact<>(
        stmt.getUses().stream()
            .filter(use -> use instanceof Var)
            .map(use -> (Var) use)
            .toList()
    );
    SetFact<Var> temIn = used.unionWith(temOut);
    if(temIn.equals(in)) return false;

    in.clear();
    in.union(temIn);
    return true;
}
```

