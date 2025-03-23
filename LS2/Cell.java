import java.util.ArrayList;
public class Cell {
    private boolean alive;

    // Default constructor
    public Cell() {this.alive = false;}
    // Constructor with alive state
    public Cell(boolean alive) {this.alive = alive;}
    // Returns whether the Cell is alive
    public boolean getAlive() {return alive;}
    // Sets the Cell's state
    public void setAlive(boolean alive) {this.alive = alive;}
    // Returns a string representation of the Cell's state
    @Override
    public String toString() {
        return alive ? "0" : "1"; // "0" for alive, "1" for dead
    }
    // Updates the Cell's state based on neighbors
    public void updateState(ArrayList<Cell> neighbors, int lower, int low, int up, int upper) {
        int liveNeighbors = 0;
        for (Cell neighbor : neighbors) {
            if (neighbor.getAlive())
                liveNeighbors++;
        }
        if (alive)
            alive = (liveNeighbors >= lower && liveNeighbors <= upper);
        else
            alive = (liveNeighbors >= low && liveNeighbors <= up);    
    }
    // Main method for testing
    public static void main(String[] args) {
        Cell cell1 = new Cell();
        Cell cell2 = new Cell(true);
        System.out.println(cell1); // Should print "1"
        System.out.println(cell2); // Should print "0"
    }
}
