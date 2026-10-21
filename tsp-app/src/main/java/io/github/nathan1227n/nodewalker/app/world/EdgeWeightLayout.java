package io.github.nathan1227n.nodewalker.app.world;

import java.util.*;

/** Screen-space weights. Refuge labels/controls are immutable obstacles with higher priority. No terrain depth test. */
public final class EdgeWeightLayout {
    public static final int MAX_LABELS=48;
    public record Request(EdgeWeightPolicy.WeightedEdge edge,Vec3 anchor,double width,double height,double projectedLength){}
    public record Placement(EdgeWeightPolicy.WeightedEdge edge,Vec3 anchor,LabelLayout.Rect bounds){}
    public record Frame(EdgeWeightPolicy.Selection selection,List<Placement> labels) {
        public Frame{labels=List.copyOf(labels);}
        public String summary(){return "Grafo: "+(selection.graphEdges().isPresent()?selection.graphEdges().getAsLong():"não disponível")
                +" · ligações na camada: "+selection.renderedEdges()+" · candidatas de peso: "+selection.candidates().size()+" · etiquetas desenhadas: "+labels.size();}
    }
    private EdgeWeightLayout(){}
    public static List<Placement> arrange(List<Request> requests,LabelLayout.Rect viewport,List<LabelLayout.Rect> obstacles,double nearClip,double farClip) {
        int budget=Math.min(MAX_LABELS,Math.max(1,(int)(viewport.width()*viewport.height()/15000)));
        var ordered=requests.stream().sorted(Comparator.comparingInt((Request r)->r.edge().solution()?0:1)
                .thenComparingInt(r->r.edge().solution()?r.edge().solutionOrder():r.edge().from()).thenComparingInt(r->r.edge().to())).toList();
        var placed=new ArrayList<Placement>();
        for(var request:ordered) {
            if(placed.size()>=budget) break;
            var at=request.anchor();
            if(!Double.isFinite(at.x()+at.y()+at.z()) || at.z()<=nearClip || at.z()>=farClip
                    || at.x()<viewport.x() || at.x()>viewport.x()+viewport.width() || at.y()<viewport.y() || at.y()>viewport.y()+viewport.height()
                    || request.width()>viewport.width() || request.height()>viewport.height()
                    || (!request.edge().solution() && request.projectedLength()<44)) continue;
            double w=request.width(),h=request.height();
            double[][] offsets={{-w/2,-h/2},{-w/2,-h-8},{-w/2,8},{8,-h/2},{-w-8,-h/2},{-w/2,-2*h-12},{-w/2,h+12},{18,-h-8},{-w-18,-h-8}};
            for(var offset:offsets) {
                var rect=new LabelLayout.Rect(at.x()+offset[0],at.y()+offset[1],w,h);
                if(!viewport.contains(rect) || obstacles.stream().anyMatch(o->o.overlaps(rect,5)) || placed.stream().anyMatch(p->p.bounds().overlaps(rect,5))) continue;
                placed.add(new Placement(request.edge(),at,rect));break;
            }
        }
        return List.copyOf(placed);
    }
}
