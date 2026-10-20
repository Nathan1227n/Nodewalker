package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real production controls and solvers. Opt-in desktop evidence; never invoked by headless Surefire. */
public final class ResultsVisualReview {
    private static final CountDownLatch finished = new CountDownLatch(1);
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final List<String> rows = new ArrayList<>();
    private static NodeWalkerApp app;
    private static Stage stage, report;
    private static TspWorldView view;
    private static Path output;
    private static Object matrix, world;
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]); Files.createDirectories(output);
        rows.add("capture\tmethod\tinstance\trun\talgorithm\tstatus\tcost\tpath");
        Platform.startup(()->Platform.runLater(()->guard(()->{
            app=new NodeWalkerApp(); stage=new Stage(); app.start(stage,Arrays.asList(args).contains("--2d-only") ? List.of("--2d") : List.of(),Map.of());
            view=(TspWorldView)stage.getScene().lookup("#tsp-world"); click(stage,"generate");
            when(()->s().phase()==OperationalSnapshot.Phase.INSTANCIA_PRONTA && view.isReady(),()->{
                view.finishPlayback(); matrix=s().problem(); world=s().scene().preparedWorld(); view.select(3); click(stage,"execute");
                done(()->{
                    capture(stage,"01-compact-result-real"); click(stage,"present-results");
                    report=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage st && st.getTitle().contains("Resultados e comparação")).findFirst().orElseThrow();
                    after(()->{
                        check(label("report-cost").equals(HudPresentation.of(s()).cost()),"real cost in report");
                        click(report,"copy-report"); check(Clipboard.getSystemClipboard().getString().contains("seed 23"),"copied context");
                        capture(report,"02-backtracking-presentation-real");
                        ((TabPane)node(report,"results-tabs")).getSelectionModel().select(1);
                        after(()->{check(label("comparison-verdict").contains("Força Bruta"),"missing counterpart identified");
                            capture(report,"03-comparison-before-bruteforce"); long run=s().runId(); click(report,"compare-run-bruteforce");
                            check(node(report,"compare-run-bruteforce").isDisabled(),"comparison blocks duplicate starts while configuring/executing");
                            check(node(report,"report-cancel").isVisible(),"comparison exposes cooperative cancellation while busy");
                            when(()->s().runId()>run && s().phase()==OperationalSnapshot.Phase.CONCLUIDO && view.isReady(),()->{
                                view.finishPlayback(); check(s().problem()==matrix,"comparison uses exact same matrix"); check(s().scene().preparedWorld()==world,"comparison shares prepared geometry"); check(view.selectedIndex()==3,"comparison preserves selected refuge");
                                check(TspComparison.of(s().instanceId(),s().history()).verdict()==TspComparison.Verdict.AGREEMENT,"real solvers agree");
                                after(()->{check(label("comparison-verdict").equals("Mesmo custo ótimo e mesmo ciclo"),"conclusion on screen");
                                    click(report,"copy-report"); var copied=Clipboard.getSystemClipboard().getString();
                                    check(copied.contains("Força Bruta") && copied.contains("Backtracking"),"comparison export");
                                    writeText("comparison-copy.txt",copied); capture(report,"04-comparison-two-real-solvers");
                                    report.setWidth(700); report.setHeight(500);
                                    after(()->{var close = node(report,"close-results"); var bounds = close.localToScene(close.getBoundsInLocal());
                                        check(bounds.getMinX()>=0 && bounds.getMaxX()<=report.getScene().getWidth(),"close control stays inside small window"); capture(report,"05-small-report-scrollable");
                                        click(report,"close-results"); check(!report.isShowing(),"back to map");
                                        combo("algorithm",ExecutionRequest.Algorithm.DIJKSTRA);
                                        when(()->s().phase()==OperationalSnapshot.Phase.INSTANCIA_PRONTA && view.isReady(),()->{
                                            view.finishPlayback(); click(stage,"execute"); done(ResultsVisualReview::paths);
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        })));
        if(!finished.await(60,TimeUnit.SECONDS)){Platform.exit();throw new AssertionError("Results review timeout");}
        if(failure.get()!=null)throw new AssertionError("Results graphical review failed",failure.get());
        System.out.println("RESULTS PASS: real result cards, same-instance TSP comparison, counterpart execution, copy, resize, Dijkstra destination reuse and new-instance invalidation.");
    }
    private static void paths(){
        var r=((ExecutionRecord.Paths)s().result()).result(); long run=s().runId();
        int target=java.util.stream.IntStream.range(0,s().n()).filter(i->r.pathTo(i).orElseThrow().size()>2).findFirst().orElseThrow();
        combo("destination",target);
        when(()->s().destination()==target && view.isReady(),()->{
            view.finishPlayback(); click(stage,"present-results"); report.setWidth(1000); report.setHeight(820);
            after(()->{check(label("report-cost").equals(r.distanceTo(target).orElseThrow().toString()),"confirmed distance");
                check(label("report-algorithm").equals("Dijkstra clássico"),"separate shortest path report");
                capture(report,"06-dijkstra-intermediate-path-real"); combo("destination",r.origin());
                when(()->s().destination()==r.origin() && view.isReady(),()->{
                    check(s().runId()==run && ((ExecutionRecord.Paths)s().result()).result()==r,"destination never reruns Dijkstra");
                    after(()->{check(label("report-cost").equals("0"),"live origin distance zero"); capture(report,"07-origin-destination-zero-real");
                        click(stage,"generate"); when(()->s().instanceId()==2 && s().phase()==OperationalSnapshot.Phase.INSTANCIA_PRONTA,()->{
                            after(()->{check(label("report-cost").equals("não informado"),"new instance removes old report");
                                ((TabPane)node(report,"results-tabs")).getSelectionModel().select(1);
                                check(node(report,"compare-bruteforce")!=null,"empty comparison available");
                                capture(report,"08-new-instance-no-old-comparison"); finish();
                            });
                        });
                    });
                });
            });
        });
    }
    private static OperationalSnapshot s(){return app.controllerForReview().snapshot();}
    private static void done(Runnable next){when(()->s().phase()==OperationalSnapshot.Phase.CONCLUIDO && s().result()!=null && view.isReady(),()->{view.finishPlayback();after(next);});}
    private static void after(Runnable next){final int[] pulses={0};when(()->++pulses[0]>=4,next);}
    private static void when(BooleanSupplier condition,Runnable next){new AnimationTimer(){final long start=System.nanoTime();public void handle(long now){
        try{if(condition.getAsBoolean()){stop();Platform.runLater(()->guard(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();if(report!=null){report.getScene().getRoot().applyCss();report.getScene().getRoot().layout();}next.run();}));}
            else if(now-start>TimeUnit.SECONDS.toNanos(20)){stop();throw new AssertionError("wait timed out: "+s().phase());}}
        catch(Throwable t){stop();failure.compareAndSet(null,t);t.printStackTrace();finish();}
    }}.start();}
    private static Node node(Stage window,String id){return Objects.requireNonNull(window.getScene().lookup("#"+id),id);}
    private static String label(String id){return ((Label)node(report,id)).getText();}
    private static void click(Stage window,String id){check(!node(window,id).isDisabled(),id+" disabled");((Button)node(window,id)).fire();}
    @SuppressWarnings("unchecked") private static <T> void combo(String id,T value){((ComboBox<T>)node(stage,id)).setValue(value);}
    private static void capture(Stage window,String name){try{
        var shot=window.getScene().snapshot(null);int w=(int)shot.getWidth(),h=(int)shot.getHeight();int[] pixels=new int[w*h];
        shot.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,pixels,0,w);ImageIO.write(image,"png",output.resolve(name+".png").toFile());
        var hud=HudPresentation.of(s()); rows.add(name+"\treal-algorithm-api-controls\t"+s().instanceId()+"\t"+s().runId()+"\t"+s().request().algorithm()+"\t"+s().phase()+"\t"+hud.cost()+"\t"+hud.route());
    }catch(Exception e){throw new IllegalStateException(e);}}
    private static void writeText(String name,String text){try{Files.writeString(output.resolve(name),text+"\n");}catch(Exception e){throw new IllegalStateException(e);}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void guard(Runnable next){try{next.run();}catch(Throwable t){failure.compareAndSet(null,t);t.printStackTrace();finish();}}
    private static void finish(){try{Files.write(output.resolve("results-manifest.tsv"),rows);}catch(Exception e){failure.compareAndSet(null,e);}finally{if(report!=null)report.close();if(app!=null)app.stop();if(stage!=null)stage.close();Platform.exit();finished.countDown();}}
}
