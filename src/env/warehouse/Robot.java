package warehouse;

/**
 * Represents a warehouse robot.
 */
public class Robot {
    private final String id;
    private final String type; // "light", "medium", "heavy"
    private final double maxWeight;
    private final int maxWidth;
    private final int maxHeight;
    private final int speed; // speed: high=3, medium=2, low=1
    private int x, y;
    private Container carriedContainer;
    private boolean busy;
    private String currentTask;
    
    public Robot(String id, String type, double maxWeight, int maxWidth, int maxHeight, int speed) {
        this.id = id;
        this.type = type;
        this.maxWeight = maxWeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.speed = speed;
        this.x = 0;
        this.y = 0;
        this.carriedContainer = null;
        this.busy = false;
        this.currentTask = null;
    }
    
    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public double getMaxWeight() { return maxWeight; }
    public int getMaxWidth() { return maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    public int getSpeed() { return speed; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Container getCarriedContainer() { return carriedContainer; }
    public boolean isBusy() { return busy; }
    public String getCurrentTask() { return currentTask; }
    
    // Setters
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setBusy(boolean busy) { this.busy = busy; }
    public void setCurrentTask(String task) { this.currentTask = task; }
    
    /**
     * Checks whether the robot can carry a container.
     */
    public boolean canCarry(Container container) {
        return container.getWeight() <= maxWeight &&
               container.getWidth() <= maxWidth &&
               container.getHeight() <= maxHeight;
    }
    
    /**
     * Picks up a container.
     */
    public boolean pickup(Container container) {
        if (carriedContainer != null || !canCarry(container)) {
            return false;
        }
        this.carriedContainer = container;
        return true;
    }
    
    /**
     * Drops the carried container.
     */
    public Container drop() {
        Container container = this.carriedContainer;
        this.carriedContainer = null;
        return container;
    }
    
    /**
     * Checks whether the robot is carrying a container.
     */
    public boolean isCarrying() {
        return carriedContainer != null;
    }
    
    /**
     * Returns the Manhattan distance to a point.
     */
    public int distanceTo(int targetX, int targetY) {
        return Math.abs(x - targetX) + Math.abs(y - targetY);
    }
    
    @Override
    public String toString() {
        String carrying = isCarrying() ? carriedContainer.getId() : "none";
        return String.format("Robot[%s(%s): @(%d,%d), carrying=%s, busy=%s]", 
            id, type, x, y, carrying, busy);
    }
}
