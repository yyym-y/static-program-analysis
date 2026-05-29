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
import java.util.Collection;
import java.util.HashSet;
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
        Queue<JMethod> workList = new ArrayDeque<>();
        workList.add(entry);
        while(! workList.isEmpty()) {
            JMethod head = workList.poll();
            if(callGraph.contains(head)) continue;
            callGraph.addReachableMethod(head);
            Set<Invoke> callSites = callGraph.getCallSitesIn(head);
            for(Invoke c : callSites) {
                Set<JMethod> to_method = resolve(c);

                for (JMethod method : to_method) {
                    callGraph.addEdge(determindCallSiteEdge(c, method));
                    workList.add(method);
                }
            }
        }

        return callGraph;
    }

    private Edge<Invoke, JMethod> determindCallSiteEdge(Invoke callSite, JMethod method) {
        System.out.println(callSite.toString() + "  ---->  " + method);
        switch (CallGraphs.getCallKind(callSite)) {
            case STATIC -> {
                return new Edge<>(CallKind.STATIC, callSite, method);
            }
            case SPECIAL -> {
                return new Edge<>(CallKind.SPECIAL, callSite, method);
            }
            case VIRTUAL -> {
                return new Edge<>(CallKind.VIRTUAL, callSite, method);
            }
            case INTERFACE -> {
                return new Edge<>(CallKind.INTERFACE, callSite, method);
            }
        }
        return null;
    }

    /**
     * Resolves call targets (callees) of a call site via CHA.
     */
    private Set<JMethod> resolve(Invoke callSite) {
        // TODO - finish me
        Set<JMethod> res = new HashSet<>();

        // System.out.println(callSite.toString() + " -Kind- " + CallGraphs.getCallKind(callSite));
        // System.out.println(callSite.getMethodRef().getDeclaringClass());

        switch (CallGraphs.getCallKind(callSite)) {
            case STATIC : {}
            case SPECIAL : { 
                JMethod m = dispatch(callSite.getMethodRef().getDeclaringClass(),
                    callSite.getMethodRef().getSubsignature());
                if(m != null) res.add(m);
                break;
            }
            case VIRTUAL : {}
            case INTERFACE : {
                Set<JClass> subClasses = new HashSet<>();
                subClasses.add(callSite.getMethodRef().getDeclaringClass());

                Queue<JClass> workList = new ArrayDeque<>();
                workList.add(callSite.getMethodRef().getDeclaringClass());
                while(! workList.isEmpty()) {
                    JClass head = workList.poll();
                    if(head.isInterface()) {
                        hierarchy.getDirectImplementorsOf(head).forEach(obj -> {
                            subClasses.add(obj);
                            workList.add(obj);
                        });
                        hierarchy.getDirectSubinterfacesOf(head).forEach(obj -> {
                            subClasses.add(obj);
                            workList.add(obj);
                        });
                    } else {
                        hierarchy.getDirectSubclassesOf(head).forEach(obj -> {
                            subClasses.add(obj)
                            workList.add(obj);
                        });
                    }
                }
                for(JClass cls : subClasses) {
                    JMethod m = dispatch(cls, callSite.getMethodRef().getSubsignature());
                    if(m == null || m.isAbstract()) continue;
                    res.add(m);
                }
            }

        }

        return res;
    }

    /**
     * Looks up the target method based on given class and method subsignature.
     *
     * @return the dispatched target method, or null if no satisfying method
     * can be found.
     */
    private JMethod dispatch(JClass jclass, Subsignature subsignature) {
        // TODO - finish me
        if(jclass == null) return null;
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
