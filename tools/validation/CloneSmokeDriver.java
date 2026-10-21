import io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest;
import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.stage.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** API-driven functional audit inside the real javafx:run process; never a human-input claim. */
public final class CloneSmokeDriver {
    private static Path output;
    private static Stage stage;
    private static TspWorldView view;
    private static boolean initial3d;
    private static final List<String> events=new ArrayList<>();
    public static void start(String directory) throws Exception {
        output=Path.of(directory);Files.createDirectories(output);
        // Bootstrap only: correctness below waits on UI/model predicates and pulses, never elapsed sleeps.
        for(int attempt=0;attempt<400;attempt++) {
            try { Platform.runLater(()->waitFor(()->Window.getWindows().stream().anyMatch(w->w instanceof Stage s && s.getTitle().startsWith("NodeWalker")),CloneSmokeDriver::begin));return; }
            catch(IllegalStateException notStarted){Thread.sleep(25);}
        }
        throw new IllegalStateException("JavaFX toolkit did not initialize");
    }
    private static void begin(){
        stage=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s && s.getTitle().startsWith("NodeWalker")).findFirst().orElseThrow();
        view=(TspWorldView)node("tsp-world");initial3d=view.isThreeD();
        events.add("mainClass=NodeWalkerApp; interaction=API Button.fire/ComboBox; initial3d="+initial3d+"; SCENE3D="+Platform.isSupported(javafx.application.ConditionalFeature.SCENE3D));
        ((TextField)node("city-count")).setText("5");((TextField)node("instance-seed")).setText("23");click("generate");
        waitFor(()->view.sceneModelProperty().get()!=null && view.isReady() && !node("execute").isDisabled(),()->{
            @SuppressWarnings("unchecked")var algorithm=(ComboBox<ExecutionRequest.Algorithm>)node("algorithm");algorithm.setValue(ExecutionRequest.Algorithm.BRUTE_FORCE);
            waitFor(()->!node("execute").isDisabled(),()->{click("execute");waitFor(()->view.modelProperty().get()!=null && view.modelProperty().get().result().isPresent() && view.isReady(),CloneSmokeDriver::result);});
        });
    }
    private static void result(){
        var scene=view.modelProperty().get();var result=scene.result().orElseThrow();
        check(result.optimalityGuaranteed(),"completed TSP");check(result.cost().orElseThrow()==2875,"N5 seed23 expected cost");
        check(result.route().orElseThrow().indices().equals(List.of(0,1,4,2,3,0)),"canonical route");
        check(scene.costBreakdown().total().orElseThrow().longValueExact()==2875,"complete mathematical sum");
        events.add("N=5; seed=23; algorithm=BRUTE_FORCE; status="+result.status()+"; cost="+result.cost().orElseThrow()+"; route="+result.route().orElseThrow().indices());
        view.finishPlayback();pulses(()->{capture(stage,"01-tsp-result-"+(initial3d?"3d":"2d"));
            if(initial3d){for(var n:stage.getScene().getRoot().lookupAll(".button"))if(n instanceof Button b && b.getText().equals("MAPA 2D")){b.fire();break;}}
            pulses(()->{check(!view.isThreeD(),"2D mode");check(view.sceneModelProperty().get()==scene,"same instance/result after mode switch");capture(stage,"02-tsp-2d");click("present-results");
                pulses(()->{
                    var report=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s && s.getTitle().contains("Resultados e comparação")).findFirst().orElseThrow();
                    @SuppressWarnings("unchecked")var table=(TableView<CostBreakdown.Term>)report.getScene().lookup("#cost-breakdown-table");
                    check(table!=null && table.getItems().size()==5,"all cycle terms in table");
                    capture(report,"03-full-result-table");((Button)report.getScene().lookup("#close-results")).fire();
                    events.add("resultTableTerms=5; returnIncluded=true; modeSwitchPreservedResult=true");
                    pulses(()->{try{events.add("close=WINDOW_CLOSE_REQUEST; normal JavaFX application lifecycle; no System.exit");Files.write(output.resolve("PASS.txt"),events);}catch(Exception e){throw new RuntimeException(e);}
                        stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST));
                    });
                });
            });
        });
    }
    private static Node node(String id){return Objects.requireNonNull(stage.getScene().lookup("#"+id),id);}
    private static void click(String id){check(!node(id).isDisabled(),id+" enabled");((Button)node(id)).fire();}
    private static void pulses(Runnable next){int[] count={0};waitFor(()->++count[0]>=4 && view.isReady(),next);}
    private static void waitFor(BooleanSupplier predicate,Runnable next){new AnimationTimer(){final long start=System.nanoTime();public void handle(long now){try{
        if(predicate.getAsBoolean()){stop();Platform.runLater(()->{try{stageLayout();next.run();}catch(Throwable e){fail(e);}});}
        else if(now-start>30_000_000_000L){stop();fail(new AssertionError("UI predicate timed out"));}
    }catch(Throwable e){stop();fail(e);}}}.start();}
    private static void stageLayout(){if(stage!=null){stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();}}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    private static void capture(Stage window,String name){try{
        var shot=window.getScene().snapshot(null);int w=(int)shot.getWidth(),h=(int)shot.getHeight();int[] pixels=new int[w*h];
        shot.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,pixels,0,w);ImageIO.write(image,"png",output.resolve(name+".png").toFile());
    }catch(Exception e){throw new RuntimeException(e);}}
    private static void fail(Throwable error){error.printStackTrace();try{Files.writeString(output.resolve("FAIL.txt"),error.toString());}catch(Exception ignored){}Runtime.getRuntime().halt(92);}
}
