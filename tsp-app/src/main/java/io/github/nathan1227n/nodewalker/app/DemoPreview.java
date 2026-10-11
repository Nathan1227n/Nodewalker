package io.github.nathan1227n.nodewalker.app;

import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.canvas.Canvas;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/** Apenas uma prova gráfica de G0; não representa uma solução algorítmica. */
final class DemoPreview {
    private static final Color BACKGROUND = Color.web("#081321");
    private static final Color ROUTE_COLOR = Color.web("#50F0C8");
    private static final Color CITY_COLOR = Color.web("#79B8FF");

    private static final Point3D[] CITIES = {
        new Point3D(-195, 60, 300), new Point3D(-65, 15, 480),
        new Point3D(130, 50, 480), new Point3D(215, 20, 300),
        new Point3D(0, 80, 260)
    };

    private DemoPreview() {
    }

    static Node create3d() {
        Group world = new Group();

        Box terrain = new Box(620, 8, 480);
        terrain.setTranslateX(0);
        terrain.setTranslateY(153);
        terrain.setTranslateZ(385);
        terrain.setMaterial(new PhongMaterial(Color.web("#10273A")));
        world.getChildren().add(terrain);

        for (int i = 0; i < CITIES.length; i++) {
            Point3D from = CITIES[i];
            Point3D to = CITIES[(i + 1) % CITIES.length];
            world.getChildren().add(edge(from, to));
        }

        for (Point3D point : CITIES) {
            Sphere city = new Sphere(21);
            city.setMaterial(new PhongMaterial(CITY_COLOR));
            city.setTranslateX(point.getX());
            city.setTranslateY(point.getY());
            city.setTranslateZ(point.getZ());
            world.getChildren().add(city);
        }

        world.getChildren().add(new AmbientLight(Color.web("#96B1C3")));
        PointLight lamp = new PointLight(Color.WHITE);
        lamp.setTranslateX(-200);
        lamp.setTranslateY(-270);
        lamp.setTranslateZ(-200);
        world.getChildren().add(lamp);

        SubScene subScene = new SubScene(world, 700, 500, true, SceneAntialiasing.BALANCED);
        subScene.setFill(BACKGROUND);
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(1);
        camera.setFarClip(5000);
        camera.setFieldOfView(45);
        camera.setTranslateZ(-1000);
        camera.setTranslateY(-115);
        camera.setRotationAxis(Rotate.X_AXIS);
        camera.setRotate(-7);
        subScene.setCamera(camera);

        StackPane holder = new StackPane(subScene);
        subScene.widthProperty().bind(holder.widthProperty());
        subScene.heightProperty().bind(holder.heightProperty());
        return holder;
    }

    private static Cylinder edge(Point3D start, Point3D end) {
        Point3D difference = end.subtract(start);
        Cylinder connection = new Cylinder(3, difference.magnitude());
        connection.setMaterial(new PhongMaterial(ROUTE_COLOR));
        Point3D midpoint = start.add(end).multiply(0.5);
        connection.setTranslateX(midpoint.getX());
        connection.setTranslateY(midpoint.getY());
        connection.setTranslateZ(midpoint.getZ());

        Point3D yAxis = new Point3D(0, 1, 0);
        Point3D axis = yAxis.crossProduct(difference);
        if (axis.magnitude() > 0) {
            connection.setRotationAxis(axis);
            connection.setRotate(yAxis.angle(difference));
        }
        return connection;
    }

    static Node create2d() {
        Canvas canvas = new Canvas(700, 500);
        StackPane holder = new StackPane(canvas);
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());
        canvas.widthProperty().addListener((observable, oldValue, newValue) -> draw2d(canvas));
        canvas.heightProperty().addListener((observable, oldValue, newValue) -> draw2d(canvas));
        draw2d(canvas);
        return holder;
    }

    private static void draw2d(Canvas canvas) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);

        double[][] positions = {
            {0.19, 0.66}, {0.35, 0.29}, {0.63, 0.22}, {0.82, 0.61}, {0.47, 0.77}
        };
        gc.setStroke(ROUTE_COLOR);
        gc.setLineWidth(3);
        for (int i = 0; i < positions.length; i++) {
            double[] from = positions[i];
            double[] to = positions[(i + 1) % positions.length];
            gc.strokeLine(from[0] * width, from[1] * height, to[0] * width, to[1] * height);
        }
        for (int i = 0; i < positions.length; i++) {
            double px = positions[i][0] * width;
            double py = positions[i][1] * height;
            gc.setFill(CITY_COLOR);
            gc.fillOval(px - 12, py - 12, 24, 24);
            gc.setFill(Color.WHITE);
            gc.fillText("C" + i, px + 15, py - 13);
        }
        gc.setFill(Color.web("#A1B5CA"));
        gc.fillText("Mapa ilustrativo • ainda sem solver TSP", 24, height - 25);
    }
}
