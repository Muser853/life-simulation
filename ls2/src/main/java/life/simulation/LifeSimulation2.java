import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class LifeSimulation2 extends Application {
    private static final int CHART_WIDTH = 500, CHART_HEIGHT = 800, CELL_SIZE = 16;
    private static String OUTPUT_DIR = "simulation_results";
    private static int max = 9, lower, low, up, upper;
    private Label lowerLabel, lowLabel, upLabel, upperLabel, cellCountLabel;
    private Slider lowerSlider, lowSlider, upSlider, upperSlider, cellCountSlider;
    private ComboBox<String> gridTypeCombo;
    private Map<String, LineChart<Number, Number>> charts;
    private Landscape2 currentLandscape;

    @Override
    public void start(Stage stage) {
        initializeSimulationParameters();
        setupUIComponents(stage);
        runSimulations();
    }

    private void initializeSimulationParameters() {
        lower = 2;
        low = 3;
        up = 3;
        upper = 3;
        currentLandscape = new Landscape2(max, max, max, 0.5, lower, low, up, upper, Landscape2.GridType.SQUARE, 3);
        charts = new HashMap<>();
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
        gridTypeCombo.setOnAction(_ -> resetSimulationWithGridType(getSelectedGridType()));
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

    private Slider createSlider(double max, double initialValue, Consumer<Double> onChange) {
        Slider slider = new Slider(0, max, initialValue);
        slider.valueProperty().addListener((_, _, newVal) -> onChange.accept(newVal.doubleValue()));
        return slider;
    }

    private void updateLower(double value) {
        lower = (int) value;
        lowerLabel.setText("Lower: " + lower);
        resetSimulation();
    }

    private void updateLow(double value) {
        low = (int) value;
        lowLabel.setText("Low: " + low);
        resetSimulation();
    }

    private void updateUp(double value) {
        up = (int) value;
        upLabel.setText("Up: " + up);
        resetSimulation();
    }

    private void updateUpper(double value) {
        upper = (int) value;
        upperLabel.setText("Upper: " + upper);
        resetSimulation();
    }

    private void updateCellCount(double value) {
        cellCountLabel.setText("Cell Count: " + (int) value);
        resetSimulation();
    }

    private void resetSimulation() {
        currentLandscape = new Landscape2(max, max, max, 0.5, lower, low, up, upper, getSelectedGridType(), 3);
        initializeCells((int) cellCountSlider.getValue());
    }

    private void resetSimulationWithGridType(Landscape2.GridType gridType) {
        currentLandscape = new Landscape2(max, max, max, 0.5, lower, low, up, upper, gridType, 3);
        initializeCells((int) cellCountSlider.getValue());
    }

    private void initializeCells(int cellCount) {
        Random rand = new Random();
        for (int i = 0; i < cellCount; i++) {
            int row = rand.nextInt(max);
            int col = rand.nextInt(max);
            int depth = rand.nextInt(max);
            currentLandscape.getCell(row, col, depth).setAlive(true);
        }
    }

    private Landscape2.GridType getSelectedGridType() {
        String type = gridTypeCombo.getValue();
        return switch (type) {
            case "Triangle" -> Landscape2.GridType.TRIANGLE;
            case "Hexagon" -> Landscape2.GridType.HEXAGON;
            default -> Landscape2.GridType.SQUARE;
        };
    }

    private void runSimulations() {
        for (int width = 1; width <= max; width++) {
            for (int height = 1; height <= max; height++) {
                for (int depth = 1; depth <= max; depth++) {
                    int volume = width * height * depth;
                    int halfCubeRoot = (int) Math.cbrt(volume) / 2;
                    for (int lowValue = 1; lowValue <= halfCubeRoot; lowValue++) {
                        for (int upValue = 1; upValue <= halfCubeRoot; upValue++) {
                            for (int upperValue = 1; upperValue <= halfCubeRoot; upperValue++) {
                                String key = String.format(
                                    "Width: %d, Height: %d, Depth: %d, Low: %d, Up: %d, Upper: %d",
                                    width, height, depth, lowValue, upValue, upperValue
                                );
                                LineChart<Number, Number> chart = createChart(key);
                                charts.put(key, chart);
                            }
                        }
                    }
                }
            }
        }
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

    private void saveChartAsSvg(LineChart<Number, Number> chart, String key) {
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
                svgContent.append("\" stroke=\"black\" stroke-width=\"2\" fill=\"none\" />\n");
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
                Cell cell = currentLandscape.getCell(row, col, 0);
                if (cell.getAlive()) gc.setFill(Color.BLACK);
                else gc.setFill(Color.WHITE);
                gc.fillRect(col * CELL_SIZE, row * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
        VBox layout = (VBox) stage.getScene().getRoot();
        layout.getChildren().add(canvas);
        try {Thread.sleep(256);}catch(InterruptedException _){}
    }

    private void startSimulation() {
        new Thread(() -> {
            int maxIterations = 1000;
            int minIterations = Integer.MAX_VALUE;
            int maxIterationsRecorded = 0;
            int totalIterations = 0;
            int validRuns = 0;

            for (int i = 0; i < maxIterations; i++) {
                int iterations = 0;
                while (currentLandscape.countLivingCells() > 0 && iterations < maxIterations) {
                    currentLandscape.advance();
                    iterations++;
                    draw2DLandscape();
                }
                if (iterations < maxIterations) {
                    minIterations = Math.min(minIterations, iterations);
                    maxIterationsRecorded = Math.max(maxIterationsRecorded, iterations);
                    totalIterations += iterations;
                    validRuns++;
                }
                resetSimulation();
            }
            double averageIterations = (double) totalIterations / validRuns;
            updateCharts(minIterations, maxIterationsRecorded, averageIterations);
        }).start();
    }

    private LineChart<Number, Number> createChart(String title) {
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