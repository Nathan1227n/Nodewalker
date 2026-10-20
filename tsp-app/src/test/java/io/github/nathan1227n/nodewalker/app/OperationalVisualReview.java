package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalSnapshot.Phase.*;

/** Opt-in, real Fedora window, production controls and real solvers. No fixtures, no human-input claim. */
public final class OperationalVisualReview {
    private static final CountDownLatch finished = new CountDownLatch(1);
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final List<String> rows = new ArrayList<>();
    private static NodeWalkerApp app;
    private static Stage stage;
    private static TspWorldView view;
    private static Path output;
    private static Object matrix;
    private static OptionalLong bruteCost;
    private static boolean forced2d;
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]); Files.createDirectories(output); forced2d=Arrays.asList(args).contains("--2d-only") || Arrays.asList(args).contains("--fallback-only");
        rows.add("capture\tmethod\tinstance\trun\tN\tseed\talgorithm\tstatus\torigin\tdestination\ttopology\tcost\tpath");
        Platform.startup(()->guard(()->{
            app=new NodeWalkerApp(); stage=new Stage(); app.start(stage,Arrays.asList(args).contains("--2d-only") ? List.of("--2d") : List.of(),Map.of());
            view=(TspWorldView)stage.getScene().lookup("#tsp-world");
            check(s().phase()==SEM_INSTANCIA,"initial phase");
            text("city-count","3"); text("instance-seed","23"); click("generate");
            ready(()->{
                matrix=s().problem(); combo("algorithm",ExecutionRequest.Algorithm.BRUTE_FORCE);
                ready(()->{ click("execute"); done(()->{
                    bruteCost=((ExecutionRecord.Tsp)s().result()).result().cost(); capture("01-n3-bruteforce-real");
                    combo("algorithm",ExecutionRequest.Algorithm.BACKTRACKING);
                    ready(()->{click("execute");done(()->{
                        check(matrix==s().problem(),"same matrix across TSP algorithms"); check(bruteCost.equals(((ExecutionRecord.Tsp)s().result()).result().cost()),"same optimum");
                        capture("02-n3-backtracking-real"); preset(DemoCatalog.OUTPOSTS,()->{click("execute");done(()->{
                            capture("03-n5-backtracking-real"); preset(DemoCatalog.VALLEY,OperationalVisualReview::sparse);
                        });});
                    });});
                });});
            });
        }));
        if(!finished.await(90,TimeUnit.SECONDS)) { Platform.exit(); throw new AssertionError("Review timeout"); }
        if(failure.get()!=null) throw new AssertionError("Operational graphical review failed",failure.get());
        System.out.println("OPERATIONAL GRAPHICAL PASS: real generation/algorithms/cancellation; API-driven controls, not manual input.");
    }
    private static OperationalSnapshot s(){return app.controllerForReview().snapshot();}
    private static void sparse(){
        combo("algorithm",ExecutionRequest.Algorithm.DIJKSTRA);
        ready(()->{click("execute");done(()->{
            var r=((ExecutionRecord.Paths)s().result()).result(); var id=s().runId(); var world=s().scene().preparedWorld(); var sub=findSubScene(view);
            int target=java.util.stream.IntStream.range(0,s().n()).filter(i->r.pathTo(i).orElseThrow().size()>2).findFirst().orElseThrow();
            combo("destination",target);
            when(()->s().scene() instanceof ShortestPathSceneModel p && p.destination()==target,()->{
                check(id==s().runId() && r==((ExecutionRecord.Paths)s().result()).result(),"destination must reuse solver result");
                check(world==s().scene().preparedWorld(),"destination keeps geometry"); check(sub==findSubScene(view),"destination keeps SubScene");
                capture("04-n8-dijkstra-sparse-intermediate-real");
                view.setThreeD(false); after(()->{capture("05-n8-dijkstra-sparse-2d-real"); if(!forced2d)view.setThreeD(true);
                    combo("topology",RefugeTopology.Kind.COMPLETE); ready(()->{click("execute");done(()->{
                        capture("06-n8-dijkstra-complete-real"); combo("origin",2); ready(()->{click("execute");done(()->{
                            check(((ExecutionRecord.Paths)s().result()).result().origin()==2,"new origin");
                            capture("07-n8-dijkstra-origin2-real"); preset(DemoCatalog.FRONTIER,OperationalVisualReview::cancelRealSearch);
                        });});
                    });});
                });
            });
        });});
    }
    private static void cancelRealSearch(){
        combo("algorithm",ExecutionRequest.Algorithm.BRUTE_FORCE);
        text("node-limit",Long.toString(Long.MAX_VALUE)); text("time-limit","60000");
        ((CheckBox)node("acknowledge-expensive")).setSelected(true);
        ready(()->{
            click("execute"); when(()->s().phase()==EXECUTANDO_ALGORITMO,()->after(()->{
                click("cancel"); when(()->s().phase()==CANCELADO && s().result()!=null && !view.isAnimating(),()->{
                    var r=((ExecutionRecord.Tsp)s().result()).result(); check(r.route().isPresent(),"real cancellation preserves incumbent"); check(!r.optimalityGuaranteed(),"no optimality on cancellation");
                    capture("08-n12-cancelled-incumbent-real");
                    stage.setWidth(960); stage.setHeight(680); view.setDecoration(false); view.setSimplified(true); view.select(3); view.focusSelection();
                    after(()->{capture("09-n12-small-window-simplified-real");
                        click("execute"); when(()->s().phase()==EXECUTANDO_ALGORITMO,()->{
                            long start=System.nanoTime(); app.stop(); stage.close();
                            check(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1),"close must not wait for solver"); finish();
                        });
                    });
                });
            }));
        });
    }
    private static void preset(DemoCatalog demo,Runnable next){combo("demo-catalog",demo);ready(next);}
    private static void ready(Runnable next){when(()->s().phase()==INSTANCIA_PRONTA && s().scene()!=null && view.isReady() && !view.isAnimating(),next);}
    private static void done(Runnable next){when(()->s().phase()==CONCLUIDO && s().result()!=null && view.isReady() && !view.isAnimating(),next);}
    private static void after(Runnable next){final int[] pulses={0};when(()->++pulses[0]>=5 && view.isReady() && !view.isAnimating(),next);}
    private static void when(BooleanSupplier condition,Runnable next){
        new AnimationTimer(){final long start=System.nanoTime();public void handle(long now){
            try{if(condition.getAsBoolean()){stop();afterLayout(next);}else if(System.nanoTime()-start>TimeUnit.SECONDS.toNanos(30)){stop();throw new AssertionError("Wait timed out: "+s());}}
            catch(Throwable t){stop();fail(t);}
        }}.start();
    }
    private static void afterLayout(Runnable next){Platform.runLater(()->guard(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();next.run();}));}
    private static Node node(String id){return Objects.requireNonNull(stage.getScene().lookup("#"+id),id);}
    private static void click(String id){check(!node(id).isDisabled(),id+" disabled");((Button)node(id)).fire();}
    private static void text(String id,String text){((TextField)node(id)).setText(text);}
    @SuppressWarnings("unchecked") private static <T> void combo(String id,T value){((ComboBox<T>)node(id)).setValue(value);}
    private static SubScene findSubScene(Node n){if(n instanceof SubScene sub)return sub;if(n instanceof Parent p)for(var child:p.getChildrenUnmodifiable()){var found=findSubScene(child);if(found!=null)return found;}return null;}
    private static void capture(String name){
        try{
            var shot=stage.getScene().snapshot(null);int width=(int)shot.getWidth(),height=(int)shot.getHeight();int[] pixels=new int[width*height];
            shot.getPixelReader().getPixels(0,0,width,height,PixelFormat.getIntArgbInstance(),pixels,0,width);
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,width,height,pixels,0,width);ImageIO.write(image,"png",output.resolve(name+".png").toFile());
            var snapshot=s();var hud=HudPresentation.of(snapshot);
            rows.add(name+"\treal-algorithm-api-controls\t"+snapshot.instanceId()+"\t"+snapshot.runId()+"\t"+snapshot.n()+"\t"+snapshot.seed()+"\t"+snapshot.request().algorithm()+"\t"+snapshot.phase()+"\t"+snapshot.request().origin()+"\t"+snapshot.destination()+"\t"+(snapshot.request().algorithm().tsp()?"não se aplica":snapshot.request().topology())+"\t"+hud.cost()+"\t"+hud.route());
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void guard(Runnable r){try{r.run();}catch(Throwable t){fail(t);}}
    private static void fail(Throwable t){failure.compareAndSet(null,t);t.printStackTrace();finish();}
    private static void finish(){try{Files.write(output.resolve("operational-manifest.tsv"),rows);}catch(Exception e){failure.compareAndSet(null,e);}finally{if(app!=null)app.stop();if(stage!=null)stage.close();Platform.exit();finished.countDown();}}
}
