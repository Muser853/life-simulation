import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class LandscapeDisplay extends Application {
    private Landscape scape;
    private Canvas canvas;
    private int gridScale;

    @Override
    public void start(Stage primaryStage) {
        scape = new Landscape(100, 100, 0.25);
        gridScale = 6;
        
        canvas = new Canvas(scape.getCols() * gridScale, scape.getRows() * gridScale);
        
        Scene scene = new Scene(new BorderPane(canvas), canvas.getWidth(), canvas.getHeight());
        primaryStage.setTitle("Game of Life");
        primaryStage.setScene(scene);
        primaryStage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                scape.advance();
                render();
            }
        }.start();
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        scape.draw(gc, gridScale);
    }

    public void saveImage(String filename) {
        WritableImage snapshot = canvas.snapshot(null, null);
        try {
            ImageIO.write(
                SwingFXUtils.fromFXImage(snapshot, null), 
                "png", 
                new File(filename)
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}