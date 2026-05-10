# 作业3：死代码检测

死代码检测可以分为三个部分来解决， 分别是控制流不可达代码， 分支不可达代码， 无用赋值 三个部分， 我们依次实现对应的代码

## 控制流不可达代码

主要思路是使用 `DFS` 来判断 CFG 中哪些节点不可以到达，将不可达的代码加入到 dead 集里面

> 注意， 如果我们直接遍历 CFG (`for(Stmt stmt : cfg)`) , 那么返回的是所有节点的集合

实现的代码很简单：

```java
Set<Stmt> reachable = new HashSet<>();
Queue<Stmt> workList = new ArrayDeque<>();
workList.add(cfg.getEntry());
while (!workList.isEmpty()) {
    Stmt head = workList.poll();
    if (reachable.contains(head)) continue;
    reachable.add(head);
    Set<Edge<Stmt>> edges = cfg.getOutEdgesOf(head);
    for(Edge<Stmt> edge : edges) {
        // if(! canReach(edge, constants, cfg)) continue; //（Branch Unreachable）
        workList.add(edge.getTarget());
    }
}
for (Stmt stmt : ir.getStmts()) {
    if (!reachable.contains(stmt)) {
        deadCode.add(stmt);
    }
}
```

## 分支不可达代码

一开始可能会比较难想，但是实际上我们可以借用上面的 DFS 框架来判断分支是否可达，对于一条边，我们可以利用常量传播的信息来判断这条边是否可以使用，如果判断出不可以，那么就不走这条边， 我们通过 `canReach` 函数来判断某条边是否可达

`canReach` 分别处理 `IF` 和 `Switch` 分支， 对于 if 分支我们判断如果两个操作数都是常量，那么就可以判断条件是否满足了

对于 Switch 分支，和 if 分支处理差不多，唯一的坑点是 `default` 分支，如果没有case能满足条件，那么就只能走 default 分支，那么所有的 case 将都是死代码。

实现代码如下：
```java
private boolean canReach(Edge<Stmt> edge, DataflowResult<Stmt, CPFact> consRes, CFG<Stmt> cfg) {
    Stmt target = edge.getTarget(), source = edge.getSource();
    if(source instanceof If ifStmt) {
        ConditionExp conExp = ifStmt.getCondition();
        Var var1 = conExp.getOperand1(); Var var2 = conExp.getOperand2();
        CPFact in = consRes.getInFact(ifStmt);
        Value v1 = in.get(var1), v2 = in.get(var2);
        if(!(v1.isConstant() && v2.isConstant())) return true;
        boolean result = true;
        switch (conExp.getOperator()) {
            case EQ -> result = v1.getConstant() == v2.getConstant();
            case NE -> result = v1.getConstant() != v2.getConstant();
            case LT -> result = v1.getConstant() < v2.getConstant();
            case GT -> result = v1.getConstant() > v2.getConstant();
            case LE -> result = v1.getConstant() <= v2.getConstant();
            case GE -> result = v1.getConstant() >= v2.getConstant();
        }
        if(edge.getKind() == Edge.Kind.IF_TRUE && result) return true;
        if(edge.getKind() == Edge.Kind.IF_FALSE && ! result) return true;
        return false;
    }

    if(source instanceof SwitchStmt switchStmt) {
        Var var = switchStmt.getVar();
        CPFact in = consRes.getInFact(switchStmt);
        Value val = in.get(var);
        if(! val.isConstant()) return true;
        if(edge.isSwitchCase() && edge.getCaseValue() == val.getConstant())
            return true;
        if(edge.getKind() == Edge.Kind.SWITCH_DEFAULT) {
            boolean res = false;
            for(Edge<Stmt> outEdge : cfg.getOutEdgesOf(source)) {
                if(outEdge.isSwitchCase() && outEdge.getCaseValue() == val.getConstant())
                    res = true;
            }
            return !res;
        }
        return false;
    }
    return true;
}
```

## 无用赋值

这个完全照搬老师在指南中写的提示：

> 为了检测无用赋值，我们需要预先对被检测代码施用活跃变量分析。对于一个赋值语句，如果它等号左侧的变量（LHS 变量）是一个无用变量（换句话说，not live），那么我们可以把它标记为一个无用赋值。

注意，这里需要使用的是 `OUT` ， 因为我们使用的是反向传播

代码实现如下：

```java
for (Stmt stmt : cfg) {
    SetFact<Var> out = liveVars.getOutFact(stmt); // must be out fact
    if(! stmt.getDef().isPresent())
        continue;
    
    if (stmt.getDef().get() instanceof Var var) {
        if (out.contains(var)) continue;
        List<RValue> use = stmt.getUses();
        boolean safe = true;
        for (RValue rval : use) {
            safe &= DeadCodeDetection.hasNoSideEffect(rval);
        }
        if (!safe) continue;
        deadCode.add(stmt);
    }
}
```