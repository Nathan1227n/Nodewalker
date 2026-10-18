package io.github.nathan1227n.nodewalker.spike.jme;

import io.github.nathan1227n.nodewalker.spike.*;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.light.AmbientLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;
import com.jme3.system.AppSettings;
import com.jme3.system.lwjgl.LwjglWindow;
import org.lwjgl.glfw.GLFW;
import com.jme3.util.BufferUtils;
import java.io.File;
import java.util.*;

public final class JmePrototype extends SimpleApplication {
    private static final DisplayTransform TRANSFORM=DisplayTransform.JME;
    private final RunOptions options;
    private final RunRecorder recorder;
    private final OrbitCamera orbit=new OrbitCamera();
    private final Node landscape=new Node("terrain"),decoration=new Node("decoration"),shelters=new Node("shelters"),glow=new Node("halo");
    private final Map<String,Material> materials=new HashMap<>();
    private final List<BitmapText> labels=new ArrayList<>();
    private BitmapText selection,mode;
    private ScreenshotAppState screenshot;
    private boolean simple,dragging,showDecoration,showGlow,captured;
    private float dragDistance;

    private JmePrototype(RunOptions options) {
        this.options=options;simple=options.simple();showDecoration=!simple;
        recorder=new RunRecorder("jMonkeyEngine 3.9.0-stable / LWJGL3",options);
    }
    public static void main(String[] args) {
        var app=new JmePrototype(RunOptions.parse(List.of(args)));
        AppSettings settings=new AppSettings(true);
        settings.setResolution(1280,720);settings.setSamples(0);settings.setVSync(true);settings.setFrameRate(-1);settings.setX11PlatformPreferred(true);
        settings.setGammaCorrection(false);settings.setResizable(false);settings.setAudioRenderer(null);
        settings.setRenderer(AppSettings.LWJGL_OPENGL33);settings.setTitle("NodeWalker | jMonkeyEngine | spike #23");
        app.setSettings(settings);app.setShowSettings(false);app.setPauseOnLostFocus(false);app.start();
    }
    @Override public void simpleInitApp() {
        flyCam.setEnabled(false);setDisplayFps(false);setDisplayStatView(false);
        inputManager.setCursorVisible(true);viewPort.setBackgroundColor(color(SceneGeometry.BACKGROUND));
        cam.setFrustumPerspective(45,1280f/720,1,10000);
        recorder.put("framebufferWidth",cam.getWidth());recorder.put("framebufferHeight",cam.getHeight());
        recorder.put("frameRateCap","none");recorder.put("x11PlatformPreferred",true);recorder.put("gammaCorrection",false);recorder.put("rendererCaps",renderer.getCaps());
        recorder.put("rendererLimits",renderer.getLimits());
        recorder.put("lostFocusBehavior",getLostFocusBehavior());
        var monitorMode=GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if(monitorMode!=null) recorder.put("primaryMonitorMode",monitorMode.width()+"x"+monitorMode.height()+"@"+monitorMode.refreshRate());
        rootNode.attachChild(landscape);rootNode.attachChild(decoration);rootNode.attachChild(shelters);rootNode.attachChild(glow);
        rebuildTerrain();
        for(var p:SceneGeometry.decoration()) decoration.attachChild(shape(p));
        for(var p:SceneGeometry.shelters()) {
            Geometry geometry=shape(p);geometry.setUserData("refuge",p.owner());shelters.attachChild(geometry);
        }
        var cycle=SceneSnapshot.cycle();
        for(int i=1;i<cycle.size();i++) {
            Vec3 a=SceneSnapshot.cities().get(cycle.get(i-1)),b=SceneSnapshot.cities().get(cycle.get(i));
            rootNode.attachChild(edge(a,b,2.7f,"#AEF9FF",1));glow.attachChild(edge(a,b,6,SceneGeometry.CYAN,.10f));
        }
        glow.setCullHint(Spatial.CullHint.Always);
        AmbientLight ambient=new AmbientLight();ambient.setColor(color("#75848A"));rootNode.addLight(ambient);
        addLight(new Vec3(-250,1100,-350),"#C6DEEF");addLight(new Vec3(1100,700,1050),"#477D9B");
        buildOverlay();setupInput();updateCamera();
        screenshot=new ScreenshotAppState(recorder.output().toAbsolutePath()+File.separator,"jme-",System.currentTimeMillis());
        stateManager.attach(screenshot);
    }
    private void addLight(Vec3 position,String hex) {
        PointLight light=new PointLight();light.setPosition(vector(position));light.setColor(color(hex));light.setRadius(0);rootNode.addLight(light);
    }
    private void rebuildTerrain() {
        landscape.detachAllChildren();var triangles=SceneGeometry.terrain(simple);
        for(int band=0;band<SceneGeometry.ROCK.length;band++) {
            List<Float> points=new ArrayList<>(),normals=new ArrayList<>();
            for(var t:triangles) if(t.band()==band) {
                Vector3f a=vector(t.a()),b=vector(t.b()),c=vector(t.c());
                Vector3f normal=b.subtract(a).cross(c.subtract(a)).normalizeLocal();
                for(Vector3f v:List.of(a,b,c)) {
                    points.add(v.x);points.add(v.y);points.add(v.z);
                    normals.add(normal.x);normals.add(normal.y);normals.add(normal.z);
                }
            }
            if(points.isEmpty()) continue;
            Mesh mesh=new Mesh();mesh.setBuffer(VertexBuffer.Type.Position,3,BufferUtils.createFloatBuffer(floats(points)));
            mesh.setBuffer(VertexBuffer.Type.Normal,3,BufferUtils.createFloatBuffer(floats(normals)));mesh.updateBound();
            Geometry geometry=new Geometry("terrain-band-"+band,mesh);geometry.setMaterial(material(SceneGeometry.ROCK[band],false));
            landscape.attachChild(geometry);
        }
        showDecoration=!simple;decoration.setCullHint(showDecoration?Spatial.CullHint.Inherit:Spatial.CullHint.Always);
        if(mode!=null) mode.setText(modeText());
    }
    private static float[] floats(List<Float> values) {
        float[] data=new float[values.size()];for(int i=0;i<data.length;i++) data[i]=values.get(i);return data;
    }
    private Geometry shape(SceneGeometry.Part p) {
        Vec3 d=p.dimensions();
        Mesh mesh=switch(p.shape()) {
            case BOX -> new Box((float)d.x()/2,(float)d.y()/2,(float)d.z()/2);
            case SPHERE -> new Sphere(16,16,(float)d.x());
            case CYLINDER -> new Cylinder(2,12,(float)d.x(),(float)d.y(),true);
        };
        Geometry g=new Geometry("prop",mesh);g.setMaterial(material(p.color(),p.emissive()));g.setLocalTranslation(vector(p.position()));
        if(p.shape()==SceneGeometry.Shape.CYLINDER) g.rotate(FastMath.HALF_PI,0,0); // JME cylinder axis is Z
        return g;
    }
    private Material material(String hex,boolean emissive) {
        return materials.computeIfAbsent(hex+emissive,k->{
            Material m=new Material(assetManager,emissive?"Common/MatDefs/Misc/Unshaded.j3md":"Common/MatDefs/Light/Lighting.j3md");
            if(emissive) m.setColor("Color",color(hex));
            else { m.setBoolean("UseMaterialColors",true);m.setColor("Diffuse",color(hex));m.setColor("Ambient",color(hex));m.setColor("Specular",color("#26343A"));m.setFloat("Shininess",32); }
            return m;
        });
    }
    private Geometry edge(Vec3 a,Vec3 b,float radius,String hex,float alpha) {
        Vector3f start=vector(a),end=vector(b),direction=end.subtract(start);
        Geometry g=new Geometry("direct-tsp-edge",new Cylinder(2,12,radius,direction.length(),true));
        g.setLocalTranslation(start.add(end).multLocal(.5f));
        g.setLocalRotation(new Quaternion().lookAt(direction.normalize(),Vector3f.UNIT_Y));
        Material m=material(hex,true);
        if(alpha<1) {
            m=m.clone();ColorRGBA c=color(hex);c.a=alpha;m.setColor("Color",c);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.getAdditionalRenderState().setDepthWrite(false);g.setQueueBucket(RenderQueue.Bucket.Transparent);
        }
        g.setMaterial(m);return g;
    }
    private static Vector3f vector(Vec3 logical) {
        Vec3 v=TRANSFORM.forward(logical);return new Vector3f((float)v.x(),(float)v.y(),(float)v.z());
    }
    private static ColorRGBA color(String hex) {
        int rgb=Integer.parseInt(hex.substring(1),16);return new ColorRGBA(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1);
    }
    private void updateCamera() { cam.setLocation(vector(orbit.position()));cam.lookAt(vector(OrbitCamera.TARGET),Vector3f.UNIT_Y); }
    private BitmapText text(String value,float x,float top,float size,String hex) {
        BitmapText t=new BitmapText(guiFont);t.setSize(size);t.setText(value);t.setColor(color(hex));t.setLocalTranslation(x,720-top,2);guiNode.attachChild(t);return t;
    }
    private void panel(float x,float top,float width,float height) {
        Geometry p=new Geometry("overlay-panel",new Quad(width,height));Material m=material(SceneGeometry.BACKGROUND,true).clone();
        ColorRGBA c=color(SceneGeometry.BACKGROUND);c.a=.82f;m.setColor("Color",c);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);p.setMaterial(m);p.setLocalTranslation(x,720-top-height,1);guiNode.attachChild(p);
    }
    private void buildOverlay() {
        panel(24,18,650,90);panel(24,584,1220,122);
        text("N O D E W A L K E R   /   ULTIMOS REFUGIOS",34,24,22,"#EAF6FF");
        text("SPIKE 23  /  JMONKEYENGINE  /  "+SceneSnapshot.ID,34,62,12,SceneGeometry.CYAN);
        mode=text(modeText(),34,88,12,"#98B2C7");
        selection=text(SceneSnapshot.describe(0),34,590,14,SceneGeometry.AMBER);
        text("CICLO  0 > 1 > 2 > 3 > 4 > 0   |   custo da matriz: "+SceneSnapshot.cycleCost(),34,623,12,"#EAF6FF");
        text(SceneSnapshot.NOTICE,34,650,11,"#98B2C7");
        text("Arrastar: orbita | Roda: zoom | R: reset | M: simples | D: decoracao | G: halo | 0-4: selecionar | F12: captura",34,683,11,"#98B2C7");
        for(int i=0;i<5;i++) labels.add(text(i==0?"BASE 0":"R "+i,0,0,12,i==0?SceneGeometry.AMBER:SceneGeometry.CYAN));
    }
    private String modeText() { return (simple?"32x32 / reduzido":"64x64 / referencia")+" | "+(options.benchmark()?"ORBITA AUTOMATICA / medindo callbacks":"EXPLORACAO MANUAL"); }
    private void select(int i) { selection.setText(SceneSnapshot.describe(i));recorder.event("select="+i); }
    private void setupInput() {
        bind("reset",KeyInput.KEY_R);bind("simple",KeyInput.KEY_M);bind("decor",KeyInput.KEY_D);bind("glow",KeyInput.KEY_G);bind("capture",KeyInput.KEY_F12);
        int[] digits={KeyInput.KEY_0,KeyInput.KEY_1,KeyInput.KEY_2,KeyInput.KEY_3,KeyInput.KEY_4};
        for(int i=0;i<5;i++) bind("select"+i,digits[i]);
        inputManager.addMapping("drag",new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener((ActionListener)(name,pressed,tpf)->{
            if(name.equals("capture") && !pressed) {recorder.event("screenshot");screenshot.takeScreenshot();return;}
            if(options.benchmark() || options.captureOnly()) return;
            if(name.equals("drag")) {
                dragging=pressed;if(pressed) dragDistance=0;else if(dragDistance<.01) pick();return;
            }
            if(pressed) return;
            recorder.event(name);
            switch(name) {
                case "reset" -> orbit.reset();
                case "simple" -> {simple=!simple;rebuildTerrain();}
                case "decor" -> {showDecoration=!showDecoration;decoration.setCullHint(showDecoration?Spatial.CullHint.Inherit:Spatial.CullHint.Always);}
                case "glow" -> {showGlow=!showGlow;glow.setCullHint(showGlow?Spatial.CullHint.Inherit:Spatial.CullHint.Always);}
                default -> {if(name.startsWith("select")) select(Integer.parseInt(name.substring(6)));}
            }
        },"reset","simple","decor","glow","capture","drag","select0","select1","select2","select3","select4");
        inputManager.addMapping("left",new MouseAxisTrigger(MouseInput.AXIS_X,true));
        inputManager.addMapping("right",new MouseAxisTrigger(MouseInput.AXIS_X,false));
        inputManager.addMapping("up",new MouseAxisTrigger(MouseInput.AXIS_Y,false));
        inputManager.addMapping("down",new MouseAxisTrigger(MouseInput.AXIS_Y,true));
        inputManager.addMapping("in",new MouseAxisTrigger(MouseInput.AXIS_WHEEL,false));
        inputManager.addMapping("out",new MouseAxisTrigger(MouseInput.AXIS_WHEEL,true));
        inputManager.addListener((AnalogListener)(name,value,tpf)->{
            if(options.benchmark() || options.captureOnly()) return;
            if(name.equals("in") || name.equals("out")) {orbit.zoom(name.equals("in")?1:-1);recorder.event("zoom");}
            else if(dragging) {
                dragDistance+=value;recorder.event("orbit");
                switch(name) {
                    case "left" -> orbit.orbit(-value*1280,0);
                    case "right" -> orbit.orbit(value*1280,0);
                    case "up" -> orbit.orbit(0,-value*720);
                    case "down" -> orbit.orbit(0,value*720);
                    default -> { }
                }
            }
        },"left","right","up","down","in","out");
    }
    private void bind(String name,int key) { inputManager.addMapping(name,new KeyTrigger(key)); }
    private void pick() {
        Vector2f cursor=inputManager.getCursorPosition();
        Vector3f near=cam.getWorldCoordinates(cursor,0),far=cam.getWorldCoordinates(cursor,1);
        CollisionResults hits=new CollisionResults();shelters.collideWith(new Ray(near,far.subtract(near).normalizeLocal()),hits);
        if(hits.size()>0) { Integer index=hits.getClosestCollision().getGeometry().getUserData("refuge");if(index!=null) select(index); }
    }
    @Override public void simpleUpdate(float tpf) {
        long handle=((LwjglWindow)getContext()).getWindowHandle();
        recorder.window(GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE,
                GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_ICONIFIED)==GLFW.GLFW_TRUE);
        long now=System.nanoTime();boolean done=recorder.frame(now);
        if(options.benchmark()) orbit.benchmark(recorder.seconds(now));updateCamera();
        for(int i=0;i<5;i++) {
            Vector3f p=cam.getScreenCoordinates(vector(SceneSnapshot.cities().get(i)));BitmapText l=labels.get(i);
            boolean visible=p.z>=0 && p.z<=1 && p.x>0 && p.x<1220 && p.y>140 && p.y<610;
            l.setCullHint(visible?Spatial.CullHint.Inherit:Spatial.CullHint.Always);l.setLocalTranslation(p.x+10,p.y+28,2);
        }
        if(options.captureOnly() && recorder.seconds(now)>=2 && !captured) { recorder.event("screenshot");screenshot.takeScreenshot();captured=true; }
        if(options.captureOnly() && recorder.seconds(now)>=3) stop();
        if(done && options.benchmark()) {recorder.finish();stop();}
    }
    @Override public void handleError(String message,Throwable error) {
        recorder.event("rendering error: "+message);recorder.put("renderingError",String.valueOf(error));
        recorder.finish();System.err.println(message);if(error!=null) error.printStackTrace();stop();
    }
    @Override public void destroy() {try {recorder.finish();} finally {super.destroy();} }
}
