import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
//import javafx.animation.Animation;
//import javafx.animation.RotateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableFloatArray;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
//import javafx.scene.shape.Box;
//import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
//import javafx.util.Duration;
import javax.imageio.ImageIO;

public class LifeSimulation extends Application {
    private static int CHART_WIDTH = 500, CHART_HEIGHT = 800, CELL_SIZE = 8, max = 5, steps = 1024, MAX_LIFE = 4, MIN_SIZE = 1, SLEEP_TIME = 10;
    private static String OUTPUT_DIR = "simulation_results";
    private static final boolean AUTO_SAVE = true;
    private Landscape currentLandscape;
    private Canvas simulationCanvas;
    private boolean isSimulationRunning = false;
    private Thread visualSimulationThread;
    private Map<String, LineChart<Number, Number>> charts;
    private Map<String, MeshView> charts3D;
    private VBox mainLayout;
    
    public static void setOutputDirectory(String dir) {
        OUTPUT_DIR = dir;
    }

    private Canvas createSimulationCanvas(int width, int height) {
        Canvas canvas = new Canvas(width * CELL_SIZE, height * CELL_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.BLACK);
        return canvas;
    }

    private void drawLandscape(Canvas canvas, Landscape landscape) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int i = 0; i < landscape.getRows(); i++) {
            for (int j = 0; j < landscape.getCols(); j++) {
                gc.setFill(landscape.getCell(i, j).getAlive() ? Color.BLACK : Color.WHITE);
                gc.fillRect(j * CELL_SIZE, i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                gc.strokeRect(j * CELL_SIZE, i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
    }

    private void saveChart(LineChart<Number, Number> chart, String filename) {
        WritableImage image = chart.snapshot(null, null);
        File file = new File(filename);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("Saved chart to: " + file.getAbsolutePath());
        } 
        catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }

    private void saveSimulationState(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists())
            directory.mkdir();
        
        String filename = dirName + "/" + prefix + ".png";
        WritableImage image = simulationCanvas.snapshot(null, null);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(filename));
            System.out.println("Saved simulation state to: " + filename);
        }
        catch (IOException e) {
            System.err.println("Error saving simulation state: " + e.getMessage());
        }
        if (AUTO_SAVE) {
            saveAllCharts(charts);
            saveAll3DCharts(charts3D);
        }
    }

    private void saveAll3DCharts(Map<String, MeshView> charts3D) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists())
            directory.mkdir();
        
        for (Map.Entry<String, MeshView> entry : charts3D.entrySet()) {
            String baseFilename = dirName + "/" + entry.getKey();
            save3DChartToOBJ(entry.getValue(), baseFilename + ".obj");
            save3DChartToSTL(entry.getValue(), baseFilename + ".stl");
        }
    }

    private void saveAllCharts(Map<String, LineChart<Number, Number>> charts) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists())
            directory.mkdir();
        
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            String dimensions = entry.getKey().replace(',', 'x');
            String filename = dirName + "/landscape_" + dimensions + ".png";
            saveChart(entry.getValue(), filename);
        }
    }

    private void runVisualSimulation() {
        if (visualSimulationThread != null && visualSimulationThread.isAlive())
            return;

        visualSimulationThread = new Thread(() -> {
            isSimulationRunning = true;
            
            // Save initial state
            Platform.runLater(() -> {
                drawLandscape(simulationCanvas, currentLandscape);
                saveSimulationState("initial_state");
            });
            // Run one step and save
            currentLandscape.advance();
            Platform.runLater(() -> {
                drawLandscape(simulationCanvas, currentLandscape);
                saveSimulationState("after_one_step");
            });
            // Continue simulation
            for (int i = 2; i < steps && isSimulationRunning; i++) {
                currentLandscape.advance();
                Platform.runLater(() -> drawLandscape(simulationCanvas, currentLandscape));
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                    break;
                }
            }
            isSimulationRunning = false;
        });
        visualSimulationThread.setDaemon(true);
        visualSimulationThread.start();
    }

    @Override
    public void start(Stage stage) {
        currentLandscape = new Landscape(max, max, 0.5);
        simulationCanvas = createSimulationCanvas(max, max);
        charts = new HashMap<>();
        charts3D = new HashMap<>();

        // Create simulation controls
        Button startSimButton = new Button("Start Simulation");
        Button stopSimButton = new Button("Stop Simulation");
        Button resetSimButton = new Button("Reset Simulation");
        Button saveButton = new Button("Save All");

        startSimButton.setOnAction(_ -> runVisualSimulation());
        stopSimButton.setOnAction(_ -> isSimulationRunning = false);
        resetSimButton.setOnAction(_ -> {
            isSimulationRunning = false;
            currentLandscape = new Landscape(max, max, 0.5);
            drawLandscape(simulationCanvas, currentLandscape);
        });
        saveButton.setOnAction(_ -> {
            saveAllCharts(charts);
            saveSimulationState("current_state");
            saveAll3DCharts(charts3D);
        });

        // Create control panel
        HBox controlPane = new HBox(10);
        controlPane.getChildren().addAll(startSimButton, stopSimButton, resetSimButton, saveButton);
        controlPane.setPadding(new javafx.geometry.Insets(5));

        // Create simulation panel
        VBox simulationPane = new VBox(10);
        simulationPane.getChildren().addAll(simulationCanvas, controlPane);
        simulationPane.setPadding(new javafx.geometry.Insets(10));

        // Create charts
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new javafx.geometry.Insets(10));

        // Create all possible combinations of charts
        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                final NumberAxis xAxis = new NumberAxis(0, 1.0, 0.05);
                final NumberAxis yAxis = new NumberAxis(0, MAX_LIFE, 1);
                xAxis.setLabel("Chance");
                yAxis.setLabel("Average Living Cells");

                LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
                chart.setTitle(m + "x" + n + " Landscape");
                chart.setCreateSymbols(false);
                chart.setPrefSize(CHART_WIDTH, CHART_HEIGHT);

                String key = m + "," + n;
                charts.put(key, chart);
                gridPane.add(chart, n-1, m-1);
            }
        }
        // Create ScrollPane for charts
        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // Create main layout combining simulation and charts
        mainLayout = new VBox(20);
        mainLayout.getChildren().addAll(simulationPane, scrollPane);

        startChartsSimulation();

        // Create scene with dynamic size
        int sceneWidth = (int) Math.max(CHART_WIDTH * max, simulationCanvas.getWidth() + 40);
        int sceneHeight = Math.min(CHART_HEIGHT * max + (int)simulationCanvas.getHeight() + 100, 1000);
        Scene scene = new Scene(mainLayout, sceneWidth, sceneHeight);

        // Enable depth buffer
        scene.setCamera(new PerspectiveCamera());
        
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setTitle("Life Simulation - Visualization and Analysis");
        stage.show();

        // Initial draw of the landscape
        drawLandscape(simulationCanvas, currentLandscape);
    }

    private void startChartsSimulation() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Map<String, XYChart.Series<Number, Number>> series = new HashMap<>();
                    Group root3D = new Group();
                    
                    // Add lighting for better 3D visibility
                    AmbientLight ambient = new AmbientLight(Color.WHITE);
                    PointLight light = new PointLight(Color.WHITE);
                    light.setTranslateX(2000);
                    light.setTranslateY(-2000);
                    light.setTranslateZ(-3000);
                    root3D.getChildren().addAll(ambient, light);
                    
                    // Initialize 2D series for each combination
                    for (int m = MIN_SIZE; m <= max; m++) {
                        for (int n = MIN_SIZE; n <= m; n++) {
                            String key = m + "," + n;
                            XYChart.Series<Number, Number> newSeries = new XYChart.Series<>();
                            newSeries.setName(m + "x" + n);
                            series.put(key, newSeries);
                            
                            Platform.runLater(() -> {
                                charts.get(key).getData().add(series.get(key));
                            });
                        }
                    }

                    // Collect data for both 2D and 3D visualizations
                    Map<String, Map<Double, double[]>> allData = new HashMap<>();
                    
                    // For each grid size combination
                    for (int m = MIN_SIZE; m <= max; m++) {
                        for (int n = MIN_SIZE; n <= m; n++) {
                            String key = m + "," + n;
                            Map<Double, double[]> chanceData = new HashMap<>();
                            allData.put(key, chanceData);
                            
                            // For each chance value
                            for (int i = 0; i <= 20; i++) {
                                double chance = i * 0.05;
                                long totalLivingCells = 0;
                                int r = 2 * m * n;
                                
                                // Run simulations
                                for (int rep = 0; rep < r; rep++) {
                                    Landscape landscape = new Landscape(m, n, chance);
                                    for (int step = 0; step < steps; step++) {
                                        landscape.advance();
                                        totalLivingCells += landscape.countLivingCells();
                                    }
                                }
                                
                                final double averageLivingCells = totalLivingCells / ((double)r * steps);
                                chanceData.put(chance, new double[]{averageLivingCells});
                                
                                // Update 2D charts
                                final double finalChance = chance;
                                Platform.runLater(() -> {
                                    series.get(key).getData().add(
                                        new XYChart.Data<>(finalChance, averageLivingCells)
                                    );
                                });
                            }
                        }
                    }

                    createSurfaceVisualization(root3D, allData);

                    // Create and add 3D scene
                    Platform.runLater(() -> {
                        try {
                            Stage stage3D = new Stage();
                            stage3D.setTitle("3D Visualizations");

                            // Set up camera
                            PerspectiveCamera camera = new PerspectiveCamera(true);
                            camera.setTranslateX(2000);
                            camera.setTranslateY(-1000);
                            camera.setTranslateZ(-4000);
                            camera.setRotationAxis(Rotate.X_AXIS);
                            camera.setRotate(-20);

                            // Create scene with the root3D group
                            Scene scene3D = new Scene(root3D, 1600, 1200, true, SceneAntialiasing.BALANCED);
                            scene3D.setFill(Color.LIGHTGRAY);
                            scene3D.setCamera(camera);

                            // Add mouse rotation handling
                            final double[] xRotate = {0}, yRotate = {0};
                            final double[] lastX = {0}, lastY = {0};

                            scene3D.setOnMousePressed(event -> {
                                lastX[0] = event.getSceneX();
                                lastY[0] = event.getSceneY();
                            });

                            scene3D.setOnMouseDragged(event -> {
                                double dx = (event.getSceneX() - lastX[0]) * 0.5;
                                double dy = (event.getSceneY() - lastY[0]) * 0.5;
                                
                                xRotate[0] += dy;
                                yRotate[0] -= dx;

                                root3D.getTransforms().clear();
                                root3D.getTransforms().addAll(
                                    new Rotate(xRotate[0], Rotate.X_AXIS),
                                    new Rotate(yRotate[0], Rotate.Y_AXIS)
                                );

                                lastX[0] = event.getSceneX();
                                lastY[0] = event.getSceneY();
                            });

                            // Add zoom handling with mouse wheel
                            scene3D.setOnScroll(event -> {
                                double delta = event.getDeltaY() * 0.5;
                                root3D.setTranslateZ(root3D.getTranslateZ() + delta);
                            });

                            stage3D.setScene(scene3D);
                            stage3D.show();

                        } catch (Exception e) {
                            System.err.println("Error creating 3D visualization window: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
                catch (Exception e) {
                    System.err.println("Error in charts simulation: " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }
        };
        Thread simulationThread = new Thread(task);
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    private void createSurfaceVisualization(Group root3D, Map<String, Map<Double, double[]>> allData) {
        TriangleMesh mesh = new TriangleMesh();
        ObservableFloatArray points = mesh.getPoints();
        ObservableFaceArray faces = mesh.getFaces();

        int gridSize = max - MIN_SIZE + 1;
        float[] vertices = new float[gridSize * gridSize * 3];
        int vertexIndex = 0;

        // Populate vertices (x, y, z)
        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                double avgLivingCells = allData.get(m + "," + n).values().stream()
                    .mapToDouble(data -> data[0])
                    .average()
                    .orElse(0.0);

                vertices[vertexIndex++] = (m - MIN_SIZE) * 100f; // x-coordinate
                vertices[vertexIndex++] = (n - MIN_SIZE) * 100f; // y-coordinate
                vertices[vertexIndex++] = (float) (avgLivingCells * 10); // z-coordinate (scaled)
            }
        }
        points.addAll(vertices);

        // Populate faces (triangles)
        for (int i = 0; i < gridSize - 1; i++) {
            for (int j = 0; j < gridSize - 1; j++) {
                int p00 = i * gridSize + j;
                int p10 = p00 + 1;
                int p01 = p00 + gridSize;
                int p11 = p01 + 1;

                // First triangle
                faces.addAll(p00, 0, p10, 0, p01, 0);
                // Second triangle
                faces.addAll(p10, 0, p11, 0, p01, 0);
            }
        }

        MeshView meshView = new MeshView(mesh);
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(Color.BLUE);
        material.setSpecularColor(Color.WHITE);
        meshView.setMaterial(material);
        root3D.getChildren().add(meshView);
    }

    private void saveAsOBJ(TriangleMesh mesh, String filename) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            ObservableFloatArray points = mesh.getPoints();
            ObservableFaceArray faces = mesh.getFaces();

            // Write vertices
            for (int i = 0; i < points.size(); i += 3) {
                writer.write(String.format("v %.6f %.6f %.6f%n", 
                    points.get(i), points.get(i + 1), points.get(i + 2)));
            }

            // Write faces (OBJ uses 1-based indexing)
            for (int i = 0; i < faces.size(); i += 3) {
                writer.write(String.format("f %d %d %d%n", 
                    faces.get(i) + 1, faces.get(i + 1) + 1, faces.get(i + 2) + 1));
            }

            System.out.println("Saved OBJ model to: " + filename);
        } catch (IOException ex) {
            System.err.println("Error saving 3D chart to OBJ: " + ex.getMessage());
        }
    }

    private void save3DChartToOBJ(MeshView meshView, String filename) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            TriangleMesh mesh = (TriangleMesh) meshView.getMesh();
            saveAsOBJ(mesh, filename);
            float[] points = getMeshPoints(mesh);
            int[] faces = getMeshFaces(mesh);
    
            writeVertices(writer, points);
            writeFaces(writer, faces);
        } catch (IOException e) {
            System.err.println("Error saving 3D chart to OBJ: " + e.getMessage());
        }
    }
    
    private void save3DChartToSTL(MeshView meshView, String filename) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            TriangleMesh mesh = (TriangleMesh) meshView.getMesh();
            float[] points = getMeshPoints(mesh);
            int[] faces = getMeshFaces(mesh);
    
            writer.println("solid " + filename);
            writeTriangles(writer, points, faces);
            writer.println("endsolid " + filename);
        }
        catch (IOException e) {
            System.err.println("Error saving 3D chart to STL: " + e.getMessage());
        }
    }
    
    private float[] getMeshPoints(TriangleMesh mesh) {
        float[] points = new float[mesh.getPoints().size()];
        mesh.getPoints().toArray(points);
        return points;
    }
    
    private int[] getMeshFaces(TriangleMesh mesh) {
        int[] faces = new int[mesh.getFaces().size()];
        mesh.getFaces().toArray(faces);
        return faces;
    }
    
    private void writeVertices(PrintWriter writer, float[] points) {
        for (int i = 0; i < points.length; i += 3)
            writer.printf("v %f %f %f\n", points[i], points[i + 1], points[i + 2]);
    }
    
    //OBJ format
    private void writeFaces(PrintWriter writer, int[] faces) {
        for (int i = 0; i < faces.length; i += 6) {
            writer.printf("f %d %d %d\n", 
                faces[i] + 1, 
                faces[i + 2] + 1, 
                faces[i + 4] + 1);
        }
    }
    
    // STL format
    private void writeTriangles(PrintWriter writer, float[] points, int[] faces) {
        for (int i = 0; i < faces.length; i += 6) {
            int v1 = faces[i] * 3;
            int v2 = faces[i + 2] * 3;
            int v3 = faces[i + 4] * 3;
    
            writer.println("  facet normal 0 0 0");
            writer.println("    outer loop");
            writer.printf("      vertex %f %f %f\n", points[v1], points[v1 + 1], points[v1 + 2]);
            writer.printf("      vertex %f %f %f\n", points[v2], points[v2 + 1], points[v2 + 2]);
            writer.printf("      vertex %f %f %f\n", points[v3], points[v3 + 1], points[v3 + 2]);
            writer.println("    endloop");
            writer.println("  endfacet");
        }
    }

    public static void main(String[] args) {
        if (args.length >= 2) {        
            max = Integer.parseInt(args[0]);
            steps = Integer.parseInt(args[1]);
        
            if (args.length >= 3) 
                setOutputDirectory(args[2]);
        }
        launch(args);
    }
}