/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.cfg.Edge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.ArithmeticExp;
import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.CastExp;
import pascal.taie.ir.exp.ConditionExp;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.NewExp;
import pascal.taie.ir.exp.RValue;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.If;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.SwitchStmt;

import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

public class DeadCodeDetection extends MethodAnalysis {

    public static final String ID = "deadcode";

    public DeadCodeDetection(AnalysisConfig config) {
        super(config);
    }


    @Override
    public Set<Stmt> analyze(IR ir) {
        // obtain CFG
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        // obtain result of constant propagation
        DataflowResult<Stmt, CPFact> constants =
                ir.getResult(ConstantPropagation.ID);
        // obtain result of live variable analysis
        DataflowResult<Stmt, SetFact<Var>> liveVars =
                ir.getResult(LiveVariableAnalysis.ID);
        // keep statements (dead code) sorted in the resulting set
        Set<Stmt> deadCode = new TreeSet<>(Comparator.comparing(Stmt::getIndex));

        // CFG unreachable And Branch Unreachable
        Set<Stmt> reachable = new HashSet<>();
        Queue<Stmt> workList = new ArrayDeque<>();
        workList.add(cfg.getEntry());
        while (!workList.isEmpty()) {
            Stmt head = workList.poll();
            if (reachable.contains(head)) continue;
            reachable.add(head);
            Set<Edge<Stmt>> edges = cfg.getOutEdgesOf(head);
            for(Edge<Stmt> edge : edges) {
                if(! canReach(edge, constants, cfg)) continue;
                workList.add(edge.getTarget());
            }
        }
        for (Stmt stmt : ir.getStmts()) {
            if (!reachable.contains(stmt)) {
                deadCode.add(stmt);
            }
        }

        // deadAssign
        for (Stmt stmt : cfg) {
            SetFact<Var> out = liveVars.getOutFact(stmt); // must be out fact
            if(! stmt.getDef().isPresent())
                continue;
            
            if (stmt.getDef().get() instanceof Var var) {
                // System.out.println(var + "------------------------");
                if (out.contains(var)) continue;
                // System.out.println(var + "************************");
                List<RValue> use = stmt.getUses();
                boolean safe = true;
                for (RValue rval : use) {
                    safe &= DeadCodeDetection.hasNoSideEffect(rval);
                }
                if (!safe) continue;
                deadCode.add(stmt);
            }
        }

        return deadCode;
    }

/*
.\gradlew.bat test --tests pascal.taie.analysis.dataflow.analysis.DeadCodeTest.testControlFlowUnreachable --info
.\gradlew.bat test --tests pascal.taie.analysis.dataflow.analysis.DeadCodeTest.testUnreachableIfBranch --info
.\gradlew.bat test --tests pascal.taie.analysis.dataflow.analysis.DeadCodeTest.testUnreachableSwitchBranch --info
.\gradlew.bat test --tests pascal.taie.analysis.dataflow.analysis.DeadCodeTest.testDeadAssignment --info
.\gradlew.bat test --tests pascal.taie.analysis.dataflow.analysis.DeadCodeTest.testLoops --info

*/


    private boolean canReach(Edge<Stmt> edge, DataflowResult<Stmt, CPFact> consRes, CFG<Stmt> cfg) {
        Stmt target = edge.getTarget(), source = edge.getSource();
        if(source instanceof If ifStmt) {
            // System.out.println(source + "------------------------");
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
            // System.out.println(result + "####");
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

    /**
     * @return true if given RValue has no side effect, otherwise false.
     */
    private static boolean hasNoSideEffect(RValue rvalue) {
        // new expression modifies the heap
        if (rvalue instanceof NewExp ||
                // cast may trigger ClassCastException
                rvalue instanceof CastExp ||
                // static field access may trigger class initialization
                // instance field access may trigger NPE
                rvalue instanceof FieldAccess ||
                // array access may trigger NPE
                rvalue instanceof ArrayAccess) {
            return false;
        }
        if (rvalue instanceof ArithmeticExp) {
            ArithmeticExp.Op op = ((ArithmeticExp) rvalue).getOperator();
            // may trigger DivideByZeroException
            return op != ArithmeticExp.Op.DIV && op != ArithmeticExp.Op.REM;
        }
        return true;
    }
}
