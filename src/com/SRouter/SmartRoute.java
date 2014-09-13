package com.SRouter;

import java.util.LinkedList;

/**
 * Created by Ning Jiang on 9/5/14.
 * Wraps information of a route selected by Smart router
 */
public class SmartRoute {
    LinkedList<Vertex> path;

    public SmartRoute(LinkedList<Vertex> path)
    {
       this.path = path;
    }

    public LinkedList<Vertex> getPath() { return path; }
}
