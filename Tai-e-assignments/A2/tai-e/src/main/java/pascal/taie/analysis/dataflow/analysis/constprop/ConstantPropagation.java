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

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.ArithmeticExp;
import pascal.taie.ir.exp.BinaryExp;
import pascal.taie.ir.exp.BitwiseExp;
import pascal.taie.ir.exp.ConditionExp;
import pascal.taie.ir.exp.Exp;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.ShiftExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

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

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        fact.forEach((var, val) -> {
            target.update(var, meetValue(val, target.get(var)));
        });
    }

    /**
     * Meets two Values.
     */
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

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
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
        if (value1.isNAC() || value2.isNAC()) {
            return Value.getNAC();
        }
        if (value1.isUndef() || value2.isUndef()) {
            return Value.getUndef();
        }

        int n1 = value1.getConstant();
        int n2 = value2.getConstant();
        if (exp instanceof ArithmeticExp arithmeticExp) {
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
}
