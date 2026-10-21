package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import java.util.*;

/** Bounded graphic selection only. Never removes edges from a mathematical graph. */
public final class EdgeWeightPolicy {
    public static final int MAX_SECONDARY_EDGES=256;
    public enum Scope {
        AVAILABLE("Disponíveis (filtradas)"), INCIDENT_SELECTED("Incidentes à seleção");
        private final String label; Scope(String label){this.label=label;} @Override public String toString(){return label;}
    }
    public record WeightedEdge(int from,int to,long matrixCost,SegmentGeometry geometry,int solutionOrder) implements WorldSceneModel.Edge {
        public boolean solution(){return solutionOrder>=0;}
    }
    public record Selection(OptionalLong graphEdges,int renderedEdges,List<WeightedEdge> candidates) {
        public Selection {candidates=List.copyOf(candidates);}
    }
    private EdgeWeightPolicy(){}
    private static long key(int a,int b){return ((long)Math.min(a,b)<<32)|Math.max(a,b);}
    /** Called on selection/model/scope changes, not on playback frames. */
    public static List<? extends WorldSceneModel.Edge> secondaryConnections(WorldSceneModel model,int selected,Scope scope) {
        if(model instanceof PendingSceneModel) return List.of();
        if(!(model instanceof ShortestPathSceneModel paths)) return model.secondaryConnections(selected);
        var graph=paths.graph(); var edges=new LinkedHashMap<Long,WeightedEdge>(); var routeKeys=new HashSet<Long>();
        for(var edge:paths.segments()) routeKeys.add(key(edge.from(),edge.to()));
        if(scope==Scope.INCIDENT_SELECTED || graph.topology().kind()==RefugeTopology.Kind.COMPLETE) {
            int[] anchors=scope==Scope.INCIDENT_SELECTED?new int[]{selected}:new int[]{selected,paths.origin(),paths.destination()};
            for(int from:anchors) for(int j=0;j<graph.topology().degree(from) && edges.size()<MAX_SECONDARY_EDGES;j++) {
                int to=graph.topology().neighborAt(from,j); long key=key(from,to);
                if(!routeKeys.contains(key)) edges.putIfAbsent(key,edge(model,from,to,-1));
            }
        } else for(var edge:paths.secondaryConnections(selected)) edges.put(key(edge.from(),edge.to()),edge(model,edge.from(),edge.to(),-1));
        return List.copyOf(edges.values());
    }
    private static WeightedEdge edge(WorldSceneModel model,int from,int to,int order) {
        return new WeightedEdge(from,to,model.problem().cost(from,to),new SegmentGeometry(model.positions().get(from),model.positions().get(to)),order);
    }
    public static Selection select(WorldSceneModel model,EdgeWeightMode mode,boolean connections,List<? extends WorldSceneModel.Edge> secondary) {
        if(model==null || model instanceof PendingSceneModel) return new Selection(OptionalLong.empty(),0,List.of());
        long total=model instanceof ShortestPathSceneModel paths?paths.graph().topology().edgeCount():((long)model.problem().size()*(model.problem().size()-1))/2;
        var labels=new ArrayList<WeightedEdge>();
        if(mode!=EdgeWeightMode.NONE) {
            int order=0;for(var edge:model.segments()) labels.add(edge(model,edge.from(),edge.to(),order++));
            if(mode==EdgeWeightMode.CONNECTIONS && connections) for(var edge:secondary) labels.add(edge(model,edge.from(),edge.to(),-1));
        }
        return new Selection(OptionalLong.of(total),model.segments().size()+(connections?secondary.size():0),labels);
    }
}
