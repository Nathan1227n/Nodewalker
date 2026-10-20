package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
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
import static io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest.Algorithm.*;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalSnapshot.Phase.*;

/** Real solvers/renderers with manually gated background workers and UI callbacks. No sleeps or fixtures. */
public final class TransitionsVisualReview {
    private static final CountDownLatch finished=new CountDownLatch(1);
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final List<String> rows=new ArrayList<>();
    private static final class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>(); volatile boolean stopped;
        public void execute(Runnable job){if(stopped)throw new RejectedExecutionException();tasks.add(job);}
        boolean step(){var job=tasks.poll();if(job==null)return false;job.run();return true;}
        public void shutdown(){stopped=true;} public List<Runnable> shutdownNow(){stopped=true;var pending=List.copyOf(tasks);tasks.clear();return pending;}
        public boolean isShutdown(){return stopped;} public boolean isTerminated(){return stopped&&tasks.isEmpty();}
        public boolean awaitTermination(long time,TimeUnit unit){return isTerminated();}
    }
    private static final ManualExecutor compute=new ManualExecutor(), visual=new ManualExecutor();
    private static final Queue<Runnable> ui=new ConcurrentLinkedQueue<>();
    private static final ExecutorService driver=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"review-background-driver");t.setDaemon(true);return t;});
    private static NodeWalkerApp app; private static Stage stage; private static TspWorldView view; private static Path output;
    private static PreparedWorld world; private static SubScene subScene; private static Camera camera; private static List<Double> cameraTransform;
    private static Node terrain; private static Object matrix; private static int iteration;

    public static void main(String[] args)throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);
        rows.add("capture\tmethod\tinstance\trun\talgorithm\tphase\tsnapshotSegments\tactual3DRouteChildren\tselected\tworldPreserved");
        Platform.startup(()->Platform.runLater(()->guard(()->{
            app=new NodeWalkerApp();stage=new Stage();app.start(stage,Arrays.asList(args).contains("--2d-only")?List.of("--2d"):List.of(),Map.of(),
                    listener->new OperationalController(compute,visual,ui::add,new OperationalServices(){},listener));
            view=(TspWorldView)node("tsp-world"); click("generate"); pump(()->{
                view.finishPlayback();matrix=s().problem();world=s().scene().preparedWorld();view.select(3);view.focusSelection();
                subScene=findSubScene(view);camera=subScene==null?null:subScene.getCamera();
                cameraTransform=camera==null?null:transform(camera); terrain=subScene==null?node("map-terrain-backdrop"):terrain(subScene.getRoot());
                click("execute"); pump(()->{view.finishPlayback();capture("00-backtracking-real-before-switch");switchToDijkstra();});
            });
        })));
        if(!finished.await(90,TimeUnit.SECONDS)){Platform.exit();throw new AssertionError("transition review timed out");}
        if(failure.get()!=null)throw new AssertionError("transition review failed",failure.get());
        System.out.println("TRANSITIONS PASS: actual graph/canvas cleared immediately, worker/UI barriers, rerun/switch/destination revisions, preparation cancellation and late solver delivery; same world/camera/selection in 3D/2D.");
    }
    private static void switchToDijkstra(){
        combo("algorithm",DIJKSTRA);empty("01-backtracking-to-dijkstra-workers-held");
        step(compute,()->{drainUi();empty("02-topology-done-presentation-held");
            step(visual,()->{ // presentation is ready but its UI callback is held
                click("cancel");check(s().phase()==CANCELADO,"preparation cancel terminal");drainUi();
                check(s().phase()==CANCELADO && s().result()==null,"queued ready callback must not undo preparation cancel");
                empty("03-cancel-preparation-ui-callback-held");click("execute"); // retry prepares topology first, then runs
                pump(()->{view.finishPlayback();check(s().result() instanceof ExecutionRecord.Paths,"retry really ran Dijkstra");
                    capture("04-dijkstra-real-after-retry");switchToBruteForce();});
            });
        });
    }
    private static void switchToBruteForce(){
        combo("algorithm",BRUTE_FORCE);empty("05-dijkstra-to-bruteforce-presentation-held");
        step(visual,()->{ // stale TSP configuration callback queued, superseded before UI delivery
            app.controllerForReview().configure(request(DIJKSTRA));empty("06-new-config-old-presentation-queued");drainUi();
            empty("07-old-presentation-callback-rejected");
            pump(()->{
                combo("algorithm",BRUTE_FORCE);pump(()->{click("execute");pump(()->{view.finishPlayback();capture("08-bruteforce-real-before-rerun");rerun();});});
            });
        });
    }
    private static void rerun(){
        click("execute");empty("09-rerun-workers-held");
        step(compute,()->{ // solver genuinely completed, but its callbacks are still held
            click("cancel");check(s().phase()==CANCELAMENTO_SOLICITADO,"solver cancel waits for actual response");
            drainUi();check(s().result()!=null && s().result().status().equals("CONCLUIDO"),"late cancellation preserves completed response");
            empty("10-completed-solver-presentation-held");pump(()->{view.finishPlayback();check(s().phase()==CONCLUIDO,"completed result owns final status");
                capture("11-completed-result-after-late-cancel"); destination();});
        });
    }
    private static void destination(){
        combo("algorithm",DIJKSTRA);pump(()->{click("execute");pump(()->{
            view.finishPlayback();long run=s().runId();var result=s().result();combo("destination",2);empty("12-new-destination-presentation-held");
            pump(()->{check(s().runId()==run && s().result()==result,"destination reuses complete search");view.finishPlayback();
                capture("13-new-destination-real-result");pendingFxInstallation();});
        });});
    }
    private static void pendingFxInstallation(){
        click("execute");
        step(compute,()->{drainUi();step(visual,()->{drainUi();step(visual,()->{
            drainUi();check(!s().scene().segments().isEmpty(),"real path queued for renderer installation");
            if(subScene!=null)check(!view.isReady(),"old route installation is actually still queued");
            app.controllerForReview().configure(request(BRUTE_FORCE));empty("14-old-fx-route-installation-held");
            when(view::isReady,()->{empty("15-old-fx-route-installation-rejected");finish();});
        });});});
    }
    private static ExecutionRequest request(ExecutionRequest.Algorithm algorithm){var r=s().request();return new ExecutionRequest(algorithm,r.origin(),r.topology(),r.topologySeed(),r.extraConnections(),r.tspOptions(),r.pathOptions());}
    private static void empty(String name){
        check(s().scene() instanceof PendingSceneModel,"pending snapshot required: "+name);check(s().scene().segments().isEmpty(),"snapshot contains no old edges");
        check(view.sceneModelProperty().get()==s().scene(),"actual view presents current snapshot");check(!view.hasPlaybackPath(),"no old playback");
        check(s().problem()==matrix && s().scene().preparedWorld()==world,"same matrix/world");check(view.selectedIndex()==3,"selection preserved");
        if(subScene!=null){
            check(findSubScene(view)==subScene && subScene.getCamera()==camera,"same SubScene/camera");
            check(transform(camera).equals(cameraTransform),"camera pose preserved");check(terrain(subScene.getRoot())==terrain,"terrain node preserved");
            check(routeLayer().getChildren().isEmpty(),"actual route geometry emptied synchronously");
            check(!find(subScene.getRoot(),"path-playback-cursor").isVisible(),"old cursor hidden");
        }else{
            check(node("map-terrain-backdrop")==terrain,"same backdrop canvas");
            var overlay=(Canvas)node("map-path-overlay");var actual=overlay.snapshot(null,null);
            // Redraw a known empty model at the same geometry/settings. Pixel equality detects an unrefreshed old Canvas route.
            view.setModel(new PendingSceneModel(world));var expected=overlay.snapshot(null,null);
            check(Arrays.equals(pixels(actual),pixels(expected)),"actual Canvas already matches empty path rendering");
            view.setModel(s().scene());
        }
        capture(name);
    }
    private static List<Double> transform(Camera camera){var t=camera.getLocalToParentTransform();return List.of(t.getMxx(),t.getMxy(),t.getMxz(),t.getTx(),t.getMyx(),t.getMyy(),t.getMyz(),t.getTy(),t.getMzx(),t.getMzy(),t.getMzz(),t.getTz());}
    private static void step(ManualExecutor worker,Runnable next){driver.execute(()->{try{worker.step();Platform.runLater(()->guard(next));}catch(Throwable t){Platform.runLater(()->fail(t));}});}
    private static void pump(Runnable next){iteration=0;pumpNext(next);}
    private static void pumpNext(Runnable next){
        if(++iteration>50)throw new AssertionError("non-quiescent operation: "+s().phase());
        driver.execute(()->{try{boolean worked=compute.step()|visual.step();Platform.runLater(()->guard(()->{
            boolean deliveries=!ui.isEmpty();drainUi();
            if(worked||deliveries||!compute.tasks.isEmpty()||!visual.tasks.isEmpty())pumpNext(next);
            else when(view::isReady,next);
        }));}catch(Throwable t){Platform.runLater(()->fail(t));}});
    }
    private static void drainUi(){while(!ui.isEmpty())ui.remove().run();}
    private static void when(BooleanSupplier condition,Runnable next){new AnimationTimer(){final long start=System.nanoTime();public void handle(long now){try{
        if(condition.getAsBoolean()){stop();Platform.runLater(()->guard(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();next.run();}));}
        else if(now-start>TimeUnit.SECONDS.toNanos(20)){stop();throw new AssertionError("renderer not ready");}
    }catch(Throwable t){stop();fail(t);}}}.start();}
    private static Node node(String id){return Objects.requireNonNull(stage.getScene().lookup("#"+id),id);}
    private static void click(String id){check(!node(id).isDisabled(),id+" disabled");((Button)node(id)).fire();}
    @SuppressWarnings("unchecked") private static <T> void combo(String id,T value){((ComboBox<T>)node(id)).setValue(value);}
    private static OperationalSnapshot s(){return app.controllerForReview().snapshot();}
    private static SubScene findSubScene(Node n){if(n instanceof SubScene sub)return sub;if(n instanceof Parent p)for(var child:p.getChildrenUnmodifiable()){var found=findSubScene(child);if(found!=null)return found;}return null;}
    private static Node find(Node n,String id){if(id.equals(n.getId()))return n;if(n instanceof Parent p)for(var c:p.getChildrenUnmodifiable()){var hit=find(c,id);if(hit!=null)return hit;}return null;}
    private static Group routeLayer(){return (Group)Objects.requireNonNull(find(subScene.getRoot(),"active-route-layer"));}
    private static Node terrain(Node n){if(n instanceof javafx.scene.shape.MeshView)return n;if(n instanceof Parent p)for(var c:p.getChildrenUnmodifiable()){var found=terrain(c);if(found!=null)return found;}return null;}
    private static int[] pixels(javafx.scene.image.Image image){int w=(int)image.getWidth(),h=(int)image.getHeight();var data=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),data,0,w);return data;}
    private static void capture(String name){try{
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var shot=stage.getScene().snapshot(null);int w=(int)shot.getWidth(),h=(int)shot.getHeight();
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,pixels(shot),0,w);ImageIO.write(image,"png",output.resolve(name+".png").toFile());
        rows.add(name+"\treal-solver-controlled-workers-api-ui\t"+s().instanceId()+"\t"+s().runId()+"\t"+s().request().algorithm()+"\t"+s().phase()+"\t"+s().scene().segments().size()+"\t"+(subScene==null?"canvas-pixel-equality":routeLayer().getChildren().size())+"\t"+view.selectedIndex()+"\t"+(world==s().scene().preparedWorld()));
    }catch(Exception e){throw new IllegalStateException(e);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void guard(Runnable action){try{action.run();}catch(Throwable t){fail(t);}}
    private static void fail(Throwable t){failure.compareAndSet(null,t);t.printStackTrace();finish();}
    private static void finish(){try{Files.write(output.resolve("transitions-manifest.tsv"),rows);}catch(Exception e){failure.compareAndSet(null,e);}finally{if(app!=null)app.stop();if(stage!=null)stage.close();driver.shutdownNow();Platform.exit();finished.countDown();}}
}
