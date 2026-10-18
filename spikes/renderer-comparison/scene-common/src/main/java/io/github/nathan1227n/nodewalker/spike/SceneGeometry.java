package io.github.nathan1227n.nodewalker.spike;

import java.util.ArrayList;
import java.util.List;

/** All authored meshes/props and terrain sampling are shared; no external assets. */
public final class SceneGeometry {
    public static final int SEED = 23; // Analytic phase seed, not a TSP generation seed.
    public static final String BACKGROUND = "#0A1119", CYAN = "#58DAE5", AMBER = "#FFBE64";
    public static final String[] ROCK = {"#26343A", "#38494B", "#465951", "#566361", "#71766F"};
    public enum Shape { BOX, SPHERE, CYLINDER }
    /** Cylinder dimensions: x=radius, y=height; local Y axis. owner=-1 means decoration. */
    public record Part(Shape shape, Vec3 position, Vec3 dimensions, String color, boolean emissive, int owner) { }
    public record Triangle(Vec3 a, Vec3 b, Vec3 c, int band) { }
    private SceneGeometry() { }

    public static double height(double x, double z) {
        double h = 18 + hill(x,z,-70,150,410,210) + hill(x,z,520,-100,420,210)
                + hill(x,z,1060,470,520,200) + hill(x,z,80,1020,600,240)
                + hill(x,z,950,1100,310,210);
        h += 14 * Math.sin(x * .039 + SEED) * Math.cos(z * .028) + 7 * Math.sin((x + z) * .081);
        for (Vec3 p : SceneSnapshot.cities()) {
            double d = Math.hypot(x - p.x(), z - p.z());
            double blend = Math.clamp((125 - d) / 80, 0, 1);
            blend = blend * blend * (3 - 2 * blend);
            h = h * (1 - blend) + (p.y() - 48) * blend;
        }
        return h;
    }
    private static double hill(double x, double z, double cx, double cz, double h, double width) {
        return h * Math.exp(-((x-cx)*(x-cx)+(z-cz)*(z-cz))/(width*width));
    }
    public static List<Triangle> terrain(boolean simple) {
        int cells = simple ? 32 : 64;
        List<Triangle> result = new ArrayList<>(cells * cells * 2);
        for (int z = 0; z < cells; z++) for (int x = 0; x < cells; x++) {
            Vec3 a = vertex(x,z,cells), b = vertex(x+1,z,cells), c = vertex(x,z+1,cells), d = vertex(x+1,z+1,cells);
            result.add(triangle(a,c,b)); result.add(triangle(b,c,d)); // +Y front face
        }
        return List.copyOf(result);
    }
    private static Vec3 vertex(int x, int z, int cells) {
        double px = -150 + x * 1300.0 / cells, pz = -150 + z * 1300.0 / cells;
        return new Vec3(px, height(px,pz), pz);
    }
    private static Triangle triangle(Vec3 a, Vec3 b, Vec3 c) {
        return new Triangle(a,b,c,(int)Math.clamp((a.y()+b.y()+c.y())/3/100,0,4));
    }
    public static List<Part> shelters() {
        List<Part> p = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Vec3 c = SceneSnapshot.cities().get(i);
            String beacon = i == 0 ? AMBER : CYAN;
            double size = i == 0 ? 1.25 : 1;
            p.add(part(Shape.BOX,c,0,-47,0,78*size,5,64*size,"#26343A",false,i));
            p.add(part(Shape.BOX,c,0,-29,0,52*size,32,40*size,"#465951",false,i));
            p.add(part(Shape.BOX,c,0,-11,0,60*size,5,48*size,"#15222B",false,i));
            p.add(part(Shape.BOX,c,0,-27,-21*size,32*size,6,1.5,beacon,true,i));
            p.add(part(Shape.BOX,c,27*size,-27,0,1.5,6,25*size,beacon,true,i));
            p.add(part(Shape.CYLINDER,c,0,-6,0,2,12,2,"#71766F",false,i));
            p.add(part(Shape.SPHERE,c,0,0,0,i==0?7:5,0,0,beacon,true,i)); // EXACT logical route anchor
        }
        return List.copyOf(p);
    }
    public static List<Part> decoration() {
        List<Part> p = new ArrayList<>();
        Vec3 a = new Vec3(580,height(580,480),480);
        p.add(part(Shape.BOX,a,0,4,0,68,8,60,"#38494B",false,-1));
        p.add(part(Shape.CYLINDER,a,0,70,0,3,140,3,"#71766F",false,-1));
        p.add(part(Shape.BOX,a,0,115,0,75,3,3,"#71766F",false,-1));
        p.add(part(Shape.BOX,a,0,95,0,44,3,3,"#71766F",false,-1));
        p.add(part(Shape.SPHERE,a,0,141,0,3,0,0,AMBER,true,-1));
        Vec3 r = new Vec3(380,height(380,370),370);
        p.add(part(Shape.BOX,r,0,22,0,9,44,65,"#4A5150",false,-1));
        p.add(part(Shape.BOX,r,36,13,28,65,26,9,"#4A5150",false,-1));
        p.add(part(Shape.BOX,r,42,8,-10,17,16,27,"#71766F",false,-1));
        for (int i=0;i<22;i++) {
            double x = 30 + ((i*173)%930), z = 50 + ((i*251)%910);
            Vec3 b = new Vec3(x,height(x,z),z);
            p.add(part(Shape.BOX,b,0,4+i%6,0,8+i%7,8+i%11,12+i%9,"#38494B",false,-1));
        }
        return List.copyOf(p);
    }
    private static Part part(Shape s, Vec3 c, double x,double y,double z,double w,double h,double d,String color,boolean e,int owner) {
        return new Part(s,c.add(new Vec3(x,y,z)),new Vec3(w,h,d),color,e,owner);
    }
}
