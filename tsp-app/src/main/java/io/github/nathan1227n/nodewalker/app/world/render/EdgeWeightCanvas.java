package io.github.nathan1227n.nodewalker.app.world.render;

import io.github.nathan1227n.nodewalker.app.world.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import java.util.*;
import java.util.function.Function;

/** One Canvas per renderer, bounded labels, cached projection/layout. Overlay visibility is not terrain occlusion. */
final class EdgeWeightCanvas extends Canvas {
    private final Font font=Font.font("Monospaced",13);
    private final Text ruler=new Text();
    private final Map<String,Double> widths=new HashMap<>();
    private WorldSceneModel measuredModel;
    private Key last;
    private record Key(long revision,double width,double height,Object projection,List<LabelLayout.Rect> obstacles,int cities,int revealed){}
    EdgeWeightCanvas(){setId("edge-weight-overlay");setMouseTransparent(true);ruler.setFont(font);}
    void update(WorldViewState state,Function<Vec3,Vec3> projection,Object projectionKey,double width,double height,
                List<LabelLayout.Rect> obstacles,double near,double far,boolean threeD) {
        if(width<=1 || height<=1)return;
        if(getWidth()!=width)setWidth(width);if(getHeight()!=height)setHeight(height);
        int cities=state.playback().completedCities();
        if(cities<state.model().problem().size() && state.cityFraction(cities)>0)cities++;
        int revealed=state.playback().completedEdges();
        if(revealed<state.model().segments().size() && state.edgeFraction(revealed)>=.5)revealed++;
        var key=new Key(state.weightRevision(),width,height,projectionKey,List.copyOf(obstacles),cities,revealed);
        if(key.equals(last))return;last=key;
        if(measuredModel!=state.model()){measuredModel=state.model();widths.clear();}
        var g=getGraphicsContext2D();g.clearRect(0,0,width,height);
        if(state.weightMode()==EdgeWeightMode.NONE){getProperties().remove("legend-bounds");setUserData(new EdgeWeightLayout.Frame(state.weightSelection(),List.of()));return;}
        var requests=new ArrayList<EdgeWeightLayout.Request>();
        for(var edge:state.weightSelection().candidates()) {
            if(state.cityFraction(edge.from())==0 || state.cityFraction(edge.to())==0 || (edge.solution() && edge.solutionOrder()>=revealed))continue;
            var at=projection.apply(edge.geometry().midpoint());
            var a=projection.apply(edge.geometry().start());var b=projection.apply(edge.geometry().end());
            double span=a.z()>near && b.z()>near?Math.hypot(a.x()-b.x(),a.y()-b.y()):0;
            String text=Long.toString(edge.matrixCost());
            double measured=widths.computeIfAbsent(text,value->{ruler.setText(value);return ruler.getLayoutBounds().getWidth()+14;});
            requests.add(new EdgeWeightLayout.Request(edge,at,measured,24,span));
        }
        var viewport=new LabelLayout.Rect(12,134,Math.max(0,width-24),Math.max(0,height-300));
        var placements=EdgeWeightLayout.arrange(requests,viewport,obstacles,near,far);
        var frame=new EdgeWeightLayout.Frame(state.weightSelection(),placements);setUserData(frame);
        g.setFont(font);g.setLineWidth(1);
        for(var placed:placements) {
            var r=placed.bounds();boolean solution=placed.edge().solution();
            var color=Color.web(solution?(state.model().solidPath()?"#b5fff1":"#ffd18b"):"#b8c9c7");
            g.setStroke(color.deriveColor(0,1,1,.65));g.strokeLine(placed.anchor().x(),placed.anchor().y(),r.x()+r.width()/2,r.y()+r.height()/2);
            g.setFill(Color.web("#07141b",.96));g.fillRoundRect(r.x(),r.y(),r.width(),r.height(),5,5);
            g.setStroke(color);g.strokeRoundRect(r.x(),r.y(),r.width(),r.height(),5,5);
            g.setFill(color);g.fillText(Long.toString(placed.edge().matrixCost()),r.x()+7,r.y()+17);
        }
        var selection=frame.selection();
        String meaning=state.model().solidPath()?(state.model() instanceof ShortestPathSceneModel?"Ciano: caminho":"Ciano: ótimo"):"Âmbar: incumbente";
        var lines=List.of(state.weightMode()+" · filtro visual",
                "Grafo "+(selection.graphEdges().isPresent()?selection.graphEdges().getAsLong():"aguardando")
                        +" · camada "+selection.renderedEdges()+" · candidatas "+selection.candidates().size()+" · etiquetas "+placements.size(),
                meaning+" · cinza: conexões",
                threeD?"Sem teste de oclusão pelo terreno.":"Soma completa em Apresentar resultado.");
        var legendFont=Font.font("Monospaced",10);ruler.setFont(legendFont);
        double legendWidth=0;
        for(var line:lines){ruler.setText(line);legendWidth=Math.max(legendWidth,ruler.getLayoutBounds().getWidth()+16);}
        ruler.setFont(font);
        var legend=new LabelLayout.Rect(12,60,Math.min(Math.max(0,width-24),legendWidth),74);
        getProperties().put("legend-bounds",legend);
        g.setFill(Color.web("#07141b",.9));g.fillRoundRect(legend.x(),legend.y(),legend.width(),legend.height(),4,4);
        g.setFont(legendFont);
        for(int i=0;i<lines.size();i++){
            g.setFill(Color.web(i==3?"#94aaa9":"#bedbd6"));
            g.fillText(lines.get(i),20,76+i*16,Math.max(1,legend.width()-16));
        }
    }
}
