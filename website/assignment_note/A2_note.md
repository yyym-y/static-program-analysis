# 作业2：常量传播和 Worklist 求解器


与第一次作业一致，我们先实现 `Solver` 和 `WorkList` 算法，这样方便之后可以输出常量传播部分的代码

## `Solver.initializeForward`

这一部分比较简单，和之前初始化反向传播的代码差不多，这里不做讲解

```java
protected void initializeForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
    // TODO - finish me
    for(Node node : cfg) {
        result.setInFact(node, analysis.newInitialFact());
        result.setOutFact(node, analysis.newInitialFact());
    }
    Node entry = cfg.getEntry();
    result.setInFact(entry, analysis.newBoundaryFact(cfg));
    result.setOutFact(entry, analysis.newBoundaryFact(cfg));
}
```

## `WorkListSolver.doSolveForward`

这一部分和伪代码类似，我们使用 Java 的队列来实现，不过值得注意的是，我们要对队列中的节点进行判断，如果他是初始节点，我们不应该让他进行传播

> 因为在前向数据流分析里，`entry` 的 `IN` 不是由前驱节点算出来的，而是一个边界条件
>
> 如果不特判 `entry`，就会用它的前驱重新计算 `IN[entry]`。但 entry 通常没有前驱，于是 `IN[entry]` 会变成 `newInitialFact()`，覆盖掉原本正确的
>
> 因为我在循环中默认 `IN`初始化语句为 `Fact in = analysis.newInitialFact();` , 如果你不想单独包裹在 `if` 中，也可以将初始化语句写为 `Fact in = cfg.isEntry(head) ? analysis.newBoundaryFact(cfg) : analysis.newInitialFact();`
> 

```java
@Override
protected void doSolveForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
    Queue<Node> queue = new LinkedList<>();
    for (Node node : cfg) {
        queue.add(node);
    }

    while (!queue.isEmpty()) {
        Node head = queue.remove();
        if (!cfg.isEntry(head)) {
            Fact in = analysis.newInitialFact();
            for (Node pred : cfg.getPredsOf(head)) {
                analysis.meetInto(result.getOutFact(pred), in);
            }
            result.setInFact(head, in);
        }

        // Fact in = cfg.isEntry(head) ? analysis.newBoundaryFact(cfg) : analysis.newInitialFact();
        // for (Node pred : cfg.getPredsOf(head)) {
        //     analysis.meetInto(result.getOutFact(pred), in);
        // }
        // result.setInFact(head, in);

        if (analysis.transferNode(head, result.getInFact(head), result.getOutFact(head))) {
            for (Node succ : cfg.getSuccsOf(head)) {
                queue.add(succ);
            }
        }
    }
}
```

## `ConstantPropagation `

### `newBoundaryFact`

首先我们要先明白 IR， Stmt 之间的关系， 一个 Stmt 代表一个语句， 而 IR 是一个函数的静态分析中间部分，包含一个函数内部的抽象信息（变量，参数等等）， 一个 IR 里面又许多的 Stmt， 但一个 Stmt 只可能隶属一个 IR

对于常量分析，他是一个 must analysis。 所有我们的 entry 应该采用保守估计，因为不是过程间分析，所以传入函数的参数我们默认为 NAC， 而其他变量是 UNDFINE， 同时因为 CPFact 默认变量就是 UND， 所以我们不用单独设置

获取传入参数的代码为 `cfg.getIR().getParams()` , 所以边界情况的代码就很简单了

```java
@Override
public CPFact newBoundaryFact(CFG<Stmt> cfg) {
    // TODO - finish me
    CPFact boundary = new CPFact();
    for (Var param : cfg.getIR().getParams()) {
        if (canHoldInt(param)) {
            boundary.update(param, Value.getNAC());
        }
    }
    return boundary;
}
```

### `newInitialFact`

这一部分很简单，不做解释

```java
@Override
public CPFact newInitialFact() {
    // TODO - finish me
    return new CPFact();
}
```

### `meetInto`

回顾常量传播的合并规则：
- $v \sqcap \text{UNDEF} = v$
- $c \sqcap c = c$
- 若 $c_1 \neq c_2$，则 $c_1 \sqcap c_2 = \text{NAC}$
- $\text{NAC} \sqcap v = \text{NAC}$

所以代码只需要实现这个规则就可以了：

