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

package pascal.taie.analysis.graph.callgraph;

import pascal.taie.World;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.Subsignature;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

/**
 * Implementation of the CHA algorithm.
 */
class CHABuilder implements CGBuilder<Invoke, JMethod> {

    private ClassHierarchy hierarchy;

    @Override
    public CallGraph<Invoke, JMethod> build() {
        hierarchy = World.get().getClassHierarchy();
        return buildCallGraph(World.get().getMainMethod());
    }

    private CallGraph<Invoke, JMethod> buildCallGraph(JMethod entry) {
        DefaultCallGraph callGraph = new DefaultCallGraph();
        callGraph.addEntryMethod(entry);
        // TODO - finish me
        Set<JMethod> reachMethod = new HashSet<>();
        Queue<JMethod> workList = new ArrayDeque<>();
        workList.add(entry);

        while(! workList.isEmpty()) {
            JMethod head = workList.poll();
            if(reachMethod.contains(head)) continue;
            reachMethod.add(head);
            for(C)
        }

        return callGraph;
    }

    /**
     * Resolves call targets (callees) of a call site via CHA.
     */
    private Set<JMethod> resolve(Invoke callSite) {
        // TODO - finish me
        Set<JMethod> res = new HashSet<>();

        switch (CallGraph.getCallKind(callSite)) {
            case CallKind.STATIC -> res.add(callSite.getContainer);
            case CallKind.SPECIAL -> {}
            case CallKind.VIRTUAL -> {}
            case CallKind.INTERFACE: -> {}

        }

        return null;
    }

    /**
     * Looks up the target method based on given class and method subsignature.
     *
     * @return the dispatched target method, or null if no satisfying method
     * can be found.
     */
    private JMethod dispatch(JClass jclass, Subsignature subsignature) {
        // TODO - finish me
        if(jclass == null) return null
        JMethod m = jclass.getDeclaredMethod(subsignature);
        if(m != null) return m;
        return dispatch(jclass.getSuperClass(), subsignature);
    }
}

/*
gradlew.bat test --tests pascal.taie.analysis.graph.callgraph.cha.CHATest.testStaticCall --info
gradlew.bat test --tests pascal.taie.analysis.graph.callgraph.cha.CHATest.testVirtualCall --info
gradlew.bat test --tests pascal.taie.analysis.graph.callgraph.cha.CHATest.testInterface --info
gradlew.bat test --tests pascal.taie.analysis.graph.callgraph.cha.CHATest.testAbstractMethod --info
*/
