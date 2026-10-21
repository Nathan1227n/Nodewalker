package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.stage.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalSnapshot.Phase.*;

/** Actual application/solvers, Fedora windows and camera transforms. API controls; no solver-result fixtures. */
public final class WeightsVisualReview {
    private static final CountDownLatch finished=new CountDownLatch(1);
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final List<String> rows=new ArrayList<>();
    private static NodeWalkerApp app;private static Stage stage,report;private static TspWorldView view;private static Path output;
    private static boolean desktop;private static int sizeIndex;private static final int[] sizes={5,8,12};
    private static Object matrix,result;private static PreparedWorld world;private static long run;
    public static void main(String[] args)throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);
        rows.add("capture\tmethod\tN\tseed\tinstance\trun\talgorithm\tstatus\tmode\tgraphEdges\trenderedEdges\tcandidates\tlabels\tterms\ttotal\twidth\theight");
        Platform.startup(()->Platform.runLater(()->guard(()->{
            app=new NodeWalkerApp();stage=new Stage();app.start(stage,Arrays.asList(args).contains("--2d-only")?List.of("--2d"):List.of(),Map.of());
            view=(TspWorldView)node("tsp-world");desktop=view.isThreeD();
            var details=(TitledPane)node("operational-details");details.setExpanded(true);
            var tabs=(TabPane)details.getContent();tabs.getSelectionModel().select(tabs.getTabs().stream().filter(t->t.getText().equals("Mapa")).findFirst().orElseThrow());
            after(WeightsVisualReview::generate);
        })));
        if(!finished.await(120,TimeUnit.SECONDS)){Platform.exit();throw new AssertionError("weights review timeout");}
        if(failure.get()!=null)throw new AssertionError("weights review failed",failure.get());
        System.out.println("WEIGHTS PASS: real N=5/8/12 TSP/Dijkstra, 3D camera-transform projection/2D Canvas, complete tables, filtered counts, collisions, whole-edge replay, mode/destination reuse and immediate invalidation.");
    }
    private static void generate(){
        int n=sizes[sizeIndex];combo("algorithm",ExecutionRequest.Algorithm.BACKTRACKING);
        when(()->!s().phase().busy(),()->{
            ((TextField)node("city-count")).setText(Integer.toString(n));((TextField)node("instance-seed")).setText(n==8?"657":"23");
            click("generate");when(()->s().phase()==INSTANCIA_PRONTA && view.isReady(),()->{
                view.finishPlayback();matrix=s().problem();world=s().scene().preparedWorld();view.topView();
                ((CheckBox)node("acknowledge-expensive")).setSelected(true);
                click("execute");when(()->!s().phase().busy() && s().result() instanceof ExecutionRecord.Tsp && view.isReady(),WeightsVisualReview::tsp);
            });
        });
    }
    private static void tsp(){
        var r=((ExecutionRecord.Tsp)s().result()).result();check(r.route().isPresent(),"real TSP incumbent exists");
        var b=CostBreakdown.ofScene(s().scene()).orElseThrow();check(b.terms().size()==s().n() && b.terms().getLast().to()==0,"all N TSP terms including return");
        check(b.total().orElseThrow().longValueExact()==r.cost().orElseThrow(),"TSP exact sum");
        view.finishPlayback();mode(EdgeWeightMode.SOLUTION);closeDetails();
        after(()->{auditFrame();capture(stage,"n"+s().n()+"-tsp-solution-"+(desktop?"3d":"2d"));
            click("present-results");report=reportStage();after(()->{
                @SuppressWarnings("unchecked") var table=(TableView<CostBreakdown.Term>)report.getScene().lookup("#cost-breakdown-table");
                check(table!=null && table.getItems().equals(b.terms()),"complete mathematical table, no filtering");
                var scroll=(ScrollPane)((TabPane)report.getScene().lookup("#results-tabs")).getTabs().getFirst().getContent();scroll.setVvalue(1);
                after(()->{click(report,"copy-report");check(Clipboard.getSystemClipboard().getString().contains(b.copyText()),"copied every mathematical term");
                    capture(report,"n"+s().n()+"-tsp-full-table-real");
                    if(sizeIndex==0)compareTsp();
                    else if(sizeIndex==2){table.scrollTo(b.terms().size()-1);after(()->{capture(report,"n12-tsp-table-return-edge");closeReportAndConnections();});}
                    else closeReportAndConnections();
                });
            });
        });
    }
    private static void closeReportAndConnections(){
        click(report,"close-results");view.setThreeD(false);mode(EdgeWeightMode.CONNECTIONS);after(()->{
            auditFrame();capture(stage,"n"+s().n()+"-tsp-connections-2d");if(sizeIndex==0)reviewDetails(0);else paths();
        });
    }
    private static void reviewDetails(int index){
        var details=(TitledPane)node("operational-details");details.setExpanded(true);
        var tabs=(TabPane)details.getContent();
        if(index==tabs.getTabs().size()){closeDetails();after(WeightsVisualReview::replay);return;}
        tabs.getSelectionModel().select(index);
        after(()->{
            var scroll=(ScrollPane)tabs.getTabs().get(index).getContent();
            var viewport=(javafx.scene.layout.Region)Objects.requireNonNull(scroll.lookup(".viewport"));
            check(scroll.getStyleClass().contains("details-scroll"),"details use scoped dark scroll style");
            var fills=viewport.getBackground().getFills();check(!fills.isEmpty(),"details viewport has explicit background");
            for(var fill:fills)check(fill.getFill() instanceof javafx.scene.paint.Color color && color.getBrightness()<.25,"no white viewport in any detail tab");
            if(index==2){scroll.setVvalue(1);after(()->{capture(stage,"n5-dark-map-details-compact-legend");reviewDetails(index+1);});}
            else reviewDetails(index+1);
        });
    }
    private static void compareTsp(){
        ((TabPane)report.getScene().lookup("#results-tabs")).getSelectionModel().select(1);
        after(()->{click(report,"compare-run-bruteforce");
            when(()->s().phase()==CONCLUIDO && s().request().algorithm()==ExecutionRequest.Algorithm.BRUTE_FORCE && view.isReady(),()->{
                view.finishPlayback();check(TspComparison.of(s().instanceId(),s().history()).verdict()==TspComparison.Verdict.AGREEMENT,"real TSP comparison unchanged");
                check(s().problem()==matrix,"comparison reuses exact matrix");after(()->{capture(report,"n5-both-tsp-algorithms-real");closeReportAndConnections();});
            });
        });
    }
    private static void replay(){
        run=s().runId();result=s().result();int history=s().history().size();view.setPlaybackSpeed(.25);view.replayPath();
        when(()->view.pathEdgeFraction(0)>0 && view.pathEdgeFraction(0)<.5,()->{
            view.pausePlayback();check(view.visiblePathEdges()==0,"partially drawn edge not accumulated");
            String caption=((Label)node("playback-caption")).getText();check(caption.contains("Reprodução do resultado calculado") && caption.contains("acumulado de 0/5 ligações: 0 /"),"explicit exact replay caption");
            view.setThreeD(desktop);after(()->{check(s().runId()==run && s().result()==result && s().history().size()==history,"pause and 2D/3D cannot rerun solver");
                capture(stage,"n5-replay-partial-zero-accumulation");view.finishPlayback();view.replayPath();view.finishPlayback();
                check(s().runId()==run,"replay does not compute");paths();
            });
        });
    }
    private static void paths(){
        combo("algorithm",ExecutionRequest.Algorithm.DIJKSTRA);
        when(()->s().phase()==INSTANCIA_PRONTA && view.isReady(),()->{view.finishPlayback();click("execute");
            when(()->s().phase()==CONCLUIDO && s().result() instanceof ExecutionRecord.Paths && view.isReady(),()->{
                var r=((ExecutionRecord.Paths)s().result()).result();run=s().runId();result=s().result();
                int target=java.util.stream.IntStream.range(0,s().n()).boxed().max(Comparator.comparingInt(i->r.pathTo(i).orElseThrow().size())).orElseThrow();
                combo("destination",target);when(()->s().scene() instanceof ShortestPathSceneModel p && p.destination()==target && view.isReady(),()->{
                    check(matrix==s().problem() && world==s().scene().preparedWorld(),"same matrix/world for both problems");
                    check(s().runId()==run && s().result()==result,"destination reuses solver");
                    view.finishPlayback();view.setThreeD(desktop);view.topView();mode(EdgeWeightMode.CONNECTIONS);
                    after(()->{
                        auditFrame();var b=CostBreakdown.ofScene(s().scene()).orElseThrow();check(b.total().equals(r.distanceTo(target)),"BigInteger distance equality");
                        check(b.terms().size()==r.pathTo(target).orElseThrow().size()-1,"no artificial return");
                        capture(stage,"n"+s().n()+"-dijkstra-connections-"+(desktop?"3d":"2d"));
                        if(sizeIndex==1){click("present-results");report=reportStage();after(()->{
                            @SuppressWarnings("unchecked") var table=(TableView<CostBreakdown.Term>)report.getScene().lookup("#cost-breakdown-table");
                            check(table.getItems().equals(b.terms()),"Dijkstra full path table");
                            ((ScrollPane)((TabPane)report.getScene().lookup("#results-tabs")).getTabs().getFirst().getContent()).setVvalue(1);
                            after(()->{capture(report,"n8-dijkstra-full-table-real");click(report,"close-results");path2d();});
                        });} else path2d();
                    });
                });
            });
        });
    }
    private static void path2d(){
        view.setThreeD(false);mode(EdgeWeightMode.SOLUTION);after(()->{
            auditFrame();capture(stage,"n"+s().n()+"-dijkstra-solution-2d");
            if(sizeIndex==2)density();else if(sizeIndex==1)completeTopology();else nextSize();
        });
    }
    private static void nextSize(){sizeIndex++;if(desktop)view.setThreeD(true);generate();}
    private static void completeTopology(){
        combo("topology",io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Kind.COMPLETE);
        check(frame().labels().isEmpty() && CostBreakdown.ofScene(s().scene()).isEmpty(),"topology invalidates weights and sum immediately");
        when(()->s().phase()==INSTANCIA_PRONTA && view.isReady(),()->{click("execute");
            when(()->s().phase()==CONCLUIDO && view.isReady(),()->{
                view.finishPlayback();view.setThreeD(desktop);mode(EdgeWeightMode.CONNECTIONS);after(()->{
                    auditFrame();check(frame().selection().graphEdges().orElseThrow()==28,"complete graph remains complete");
                    capture(stage,"n8-dijkstra-complete-connections-"+(desktop?"3d":"2d"));
                    combo("topology",io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Kind.SPARSE_CONNECTED);
                    when(()->s().phase()==INSTANCIA_PRONTA && view.isReady(),WeightsVisualReview::nextSize);
                });
            });
        });
    }
    private static void density(){
        stage.setWidth(960);stage.setHeight(680);view.setThreeD(desktop);mode(EdgeWeightMode.CONNECTIONS);view.select(3);
        after(()->{
            auditFrame();check(frame().labels().size()<frame().selection().candidates().size(),"density deliberately filters labels");capture(stage,"n12-density-overlap-filter");
            comboScope(EdgeWeightPolicy.Scope.INCIDENT_SELECTED);after(()->{
                auditFrame();for(var e:frame().selection().candidates())if(!e.solution())check(e.from()==3 || e.to()==3,"incident filter uses selected refuge");
                capture(stage,"n12-incident-selection-filter");click("execute");
                check(s().scene() instanceof PendingSceneModel,"rerun invalidates immediately");
                check(frame().labels().isEmpty() && frame().selection().candidates().isEmpty(),"Canvas clears old costs synchronously");
                check(CostBreakdown.ofScene(s().scene()).isEmpty(),"no old sums");
                check(!((Label)node("playback-caption")).getText().contains("acumulado"),"no old replay sum");
                capture(stage,"n12-rerun-no-old-costs");finish();
            });
        });
    }
    private static void auditFrame(){
        var frame=frame();check(frame.labels().size()<=48,"bounded labels");
        for(var candidate:frame.selection().candidates()){
            check(candidate.matrixCost()==s().problem().cost(candidate.from(),candidate.to()),"original matrix weight");
            if(s().scene() instanceof ShortestPathSceneModel path)check(path.graph().topology().hasEdge(candidate.from(),candidate.to()),"actual topology");
        }
        var canvas=(Canvas)view.lookup("#edge-weight-overlay");var sub=subScene(view);
        var legend=(LabelLayout.Rect)canvas.getProperties().get("legend-bounds");
        check(legend!=null && legend.width()<400 && legend.width()<canvas.getWidth()*.7,"legend fits a compact corner, not the full map");
        for(int i=0;i<frame.labels().size();i++) {
            var p=frame.labels().get(i);check(p.bounds().x()>=0 && p.bounds().y()>=0 && p.bounds().x()+p.bounds().width()<=canvas.getWidth() && p.bounds().y()+p.bounds().height()<=canvas.getHeight(),"full cost text fits");
            for(int j=0;j<i;j++)check(!p.bounds().overlaps(frame.labels().get(j).bounds(),5),"no overlapping costs");
            for(var refuge:view.lookupAll(".world-label"))if(refuge.isVisible()) {
                var bounds=canvas.sceneToLocal(refuge.localToScene(refuge.getBoundsInLocal()));
                check(!p.bounds().overlaps(new LabelLayout.Rect(bounds.getMinX(),bounds.getMinY(),bounds.getWidth(),bounds.getHeight()),0),"actual refuge labels have priority");
            }
            if(sub!=null)try{
                var at=p.edge().geometry().midpoint();var camera=(PerspectiveCamera)sub.getCamera();
                var local=camera.getLocalToParentTransform().inverseTransform(new Point3D(at.x(),at.y(),at.z()));
                double focal=sub.getHeight()/(2*Math.tan(Math.toRadians(camera.getFieldOfView()/2)));
                check(local.getZ()>camera.getNearClip(),"not behind actual camera");
                check(Math.abs(sub.getWidth()/2+local.getX()*focal/local.getZ()-p.anchor().x())<1e-6,"projection matches actual camera transform X");
                check(Math.abs(sub.getHeight()/2+local.getY()*focal/local.getZ()-p.anchor().y())<1e-6,"projection matches actual camera transform Y");
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        check(!frame.labels().isEmpty(),"real cost labels drawn");
    }
    private static void mode(EdgeWeightMode mode){mapCombo("edge-weight-mode",mode);}
    private static void comboScope(EdgeWeightPolicy.Scope scope){mapCombo("edge-weight-scope",scope);}
    @SuppressWarnings("unchecked") private static <T>void mapCombo(String id,T value){
        var tabs=(TabPane)((TitledPane)node("operational-details")).getContent();
        var map=tabs.getTabs().stream().filter(t->t.getText().equals("Mapa")).findFirst().orElseThrow();
        var content=((ScrollPane)map.getContent()).getContent();((ComboBox<T>)Objects.requireNonNull(content.lookup("#"+id),id)).setValue(value);
    }
    private static void closeDetails(){((TitledPane)node("operational-details")).setExpanded(false);}
    private static Stage reportStage(){return (Stage)Window.getWindows().stream().filter(w->w instanceof Stage st && st.getTitle().contains("Resultados e comparação")).findFirst().orElseThrow();}
    private static EdgeWeightLayout.Frame frame(){return (EdgeWeightLayout.Frame)Objects.requireNonNull(view.lookup("#edge-weight-overlay")).getUserData();}
    private static OperationalSnapshot s(){return app.controllerForReview().snapshot();}
    private static Node node(String id){return Objects.requireNonNull(stage.getScene().lookup("#"+id),id);}
    private static void click(String id){click(stage,id);}
    private static void click(Stage window,String id){var n=Objects.requireNonNull(window.getScene().lookup("#"+id),id);check(!n.isDisabled(),id+" disabled");((Button)n).fire();}
    @SuppressWarnings("unchecked")private static <T>void combo(String id,T value){((ComboBox<T>)node(id)).setValue(value);}
    private static void after(Runnable next){final int[] pulses={0};when(()->++pulses[0]>=4 && (s().problem()==null || view.isReady()),next);}
    private static void when(BooleanSupplier condition,Runnable next){new AnimationTimer(){final long start=System.nanoTime();public void handle(long now){try{
        if(condition.getAsBoolean()){stop();Platform.runLater(()->guard(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();if(report!=null){report.getScene().getRoot().applyCss();report.getScene().getRoot().layout();}next.run();}));}
        else if(now-start>TimeUnit.SECONDS.toNanos(30)){stop();throw new AssertionError("wait timeout: "+s().phase()+" "+s().diagnostic());}
    }catch(Throwable t){stop();fail(t);}}}.start();}
    private static SubScene subScene(Node n){if(n instanceof SubScene sub)return sub;if(n instanceof Parent p)for(var child:p.getChildrenUnmodifiable()){var found=subScene(child);if(found!=null)return found;}return null;}
    private static void capture(Stage window,String name){try{
        var shot=window.getScene().snapshot(null);int w=(int)shot.getWidth(),h=(int)shot.getHeight();var pixels=new int[w*h];shot.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,pixels,0,w);ImageIO.write(image,"png",output.resolve(name+".png").toFile());
        var f=frame();var b=CostBreakdown.ofScene(s().scene());rows.add(name+"\treal-solvers-api-controls\t"+s().n()+"\t"+s().seed()+"\t"+s().instanceId()+"\t"+s().runId()+"\t"+s().request().algorithm()+"\t"+s().phase()+"\t"+view.weightMode()+"\t"+f.selection().graphEdges()+"\t"+f.selection().renderedEdges()+"\t"+f.selection().candidates().size()+"\t"+f.labels().size()+"\t"+b.map(d->d.terms().size()).orElse(0)+"\t"+b.flatMap(CostBreakdown::total).map(Object::toString).orElse("não disponível")+"\t"+w+"\t"+h);
    }catch(Exception e){throw new IllegalStateException(e);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void guard(Runnable action){try{action.run();}catch(Throwable t){fail(t);}}
    private static void fail(Throwable t){failure.compareAndSet(null,t);t.printStackTrace();finish();}
    private static void finish(){try{Files.write(output.resolve("weights-manifest.tsv"),rows);}catch(Exception e){failure.compareAndSet(null,e);}finally{if(report!=null)report.close();if(app!=null)app.stop();if(stage!=null)stage.close();Platform.exit();finished.countDown();}}
}
