import java.util.List;
public class Cell {
    private boolean alive;
    private int lower, low, up, upper;

    public Cell() {this.alive = false;}
    public Cell(boolean alive) {this.alive = alive;}
    public int getLower() {return lower;}
    public int getLow() {return low;}
    public int getUp() {return up;}
    public int getUpper() {return upper;}
    public boolean getAlive() {return alive;}
    public void setAlive(boolean alive) {this.alive = alive;}

    public void setBounds(int lower, int low, int up, int upper) {
        this.lower = lower;
        this.low = low;
        this.up = up;
        this.upper = upper;
    }

    public void updateLower(int lower) {
        this.lower = lower;
    }
    public void updateLow(int low) {
        this.low = low;
    }
    public void updateUp(int up) {
        this.up = up;
    }
    public void updateUpper(int upper) {
        this.upper = upper;
    }
    
    @Override
    public String toString() {
        return alive ? "0" : "1"; // "0" for alive, "1" for dead
    }
    // Updates the Cell's state based on neighbors
    public void updateState(List<Cell> neighbors) {
        int liveNeighbors = (int) neighbors.stream().filter(Cell::getAlive).count();
        if (alive) {
            // Cell remains alive if liveNeighbors is within [lower, upper]
            alive = (liveNeighbors >= lower && liveNeighbors <= upper);
        } else {
            // Cell becomes alive if liveNeighbors is within [low, up]
            alive = (liveNeighbors >= low && liveNeighbors <= up);
        }
    }
    // Main method for testing
    public static void main(String[] args) {
        Cell cell1 = new Cell();
        Cell cell2 = new Cell(true);
        System.out.println(cell1); // Should print "1"
        System.out.println(cell2); // Should print "0"
    }
}