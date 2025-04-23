import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

public class LifeSimulation2 extends Application {
    private static final int CHART_WIDTH = 500, CHART_HEIGHT = 800, CELL_SIZE = 16, SIDE_LENGTH = 9, MAX_ITERATIONS = 1000, max = 9;
    private static final String OUTPUT_DIR = "simulation_results";

    private static ComboBox<String> gridTypeCombo;

    private int lower, low, up, upper, cellCount;
    private Label lowerLabel, lowLabel, upLabel, upperLabel, cellCountLabel;
    private Slider lowerSlider, lowSlider, upSlider, upperSlider, cellCountSlider;
    private Map<String, LineChart<Number, Number>> charts;
    private Landscape2 currentLandscape;

    @Override
    public void start(Stage stage) {
        initialDemo();
        setupUIComponents(stage);
        runSimulations();
    }
    private void initialDemo() {
        this.lower = 1;
        this.low = 2;
        this.up = 4;
        this.upper = 6;
        this.cellCount = 50;
        currentLandscape = new Landscape2(max, max);
        charts = new HashMap<>();
    }
    private void updateLower(int value) {
        currentLandscape.updateLower(value);
        lowerLabel.setText("Lower: " + lower);
    }
    private void updateLow(int value) {
        currentLandscape.updateLow(value);
        lowLabel.setText("Low: " + value);
    }
    private void updateUp(int value) {
        currentLandscape.updateUp(value);
        upLabel.setText("Up: " + value);
    }
    private void updateUpper(int value) {
        currentLandscape.updateUpper(value);
        upperLabel.setText("Upper: " + value);
    }
    private void resetSimulation() {
        Landscape2.GridType gridType = getSelectedGridType();
        currentLandscape = new Landscape2(max, max, max, gridType, 3);
        currentLandscape.initializeLandscape(lower, low, up, upper); // Set thresholds
        currentLandscape.setInitialLivingCells(cellCount); // Set initial cell count
    }
    private void resetSimulationWithGridType(Landscape2.GridType gridType) {
        currentLandscape = new Landscape2(max, max, max, gridType, 3);
        currentLandscape.initializeLandscape(lower, low, up, upper); // Pass parameters
    }
    private void setupUIComponents(Stage stage) {
        lowerSlider = createSlider(10, lower, this::updateLower);
        lowSlider = createSlider(10, low, this::updateLow);
        upSlider = createSlider(10, up, this::updateUp);
        upperSlider = createSlider(10, upper, this::updateUpper);
        cellCountSlider = createSlider(100, 50, this::updateCellCount);
        gridTypeCombo = new ComboBox<>();
        gridTypeCombo.getItems().addAll("Square", "Triangle", "Hexagon");
        gridTypeCombo.setValue("Square");
        gridTypeCombo.setOnAction(_ -> {
            resetSimulationWithGridType(getSelectedGridType());
            resetSimulation(); // Ensure cell count and thresholds are applied
        });
        lowerLabel = new Label("Lower: " + lower);
        lowLabel = new Label("Low: " + low);
        upLabel = new Label("Up: " + up);
        upperLabel = new Label("Upper: " + upper);
        cellCountLabel = new Label("Cell Count: " + (int) cellCountSlider.getValue());
        Button startSimulationButton = new Button("Start Simulation");
        startSimulationButton.setOnAction(_ -> startSimulation());
        HBox controlPanel = new HBox(10);
        controlPanel.getChildren().addAll(
                lowerLabel, lowerSlider, lowLabel, lowSlider, upLabel, upSlider, upperLabel, upperSlider,
                cellCountLabel, cellCountSlider, gridTypeCombo, startSimulationButton
        );
        VBox layout = new VBox(controlPanel);
        for (LineChart<Number, Number> chart : charts.values()) layout.getChildren().add(chart);
        Scene scene = new Scene(layout, CHART_WIDTH, CHART_HEIGHT);
        stage.setTitle("Life Simulation - 3D Visualization and Analysis");
        stage.setScene(scene);
        stage.show();
    }
    private Slider createSlider(int max, int initialValue, Consumer<Integer> onChange) {
        Slider slider = new Slider(0, max, initialValue);
        slider.valueProperty().addListener((_, _, newVal) -> onChange.accept(newVal.intValue()));
        return slider;
    }
    public void runSimulations() {
        ExecutorService executor = Executors.newWorkStealingPool();
        for (int width = 1; width <= SIDE_LENGTH; width++) {
            for (int height = 1; height <= SIDE_LENGTH; height++) {
                for (Landscape2.GridType gridType : Landscape2.GridType.values()) {
                    Landscape2 scape = new Landscape2(width, height, 1, gridType, 3);
                    executor.submit(() -> {
                        simulateAllCombinations(scape);
                    });
                }
            }
        }
        for (int width = 1; width <= SIDE_LENGTH; width++) {
            for (int height = 1; height <= SIDE_LENGTH; height++) {
                for (int depth = 1; depth <= SIDE_LENGTH; depth++) {
                    for (Landscape2.GridType gridType : Landscape2.GridType.values()) {
                        Landscape2 scape = new Landscape2(width, height, depth, gridType, 3);
                        executor.submit(() -> {
                            simulateAllCombinations(scape);
                        });
                    }
                }
            }
        }
        executor.shutdown();
    }
    
