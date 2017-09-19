package io.jaegertracing.spark.dependencies.common;

import io.jaegertracing.spark.dependencies.common.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.common.tree.Node;
import io.jaegertracing.spark.dependencies.common.tree.Traversals;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Pavol Loffay
 */
public class DependenciesDerivator {

    public static Map<String, Map<String, Long>> serviceDependencies(Node root) {
        return serviceDependencies(root, new LinkedHashMap<>());
    }

    public static Map<String, Map<String, Long>> serviceDependencies(Node root,
                                                                     Map<String, Map<String, Long>> dependenciesMap) {
        Traversals.inorder(root, (child, parent) -> {
            if (parent == null) {
                return;
            }
            Map<String, Long> childMap = dependenciesMap.get(parent.getServiceName());
            if (childMap == null) {
                childMap = new LinkedHashMap<>();
                dependenciesMap.put(parent.getServiceName(), childMap);
            }

            Long callCount = childMap.get(child.getServiceName());
            if (callCount == null) {
                callCount = 0L;
            }
            childMap.put(child.getServiceName(), ++callCount);
        });
        return dependenciesMap;
    }

    public static Map<String, Map<String, Long>> serviceDependencies(List<DependencyLink> dependencyLinks) {
        Map<String, Map<String, Long>> parentDependencyMap = new LinkedHashMap<>();
        dependencyLinks.forEach(dependencyLink -> {
            Map<String, Long> childCallCountMap = parentDependencyMap.get(dependencyLink.getParent());
            if (childCallCountMap == null) {
                childCallCountMap = new LinkedHashMap<>();
                parentDependencyMap.put(dependencyLink.getParent(), childCallCountMap);
            }
            childCallCountMap.put(dependencyLink.getChild(), dependencyLink.getCallCount());
        });
        return parentDependencyMap;
    }
}
