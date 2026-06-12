package com.intentio.engine.schema;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.util.List;
import java.util.Objects;

public final class RelationGraph {

    public record Edge(String from, String to, String relationName, RelationDef.Kind kind, String fk) {
        @Override
        public int hashCode() {
            return Objects.hash(from, to, relationName);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Edge e
                    && Objects.equals(from, e.from)
                    && Objects.equals(to, e.to)
                    && Objects.equals(relationName, e.relationName);
        }
    }

    private final Graph<String, Edge> graph;

    private RelationGraph(Graph<String, Edge> graph) {
        this.graph = graph;
    }

    public static RelationGraph build(SchemaRegistry registry) {
        Graph<String, Edge> g = new DefaultDirectedGraph<>(Edge.class);
        for (EntityDef entity : registry.entities()) {
            g.addVertex(entity.name());
        }
        for (EntityDef entity : registry.entities()) {
            for (RelationDef rel : entity.relations().values()) {
                if (!g.containsVertex(rel.targetEntity())) {
                    throw new IllegalStateException(
                        "Relation " + entity.name() + "." + rel.name()
                            + " references unknown entity " + rel.targetEntity());
                }
                g.addEdge(entity.name(), rel.targetEntity(),
                    new Edge(entity.name(), rel.targetEntity(), rel.name(), rel.kind(), rel.fk()));
            }
        }
        return new RelationGraph(g);
    }

    public Graph<String, Edge> raw() { return graph; }

    public List<Edge> path(String fromEntity, String relationPath) {
        String[] segments = relationPath.split("\\.");
        java.util.List<Edge> result = new java.util.ArrayList<>();
        String cursor = fromEntity;
        for (String segment : segments) {
            Edge match = null;
            for (Edge e : graph.outgoingEdgesOf(cursor)) {
                if (e.relationName().equals(segment)) {
                    match = e;
                    break;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException(
                    "No relation '" + segment + "' from entity " + cursor);
            }
            result.add(match);
            cursor = match.to();
        }
        return result;
    }
}
