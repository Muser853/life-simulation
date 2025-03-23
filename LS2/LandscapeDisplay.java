import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.control.Slider;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.Camera;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import java.io.File;
import java.io.IOException;
public class LandscapeDisplay extends Application {
    private static Landscape2 scape;
    private static LandscapeDisplay instance;
    private static final int WINDOW_SIZE = 800;
    private static final double CELL_SIZE = 128;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private Group cellGroup;
    private double anchorX, anchorY;
    private double anchorAngleX, anchorAngleY = 0;
    
    public LandscapeDisplay(Landscape2 landscape) {scape = landscape;}
    
    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        cellGroup = new Group();
        Scene3D scene3D = createScene3D();
        SubScene subScene = new SubScene(scene3D.getRoot(), WINDOW_SIZE, WINDOW_SIZE, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.LIGHTGREY);
        subScene.setCamera(scene3D.getCamera());
        handleMouseRotation(subScene);

        // Create control panel
        VBox controls = createControls();

        // Layout
        root.setCenter(subScene);
        root.setRight(controls);

        Scene scene = new Scene(root);
        primaryStage.setTitle("3D Game of Life");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initial update
        updateCells();
    }
    private Scene3D createScene3D() {
        Group root = new Group();
        cellGroup = new Group();
        
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-1000);
        camera.setTranslateX(WINDOW_SIZE/2.0);
        camera.setTranslateY(WINDOW_SIZE/2.0);
        camera.setNearClip(1);
        camera.setFarClip(2000);

        cellGroup.getTransforms().addAll(rotateX, rotateY);
        
        double centerX = scape.getRows() * CELL_SIZE / 2;
        double centerY = scape.getCols() * CELL_SIZE / 2;
        double centerZ = scape.getDepth() * CELL_SIZE / 2;

        cellGroup.setTranslateX(centerX);
        cellGroup.setTranslateY(centerY);
        cellGroup.setTranslateZ(centerZ);

        root.getChildren().add(cellGroup);
        return new Scene3D(root, camera);
    }
    private void handleMouseRotation(SubScene scene) {
        scene.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
        });
        scene.setOnMouseDragged(event -> {
            rotateX.setAngle(anchorAngleX - (anchorY - event.getSceneY()));
            rotateY.setAngle(anchorAngleY + (anchorX - event.getSceneX()));
        });
    }
    private VBox createControls() {
        VBox controls = new VBox(10);
        controls.setPadding(new Insets(10));

        Slider transparencySlider = new Slider(0, 1, 0.5);
        transparencySlider.setShowTickLabels(true);
        transparencySlider.setShowTickMarks(true);
        transparencySlider.valueProperty().addListener((_, _, newValue) -> 
            updateCellTransparency(newValue.doubleValue()));
            
        controls.getChildren().addAll(
            new Label("Cell Transparency"),
            transparencySlider
        );
        return controls;
    }
    private void updateCells() {
        cellGroup.getChildren().clear();

        PhongMaterial aliveMaterial = new PhongMaterial(Color.BLUE);
        PhongMaterial deadMaterial = new PhongMaterial(Color.RED);

        for (int i = 0; i < scape.getRows(); i++) {
            for (int j = 0; j < scape.getCols(); j++) {
                for (int k = 0; k < scape.getDepth(); k++) {
                    Cell cell = scape.getCell(i, j, k);
                    Box box = new Box(CELL_SIZE, CELL_SIZE, CELL_SIZE);
                    box.setTranslateX(i * CELL_SIZE);
                    box.setTranslateY(j * CELL_SIZE);
                    box.setTranslateZ(k * CELL_SIZE);
                    box.setMaterial(cell.getAlive() ? aliveMaterial : deadMaterial);
                    cellGroup.getChildren().add(box);
                }
            }
        }
    }
    private void updateCellTransparency(double transparency) {
        for (Node node : cellGroup.getChildren()) {
            if (node instanceof Box) {
                PhongMaterial material = (PhongMaterial) ((Box) node).getMaterial();
                Color color = material.getDiffuseColor();
                material.setDiffuseColor(new Color(
                    color.getRed(), 
                    color.getGreen(), 
                    color.getBlue(), 
                    transparency
                ));
            }
        }
    }
    public void refresh() {
        Platform.runLater(this::updateCells);
    }
    private static class Scene3D {
        private final Group root;
        private final Camera camera;

        public Scene3D(Group root, Camera camera) {
            this.root = root;
            this.camera = camera;
        }
        public Group getRoot() { return root; }
        public Camera getCamera() { return camera; }
    }
    // Method to update the display from external threads
    public static void updateDisplay(Landscape2 newScape) {
        scape = newScape;
        Platform.runLater(() -> {
            // If instance exists, update it
            if (instance != null) {
                instance.updateCells();
            }
        });
    }
    public static void exportBoxToSTL(Box box, String filename) throws IOException {
        File file = new File(filename);
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
            writer.println("solid box");
            
            // Export the 12 triangles (6 faces) of the box
            float w = (float) box.getWidth() / 2;
            float h = (float) box.getHeight() / 2;
            float d = (float) box.getDepth() / 2;
            
            // Front face
            writeSTLTriangle(writer, 
                new float[]{-w, -h, d}, 
                new float[]{w, -h, d}, 
                new float[]{-w, h, d});
            writeSTLTriangle(writer, 
                new float[]{w, -h, d}, 
                new float[]{w, h, d}, 
                new float[]{-w, h, d});
            
            // Back face
            writeSTLTriangle(writer, 
                new float[]{w, -h, -d}, 
                new float[]{-w, -h, -d}, 
                new float[]{w, h, -d});
            writeSTLTriangle(writer, 
                new float[]{-w, -h, -d}, 
                new float[]{-w, h, -d}, 
                new float[]{w, h, -d});
            
            // Right face
            writeSTLTriangle(writer, 
                new float[]{w, -h, d}, 
                new float[]{w, -h, -d}, 
                new float[]{w, h, d});
            writeSTLTriangle(writer, 
                new float[]{w, -h, -d}, 
                new float[]{w, h, -d}, 
                new float[]{w, h, d});
            
            // Left face
            writeSTLTriangle(writer, 
                new float[]{-w, -h, -d}, 
                new float[]{-w, -h, d}, 
                new float[]{-w, h, -d});
            writeSTLTriangle(writer, 
                new float[]{-w, -h, d}, 
                new float[]{-w, h, d}, 
                new float[]{-w, h, -d});
            
            // Top face
            writeSTLTriangle(writer, 
                new float[]{-w, h, d}, 
                new float[]{w, h, d}, 
                new float[]{-w, h, -d});
            writeSTLTriangle(writer, 
                new float[]{w, h, d}, 
                new float[]{w, h, -d}, 
                new float[]{-w, h, -d});
            
            // Bottom face
            writeSTLTriangle(writer, 
                new float[]{-w, -h, -d}, 
                new float[]{w, -h, -d}, 
                new float[]{-w, -h, d});
            writeSTLTriangle(writer, 
                new float[]{w, -h, -d}, 
                new float[]{w, -h, d}, 
                new float[]{-w, -h, d});
            
            writer.println("endsolid box");
        }
    }
    
    private static void writeSTLTriangle(java.io.PrintWriter writer, float[] v1, float[] v2, float[] v3) {
        // Calculate normal vector using cross product
        float[] normal = calculateNormal(v1, v2, v3);
        
        writer.println("  facet normal " + normal[0] + " " + normal[1] + " " + normal[2]);
        writer.println("    outer loop");
        writer.println("      vertex " + v1[0] + " " + v1[1] + " " + v1[2]);
        writer.println("      vertex " + v2[0] + " " + v2[1] + " " + v2[2]);
        writer.println("      vertex " + v3[0] + " " + v3[1] + " " + v3[2]);
        writer.println("    endloop");
        writer.println("  endfacet");
    }
    
    private static float[] calculateNormal(float[] v1, float[] v2, float[] v3) {
        // Calculate vectors from v1 to v2 and v1 to v3
        float[] a = {v2[0] - v1[0], v2[1] - v1[1], v2[2] - v1[2]};
        float[] b = {v3[0] - v1[0], v3[1] - v1[1], v3[2] - v1[2]};
        
        // Cross product
        float[] normal = {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
        
        // Normalize
        float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        if (length > 0) {
            normal[0] /= length;
            normal[1] /= length;
            normal[2] /= length;
        }
        
        return normal;
    }
    
    public static void exportMeshToOBJ(TriangleMesh mesh, String filename) throws IOException {
        File file = new File(filename);
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
            writer.println("# OBJ file created by LifeSimulation2");
            
            // Write vertices
            float[] points = mesh.getPoints().toArray(null);
            for (int i = 0; i < points.length; i += 3) {
                writer.println("v " + points[i] + " " + points[i+1] + " " + points[i+2]);
            }
            
            // Write texture coordinates
            float[] texCoords = mesh.getTexCoords().toArray(null);
            for (int i = 0; i < texCoords.length; i += 2) {
                writer.println("vt " + texCoords[i] + " " + texCoords[i+1]);
            }
            
            // Write faces
            int[] faces = mesh.getFaces().toArray(null);
            for (int i = 0; i < faces.length; i += 6) {
                // OBJ indices are 1-based
                writer.println("f " + 
                    (faces[i]+1) + "/" + (faces[i+1]+1) + " " + 
                    (faces[i+2]+1) + "/" + (faces[i+3]+1) + " " + 
                    (faces[i+4]+1) + "/" + (faces[i+5]+1));
            }
        }
    }

    @Override
    public void init() {
        instance = this;
    }
}