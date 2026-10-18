package io.github.nathan1227n.nodewalker.spike.fx;

import io.github.nathan1227n.nodewalker.spike.*;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.*;

public final class FxPrototype extends Application {
    private static final DisplayTransform TRANSFORM=DisplayTransform.JAVAFX;
    private final Group world=new Group(), landscape=new Group(), decoration=new Group(), glow=new Group();
    private final OrbitCamera orbit=new OrbitCamera();
    private final Map<String,PhongMaterial> materials=new HashMap<>();
    private final List<Node> anchors=new ArrayList<>();
    private final List<Label> labels=new ArrayList<>();
    private final PerspectiveCamera camera=new PerspectiveCamera(true);
    private RunOptions options;
    private RunRecorder recorder;
    private Label selection, mode;
    private Pane root;
    private boolean simple, captured;
    private double mouseX,mouseY;
    private AnimationTimer timer;

    @Override public void start(Stage stage) {
        options=RunOptions.parse(getParameters().getRaw()); simple=options.simple();
        recorder=new RunRecorder("JavaFX 21.0.12",options);
        recorder.put("scene3d",Platform.isSupported(ConditionalFeature.SCENE3D));
        recorder.put("frameRateCap","none");
        recorder.put("vsyncProperty",System.getProperty("prism.vsync","default"));
        if(!Platform.isSupported(ConditionalFeature.SCENE3D)) {
            recorder.event("Scene3D unavailable; no rendered benchmark"); recorder.finish();
            System.err.println("Scene3D indisponivel. Use o app principal com --2d; veja docs/SPIKE_RENDERIZADORES.md.");
            Platform.exit(); return;
        }
        buildWorld();
        SubScene view=new SubScene(world,1280,720,true,SceneAntialiasing.DISABLED);
        view.setFill(Color.web(SceneGeometry.BACKGROUND));
        camera.setNearClip(1); camera.setFarClip(10000); camera.setFieldOfView(45); camera.setVerticalFieldOfView(true);
        view.setCamera(camera);
        root=new Pane(view); root.setPrefSize(1280,720);
        buildOverlay();
        view.setOnMousePressed(e->{ mouseX=e.getSceneX();mouseY=e.getSceneY(); });
        view.setOnMouseDragged(e->{
            if(!options.benchmark() && !options.captureOnly()) {
                orbit.orbit(e.getSceneX()-mouseX,e.getSceneY()-mouseY); recorder.event("orbit");
                mouseX=e.getSceneX();mouseY=e.getSceneY();
            }
        });
        view.setOnScroll(e->{if(!options.benchmark() && !options.captureOnly()) { orbit.zoom(e.getDeltaY()/40);recorder.event("zoom"); }});
        Scene scene=new Scene(root,1280,720);
        scene.setOnKeyPressed(e->{
            if(e.getCode()==KeyCode.F12) capture();
            if(options.benchmark() || options.captureOnly()) return;
            if(e.getCode()==KeyCode.R) { orbit.reset(); recorder.event("reset"); }
            if(e.getCode()==KeyCode.M) { simple=!simple; rebuildTerrain(); recorder.event("simple="+simple); }
            if(e.getCode()==KeyCode.D) { decoration.setVisible(!decoration.isVisible());recorder.event("decoration="+decoration.isVisible()); }
            if(e.getCode()==KeyCode.G) { glow.setVisible(!glow.isVisible());recorder.event("glow="+glow.isVisible()); }
            if(e.getCode().isDigitKey()) {
                try { int i=Integer.parseInt(e.getText());if(i<5) select(i); } catch(NumberFormatException ignored) { }
            }
        });
        stage.setTitle("NodeWalker | JavaFX 3D | spike #23");stage.setResizable(false);stage.setScene(scene);
        updateCamera();stage.show();
        recorder.put("renderScaleX",stage.getRenderScaleX());recorder.put("renderScaleY",stage.getRenderScaleY());
        timer=new AnimationTimer() {
            @Override public void handle(long now) {
                recorder.window(stage.isFocused(),stage.isShowing(),stage.isIconified());
                boolean done=recorder.frame(now);
                if(options.benchmark()) orbit.benchmark(recorder.seconds(now));
                updateCamera();updateLabels();
                if(options.captureOnly() && recorder.seconds(now)>=2 && !captured) { capture();captured=true; }
                if(options.captureOnly() && recorder.seconds(now)>=3) Platform.exit();
                if(done && options.benchmark()) { recorder.finish();Platform.exit(); }
            }
        };timer.start();
    }
    private void buildWorld() {
        world.getChildren().addAll(landscape,decoration,glow);
        rebuildTerrain();
        for(var p:SceneGeometry.decoration()) decoration.getChildren().add(shape(p));
        for(var p:SceneGeometry.shelters()) {
            Shape3D s=shape(p);s.setOnMouseClicked(e->{select(p.owner());e.consume();});world.getChildren().add(s);
            if(p.shape()==SceneGeometry.Shape.SPHERE) anchors.add(s);
        }
        List<Integer> cycle=SceneSnapshot.cycle();
        for(int i=1;i<cycle.size();i++) {
            Vec3 a=SceneSnapshot.cities().get(cycle.get(i-1)), b=SceneSnapshot.cities().get(cycle.get(i));
            world.getChildren().add(edge(a,b,2.7,"#AEF9FF",1));
            glow.getChildren().add(edge(a,b,6,SceneGeometry.CYAN,.10));
        }
        glow.setVisible(false); // Optional approximation, excluded from equivalent baseline.
        world.getChildren().add(new AmbientLight(Color.web("#75848A")));
        PointLight key=new PointLight(Color.web("#C6DEEF"));place(key,new Vec3(-250,1100,-350));world.getChildren().add(key);
        PointLight rim=new PointLight(Color.web("#477D9B"));place(rim,new Vec3(1100,700,1050));world.getChildren().add(rim);
    }
    private void rebuildTerrain() {
        landscape.getChildren().clear();
        var triangles=SceneGeometry.terrain(simple);
        for(int band=0;band<SceneGeometry.ROCK.length;band++) {
            TriangleMesh mesh=new TriangleMesh();mesh.getTexCoords().addAll(0,0);
            for(var t:triangles) if(t.band()==band) {
                int first=mesh.getPoints().size()/3;
                for(Vec3 p:List.of(t.a(),t.b(),t.c())) { // proper rotation preserves winding
                    Vec3 v=TRANSFORM.forward(p);mesh.getPoints().addAll((float)v.x(),(float)v.y(),(float)v.z());
                }
                mesh.getFaces().addAll(first,0,first+1,0,first+2,0);mesh.getFaceSmoothingGroups().addAll(0);
            }
            MeshView mv=new MeshView(mesh);mv.setMaterial(material(SceneGeometry.ROCK[band],false));landscape.getChildren().add(mv);
        }
        decoration.setVisible(!simple);
        if(mode!=null) mode.setText(modeText());
    }
    private Shape3D shape(SceneGeometry.Part p) {
        Vec3 d=p.dimensions();
        Shape3D node=switch(p.shape()) {
            case BOX -> new Box(d.x(),d.y(),d.z());
            case SPHERE -> new Sphere(d.x(),16);
            case CYLINDER -> new Cylinder(d.x(),d.y(),12);
        };
        place(node,p.position());node.setMaterial(material(p.color(),p.emissive()));return node;
    }
    private PhongMaterial material(String hex,boolean emissive) {
        return materials.computeIfAbsent(hex+emissive,k->{
            PhongMaterial m=new PhongMaterial(Color.web(hex));m.setSpecularColor(Color.web("#26343A"));
            if(emissive) {
                WritableImage image=new WritableImage(1,1);image.getPixelWriter().setColor(0,0,Color.web(hex));
                m.setSelfIlluminationMap(image);m.setDiffuseColor(Color.BLACK);
            }
            return m;
        });
    }
    private Cylinder edge(Vec3 a,Vec3 b,double radius,String color,double alpha) {
        Vec3 start=TRANSFORM.forward(a),end=TRANSFORM.forward(b),delta=end.subtract(start),mid=start.add(end).scale(.5);
        Cylinder c=new Cylinder(radius,delta.length(),12);c.setMaterial(material(color,true));c.setOpacity(alpha);
        c.setTranslateX(mid.x());c.setTranslateY(mid.y());c.setTranslateZ(mid.z());
        Point3D direction=new Point3D(delta.x(),delta.y(),delta.z()), up=new Point3D(0,1,0), axis=up.crossProduct(direction);
        c.getTransforms().add(new Rotate(up.angle(direction),axis.magnitude()==0?Rotate.X_AXIS:axis));return c;
    }
    private static void place(Node node,Vec3 logical) {
        Vec3 v=TRANSFORM.forward(logical);node.setTranslateX(v.x());node.setTranslateY(v.y());node.setTranslateZ(v.z());
    }
    private void updateCamera() {
        Vec3 p=TRANSFORM.forward(orbit.position()),t=TRANSFORM.forward(OrbitCamera.TARGET);
        Point3D f=new Point3D(t.x()-p.x(),t.y()-p.y(),t.z()-p.z()).normalize();
        Point3D r=f.crossProduct(new Point3D(0,-1,0)).normalize(), d=f.crossProduct(r).normalize();
        camera.getTransforms().setAll(new Affine(r.getX(),d.getX(),f.getX(),p.x(),r.getY(),d.getY(),f.getY(),p.y(),r.getZ(),d.getZ(),f.getZ(),p.z()));
    }
    private Label label(String text,double x,double y,int size,String color) {
        Label label=new Label(text);label.setLayoutX(x);label.setLayoutY(y);
        label.setStyle("-fx-font-family: monospace; -fx-font-size: "+size+"px; -fx-text-fill: "+color+"; -fx-background-color: rgba(10,17,25,0.82); -fx-padding: 5 10 5 10;");
        root.getChildren().add(label);return label;
    }
    private void buildOverlay() {
        label("N O D E W A L K E R   /   ULTIMOS REFUGIOS",24,18,22,"#EAF6FF");
        label("SPIKE 23  /  JAVAFX 3D  /  "+SceneSnapshot.ID,24,56,12,SceneGeometry.CYAN);
        mode=label(modeText(),24,82,12,"#98B2C7");
        selection=label(SceneSnapshot.describe(0),24,584,14,SceneGeometry.AMBER);
        label("CICLO  0 > 1 > 2 > 3 > 4 > 0   |   custo da matriz: "+SceneSnapshot.cycleCost(),24,617,12,"#EAF6FF");
        label(SceneSnapshot.NOTICE,24,644,11,"#98B2C7");
        label("Arrastar: orbita | Roda: zoom | R: reset | M: simples | D: decoracao | G: halo | 0-4: selecionar | F12: captura",24,677,11,"#98B2C7");
        for(int i=0;i<5;i++) {
            int index=i;Label l=label(i==0?"BASE 0":"R "+i,0,0,12,i==0?SceneGeometry.AMBER:SceneGeometry.CYAN);
            l.setOnMouseClicked(e->select(index));labels.add(l);
        }
    }
    private String modeText() { return (simple?"32x32 / reduzido":"64x64 / referencia")+" | "+(options.benchmark()?"ORBITA AUTOMATICA / medindo callbacks":"EXPLORACAO MANUAL"); }
    private void select(int i) { if(options.benchmark() || options.captureOnly()) return; selection.setText(SceneSnapshot.describe(i));recorder.event("select="+i); }
    private void updateLabels() {
        for(int i=0;i<anchors.size();i++) {
            Point3D p=anchors.get(i).localToScene(Point3D.ZERO,true);Label l=labels.get(i);
            l.setVisible(p!=null && p.getX()>0 && p.getX()<1220 && p.getY()>110 && p.getY()<580);
            if(p!=null) { l.setLayoutX(p.getX()+10);l.setLayoutY(p.getY()-28); }
        }
    }
    private void capture() {
        recorder.event("screenshot");
        try {
            var path=recorder.output().resolve("javafx-"+System.currentTimeMillis()+".png");
            ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",path.toFile());
            System.out.println("Capture: "+path.toAbsolutePath());
        } catch(IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    @Override public void stop() { if(timer!=null) timer.stop();if(recorder!=null) recorder.finish(); }
}