    private static int[] runSimulationWithCellCount(Landscape2 scape, int totalCells) {
        int min = 4096, max = 0, total = 0, validRuns = 0;
        scape.setInitialLivingCells(totalCells);
        int iterations;
        for (iterations = 0; iterations < MAX_ITERATIONS; iterations++) {
            if (scape.countLivingCells() == 0) break;
            scape.advance();
        }
        if (iterations < MAX_ITERATIONS) {
            min = Math.min(min, iterations);
            max = Math.max(max, iterations);
            total += iterations;
            validRuns++;
        }
        return new int[]{min, max, validRuns > 0 ? total / validRuns : 0};
    }

    private static void simulateAllCombinations(Landscape2 scape) {
        final int maxParam = (int) Math.cbrt(scape.getRows() * scape.getCols() * scape.getDepth()) / 2;
        for (int lower = 1; lower <= maxParam; lower++) {
            for (int low = lower + 1; low <= maxParam; low++) {
                for (int up = low + 1; up <= maxParam; up++) {
                    for (int upper = up + 1; upper <= maxParam; upper++) {
                        scape.initializeLandscape(lower, low, up, upper); // Set thresholds
                        Map<Integer, int[]> data = new HashMap<>();
                        for (int totalCells = 1; totalCells < scape.getRows() * scape.getCols() * scape.getDepth(); totalCells++) {
                            scape.setInitialLivingCells(totalCells); // Set initial cell count
                            int[] stats = runSimulationWithCellCount(scape, totalCells);
                            data.put(totalCells, stats);
                        }
                        saveChart(data, scape, lower, low, up, upper);
                    }
                }
            }
        }
    }
    private static void saveChart(Map<Integer, int[]> data, Landscape2 scape, int lower, int low, int up, int upper) {
        try {
            LineChart<Number, Number> chart = createChart(String.format(
                "W=%d H=%d D=%d Lower=%d Low=%d Up=%d Upper=%d",
                scape.getRows(), scape.getCols(), scape.getDepth(), lower, low, up, upper)
            );
            XYChart.Series<Number, Number> minSeries = new XYChart.Series<>();
            XYChart.Series<Number, Number> maxSeries = new XYChart.Series<>();
            XYChart.Series<Number, Number> avgSeries = new XYChart.Series<>();
            data.forEach((totalCells, stats) -> {
                minSeries.getData().add(new XYChart.Data<>(totalCells, stats[0]));
                maxSeries.getData().add(new XYChart.Data<>(totalCells, stats[1]));
                avgSeries.getData().add(new XYChart.Data<>(totalCells, stats[2]));
            });
            String gridType = scape.getGridType().name().toLowerCase();
            Path outputPath = Paths.get(OUTPUT_DIR, gridType);
            Files.createDirectories(outputPath);
            saveChartAsSvg(chart, chart.getTitle());    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void updateCellCount(double value) {
        this.cellCount = (int) value; // Set instance variable
        cellCountLabel.setText("Cell Count: " + cellCount);
        resetSimulation(); // Trigger reset with new cell count
    }
    private Landscape2.GridType getSelectedGridType() {
        String type = gridTypeCombo.getValue();
        return switch (type) {
            case "Triangle" -> Landscape2.GridType.TRIANGLE;
            case "Hexagon" -> Landscape2.GridType.HEXAGON;
            default -> Landscape2.GridType.SQUARE;
        };
    }
    @SuppressWarnings("unchecked")
    private void updateCharts(int minIterations, int maxIterations, double averageIterations) {
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            String key = entry.getKey();
            LineChart<Number, Number> chart = entry.getValue();
            chart.getData().clear();
            XYChart.Series<Number, Number> minSeries = new XYChart.Series<>();
            minSeries.setName("Min Iterations");
            minSeries.getData().add(new XYChart.Data<>(currentLandscape.countLivingCells(), minIterations));
            XYChart.Series<Number, Number> maxSeries = new XYChart.Series<>();
            maxSeries.setName("Max Iterations");
            maxSeries.getData().add(new XYChart.Data<>(currentLandscape.countLivingCells(), maxIterations));
            XYChart.Series<Number, Number> avgSeries = new XYChart.Series<>();
            avgSeries.setName("Average Iterations");
            avgSeries.getData().add(new XYChart.Data<>(currentLandscape.countLivingCells(), averageIterations));
            chart.getData().addAll(minSeries, maxSeries, avgSeries);
            saveChartAsSvg(chart, key);
        }
    }
    private static void saveChartAsSvg(LineChart<Number, Number> chart, String key) {
        String gridType = gridTypeCombo.getValue().toLowerCase();
        File dir = new File(OUTPUT_DIR + "/" + gridType);
        if (!dir.exists()) dir.mkdirs();
        String fileName = key.replaceAll("[^a-zA-Z0-9]", "_") + ".svg";
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            StringBuilder svgContent = new StringBuilder();
            svgContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
            svgContent.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(CHART_WIDTH).append("\" height=\"").append(CHART_HEIGHT).append("\">\n");
            // Add chart title
            svgContent.append("<text x=\"10\" y=\"20\" font-family=\"Arial\" font-size=\"14\" fill=\"black\">").append(chart.getTitle()).append("</text>\n");
            // Add X and Y axis labels
            svgContent.append("<text x=\"").append(CHART_WIDTH / 2).append("\" y=\"").append(CHART_HEIGHT - 10).append("\" font-family=\"Arial\" font-size=\"12\" fill=\"black\" text-anchor=\"middle\">").append(chart.getXAxis().getLabel()).append("</text>\n");
            svgContent.append("<text x=\"10\" y=\"").append(CHART_HEIGHT / 2).append("\" font-family=\"Arial\" font-size=\"12\" fill=\"black\" transform=\"rotate(-90, 10, ").append(CHART_HEIGHT / 2).append(")\" text-anchor=\"middle\">").append(chart.getYAxis().getLabel()).append("</text>\n");
            int colorIndex = 0;
            String[] colors = {"red", "blue", "green"}; // Unique colors for each series
            for (XYChart.Series<Number, Number> series : chart.getData()) {
                svgContent.append("<path d=\"");    // Add data series
                boolean firstPoint = true;
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    double x = chart.getXAxis().getDisplayPosition(data.getXValue());
                    double y = chart.getYAxis().getDisplayPosition(data.getYValue());
                    if (firstPoint) {
                        svgContent.append("M").append(x).append(",").append(y);
                        firstPoint = false;
                    } else {
                        svgContent.append(" L").append(x).append(",").append(y);
                    }
                }
                svgContent.append("\" stroke=\"").append(colors[colorIndex % colors.length]).append("\" stroke-width=\"2\" fill=\"none\" />\n");
                colorIndex++;
            }
            svgContent.append("</svg>");
            fos.write(svgContent.toString().getBytes());
        } catch (IOException _) {}
    }
    private void draw2DLandscape() {
        Canvas canvas = new Canvas(CHART_WIDTH / 2, CHART_HEIGHT / 2);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (int row = 0; row < max; row++) {
            for (int col = 0; col < max; col++) {
                List<Integer> coords = new ArrayList<>();
                coords.add(row);
                coords.add(col);
                coords.add(0); // Fixed z-coordinate for 2D view
                Cell cell = currentLandscape.getCell(coords);
                if (cell == null) {
                    gc.setFill(Color.WHITE);
                } else {
                    gc.setFill(cell.getAlive() ? Color.BLACK : Color.WHITE);
                }
                gc.fillRect(col * CELL_SIZE, row * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
        Stage stage = (Stage) canvas.getScene().getWindow();stage.setTitle("2D Landscape Simulation");
        stage.show();
        VBox layout = (VBox) stage.getScene().getRoot();
        layout.getChildren().add(canvas);
        try {Thread.sleep(256);}catch(InterruptedException _){}
    }
    private void startSimulation() {
        new Thread(() -> {
            // Replace primitive variables with array elements
            int[] minIterationsHolder = {4096};
            int[] maxIterationsRecordedHolder = {0};
            int[] totalIterationsHolder = {0};
            int[] validRunsHolder = {0};
            for (int i = 0; i < 4096; i++) {
                int iterations = 0;
                while (currentLandscape.countLivingCells() > 0 && iterations < 4096) {
                    currentLandscape.advance();
                    iterations++;
                    draw2DLandscape();
                    try {
                        Thread.sleep(256);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (iterations < 4096) {
                    minIterationsHolder[0] = Math.min(minIterationsHolder[0], iterations);
                    maxIterationsRecordedHolder[0] = Math.max(maxIterationsRecordedHolder[0], iterations);
                    totalIterationsHolder[0] += iterations;
                    validRunsHolder[0]++;
                }
                resetSimulation();
            }
            double averageIterations = (double) totalIterationsHolder[0] / validRunsHolder[0];
            Platform.runLater(() -> updateCharts(
                minIterationsHolder[0],
                maxIterationsRecordedHolder[0],
                averageIterations
            ));
        }).start();
    }
    private static LineChart<Number, Number> createChart(String title) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Number of Social Agents");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Iterations");
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        return chart;
    }
    public static void main(String[] args) {launch(args);}
}