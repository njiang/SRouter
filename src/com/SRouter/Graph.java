package com.SRouter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ning Jiang on 9/8/14.
 */
public class Graph {
    private final List<Vertex> vertexes;
    private final List<Edge> edges;

    public Graph() {
        this.vertexes = new ArrayList<Vertex>();
        this.edges = new ArrayList<Edge>();
    }

    public Graph(List<Vertex> vertexes, List<Edge> edges) {
        this.vertexes = vertexes;
        this.edges = edges;
    }

    public void addVertex(Vertex v) {
        if (v != null && !vertexes.contains(v)) {
            vertexes.add(v);
        }
    }

    public void addEdge(Edge e) {
        if (e != null && !this.edges.contains(e)) {
            this.edges.add(e);
        }
    }

    public List<Vertex> getVertexes() {
        return vertexes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}