```java
@Override
public void meetInto(CPFact fact, CPFact target) {
    // TODO - finish me
    fact.forEach((var, val) -> {
        target.update(var, meetValue(val, target.get(var)));
    });
}
public Value meetValue(Value v1, Value v2) {
    // TODO - finish me
    // NAC + ANY
    if(v1.isNAC() || v2.isNAC())
        return Value.getNAC();
    // CON + UND
    if(v1.isUndef() || v2.isUndef()) {
        if(v1.isConstant()) return v1;
        return v2;
    }
    // CON + CON
    int val1 = v1.getConstant();
    int val2 = v2.getConstant();
    if(val1 == val2)
        return v1;
    return Value.getNAC();
}
```

### `transferNode`

常量传播的传递函数的逻辑为： $\text{OUT[S1]} = \text{def}_{\text{S1}} \cup (\text{OUT[S1]} \setminus \text{kill}_{\text{S1}})$

其中 $\text{kill}_{\text{S1}}$ 为包含被重新赋值的变量的集合 `<Var， ？>`， $\text{def}_{\text{S1}}$ 是被重新被赋值的对象以及常量值 `<Var, Con>`

所以我们要先判断这个 Stmt 是不是赋值语句，如果是的话在依次判断语句的类型，实际上到后面就是 `switch-case` 的集合

注意，有一个坑：
> X 是 NAC 但是同时触发除零，也返回 Undef
>
> 

```java
@Override
public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
    // TODO - finish me
    CPFact oldOut = out.copy();

    out.clear();
    out.copyFrom(in);

    if (stmt instanceof DefinitionStmt<?, ?> defStmt
            && defStmt.getLValue() instanceof Var lhs
            && canHoldInt(lhs)) {
        out.update(lhs, evaluate(defStmt.getRValue(), in));
    }

    return !out.equals(oldOut);
}

public static Value evaluate(Exp exp, CPFact in) {
    // TODO - finish me
    if (exp instanceof IntLiteral literal) {
        return Value.makeConstant(literal.getValue());
    }
    if (exp instanceof Var var) {
        return in.get(var);
    }
    if (!(exp instanceof BinaryExp binaryExp)) {
        return Value.getNAC();
    }

    Value value1 = in.get(binaryExp.getOperand1());
    Value value2 = in.get(binaryExp.getOperand2());
    ArithmeticExp arithmeticExp = exp instanceof ArithmeticExp exp1 ? exp1 : null;
    if (arithmeticExp != null
            && value2.isConstant()
            && value2.getConstant() == 0
            && (arithmeticExp.getOperator() == ArithmeticExp.Op.DIV
            || arithmeticExp.getOperator() == ArithmeticExp.Op.REM)) {
        return Value.getUndef();
    }
    if (value1.isNAC() || value2.isNAC()) {
        return Value.getNAC();
    }
    if (value1.isUndef() || value2.isUndef()) {
        return Value.getUndef();
    }

    int n1 = value1.getConstant();
    int n2 = value2.getConstant();
    if (arithmeticExp != null) {
        return switch (arithmeticExp.getOperator()) {
            case ADD -> Value.makeConstant(n1 + n2);
            case SUB -> Value.makeConstant(n1 - n2);
            case MUL -> Value.makeConstant(n1 * n2);
            case DIV -> n2 == 0 ? Value.getUndef() : Value.makeConstant(n1 / n2);
            case REM -> n2 == 0 ? Value.getUndef() : Value.makeConstant(n1 % n2);
        };
    }
    if (exp instanceof ConditionExp conditionExp) {
        return switch (conditionExp.getOperator()) {
            case EQ -> Value.makeConstant(n1 == n2 ? 1 : 0);
            case NE -> Value.makeConstant(n1 != n2 ? 1 : 0);
            case LT -> Value.makeConstant(n1 < n2 ? 1 : 0);
            case GT -> Value.makeConstant(n1 > n2 ? 1 : 0);
            case LE -> Value.makeConstant(n1 <= n2 ? 1 : 0);
            case GE -> Value.makeConstant(n1 >= n2 ? 1 : 0);
        };
    }
    if (exp instanceof ShiftExp shiftExp) {
        return switch (shiftExp.getOperator()) {
            case SHL -> Value.makeConstant(n1 << n2);
            case SHR -> Value.makeConstant(n1 >> n2);
            case USHR -> Value.makeConstant(n1 >>> n2);
        };
    }
    if (exp instanceof BitwiseExp bitwiseExp) {
        return switch (bitwiseExp.getOperator()) {
            case OR -> Value.makeConstant(n1 | n2);
            case AND -> Value.makeConstant(n1 & n2);
            case XOR -> Value.makeConstant(n1 ^ n2);
        };
    }
    return Value.getNAC();
}
```