package warehouse;

import jason.asSyntax.Literal;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Structure;
import jason.environment.Environment;

import java.awt.GraphicsEnvironment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Warehouse environment.
 *
 * This class keeps the physical state of the warehouse and gives percepts to
 * the agents.
 *
 * Agents decide what to do. The environment only checks if the physical action
 * is possible and then updates the state.
 */
public class WarehouseArtifact extends Environment {

    // Basic layout and time values.
    private static final int GRID_WIDTH = 20;
    private static final int GRID_HEIGHT = 15;
    private static final int INBOUND_X = 1;
    private static final int INBOUND_Y = 1;
    private static final int OUTBOUND_X = 18;
    private static final int OUTBOUND_Y = 13;
    private static final int DELTA_T_SECONDS = 30;
    private static final int MAX_REPATH_ATTEMPTS = 250;

    // Main warehouse state.
    private CellType[][] grid;
    private Map<String, Robot> robots;
    private Map<String, Container> containers;
    private Map<String, Shelf> shelves;

    // Extra state for tasks, reservations and output cycles.
    private Map<String, List<String>> shelfPolicy;
    private Map<String, ActiveCycle> activeCycles;
    private Map<String, String> storageReservations;
    private Map<String, Double> reservedShelfWeight;
    private Map<String, Integer> reservedShelfVolume;
    private String lastOutputAgent;
    private final Object movementLock = new Object();

    private WarehouseView view;
    private ExecutorService containerGeneratorExecutor;
    private volatile boolean running = true;

    private int containerCounter = 0;
    private int totalContainersProcessed = 0;
    private int totalDelivered = 0;
    private int totalRemoved = 0;
    private int totalErrors = 0;
    private long startTime;

    @Override
    public void init(String[] args) {
        super.init(args);

        grid = new CellType[GRID_WIDTH][GRID_HEIGHT];
        robots = new ConcurrentHashMap<>();
        containers = new ConcurrentHashMap<>();
        shelves = new LinkedHashMap<>();
        shelfPolicy = new HashMap<>();
        activeCycles = new ConcurrentHashMap<>();
        storageReservations = new ConcurrentHashMap<>();
        reservedShelfWeight = new ConcurrentHashMap<>();
        reservedShelfVolume = new ConcurrentHashMap<>();
        lastOutputAgent = null;

        startTime = System.currentTimeMillis();

        initializeGrid();
        initializeShelves();
        initializeShelfPolicy();
        initializeRobots();
        refreshShelfPercepts();

        if (!GraphicsEnvironment.isHeadless()) {
            view = new WarehouseView(this, GRID_WIDTH, GRID_HEIGHT);
            view.setVisible(true);
            view.logMessage("Warehouse initialized: inbound, classification and outbound zones ready");
        }

        startContainerGenerator();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        System.out.println("Warehouse environment initialized");
        System.out.println("DeltaT seconds: " + DELTA_T_SECONDS);
        System.out.println("Robots: " + robots.size());
        System.out.println("Shelves: " + shelves.size());
    }

    private void initializeGrid() {
        // Inbound is on the left, classification is in the middle, and outbound
        // is on the bottom-right.
        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                grid[x][y] = CellType.EMPTY;
            }
        }

        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                grid[x][y] = CellType.ENTRANCE;
            }
        }

        for (int x = 3; x < 7; x++) {
            for (int y = 0; y < 2; y++) {
                grid[x][y] = CellType.CLASSIFICATION;
            }
        }

        for (int x = 17; x < 20; x++) {
            for (int y = 12; y < 15; y++) {
                grid[x][y] = CellType.OUTBOUND;
            }
        }
    }

    private void initializeShelves() {
        // Shelves have different capacities, so the demo is easier to see.
        int shelfId = 1;

        for (int x = 10; x < 18; x += 2) {
            Shelf shelf = new Shelf("shelf_" + shelfId++, x, 2, 2, 2, 50, 8);
            addShelfToGrid(shelf);
        }

        for (int x = 10; x < 18; x += 3) {
            Shelf shelf = new Shelf("shelf_" + shelfId++, x, 6, 3, 2, 100, 12);
            addShelfToGrid(shelf);
        }

        for (int x = 10; x < 16; x += 4) {
            Shelf shelf = new Shelf("shelf_" + shelfId++, x, 10, 4, 3, 200, 20);
            addShelfToGrid(shelf);
        }
    }

    private void addShelfToGrid(Shelf shelf) {
        shelves.put(shelf.getId(), shelf);
        for (int dx = 0; dx < shelf.getWidth(); dx++) {
            for (int dy = 0; dy < shelf.getHeight(); dy++) {
                grid[shelf.getX() + dx][shelf.getY() + dy] = CellType.SHELF;
            }
        }
    }

    private void initializeShelfPolicy() {
        // Shelf rule from the assignment.
        // Urgent has its own shelves. Standard and fragile share the others.
        List<String> urgentShelves = Arrays.asList("shelf_1", "shelf_5", "shelf_8");
        List<String> normalShelves = Arrays.asList(
            "shelf_2", "shelf_3", "shelf_4", "shelf_6", "shelf_7", "shelf_9"
        );

        shelfPolicy.put("urgent", urgentShelves);
        shelfPolicy.put("standard", normalShelves);
        shelfPolicy.put("fragile", normalShelves);
    }

    private void initializeRobots() {
        // These capacities are also written as beliefs in the .mas2j file.
        addRobot("robot_light", "light", 10, 1, 1, 3, 1, 3);
        addRobot("robot_medium", "medium", 30, 1, 2, 2, 2, 3);
        addRobot("robot_heavy_1", "heavy", 100, 2, 3, 1, 3, 3);
        addRobot("robot_heavy_2", "heavy", 100, 2, 3, 1, 4, 3);
    }

    private void addRobot(String id, String type, double maxWeight, int maxWidth,
                          int maxHeight, int speed, int x, int y) {
        Robot robot = new Robot(id, type, maxWeight, maxWidth, maxHeight, speed);
        robot.setPosition(x, y);
        robots.put(id, robot);
    }

    private void startContainerGenerator() {
        // Random arrivals keep the system running.
        containerGeneratorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ContainerGenerator");
            t.setDaemon(true);
            return t;
        });

        containerGeneratorExecutor.submit(() -> {
            Random rand = new Random();
            while (running) {
                try {
                    Thread.sleep(5000 + rand.nextInt(5000));
                    if (!running) {
                        break;
                    }
                    Container container = generateRandomContainer();
                    registerInboundContainer(container);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private Container generateRandomContainer() {
        // Most containers are standard. Fragile and urgent appear less often.
        Random rand = new Random();
        String id = "container_" + (++containerCounter);
        int[][] sizes = {{1, 1}, {1, 2}, {2, 2}, {2, 3}};
        int[] size = sizes[rand.nextInt(sizes.length)];
        double weight = 5 + rand.nextDouble() * 95;

        String type;
        double r = rand.nextDouble();
        if (r < 0.70) {
            type = "standard";
        } else if (r < 0.85) {
            type = "fragile";
        } else {
            type = "urgent";
        }

        Container container = new Container(id, size[0], size[1], weight, type);
        container.setPosition(INBOUND_X, INBOUND_Y);
        return container;
    }

    private synchronized void registerInboundContainer(Container container) {
        containers.put(container.getId(), container);
        publishInboundContainer(container);
        logView(String.format(Locale.US, "Inbound: %s %.1fkg %s",
            container.getId(), container.getWeight(), container.getType()));
        updateView();
    }

    public void stop() {
        running = false;
        if (containerGeneratorExecutor != null) {
            containerGeneratorExecutor.shutdown();
            try {
                if (!containerGeneratorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    containerGeneratorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                containerGeneratorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Warehouse environment stopped");
    }

    @Override
    public boolean executeAction(String agName, Structure action) {
        // Jason agents call actions here.
        try {
            switch (action.getFunctor()) {
                case "move_to":
                    return executeMoveTo(agName, action);
                case "claim_storage":
                    return executeClaimStorage(agName, action);
                case "claim_output":
                    return executeClaimOutput(agName, action);
                case "pickup":
                    return executePickup(agName, action);
                case "drop_at":
                    return executeDropAt(agName, action);
                case "deliver":
                    return executeDeliver(agName, action);
                case "read_time":
                    return executeReadTime(agName);
                case "open_output_cycle":
                    return executeOpenOutputCycle(action);
                case "close_output_cycle":
                    return executeCloseOutputCycle(action);
                case "remove_outbound":
                    return executeRemoveOutbound(action);
                case "scan_surroundings":
                    return executeScanSurroundings(agName);
                case "recover_task":
                    return executeRecoverTask(agName);
                default:
                    addError(agName, "unknown_action", action.getFunctor());
                    return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean executeMoveTo(String agName, Structure action) throws Exception {
        // Movement is step by step, so it is visible in the GUI.
        int targetX = (int) ((NumberTerm) action.getTerm(0)).solve();
        int targetY = (int) ((NumberTerm) action.getTerm(1)).solve();
        Robot robot = robots.get(agName);

        if (robot == null) {
            addError(agName, "robot_not_found", agName);
            return false;
        }
        if (targetX < 0 || targetX >= GRID_WIDTH || targetY < 0 || targetY >= GRID_HEIGHT) {
            addError(agName, "illegal_move", "(" + targetX + "," + targetY + ")");
            return false;
        }
        if (isBlockedForTravel(targetX, targetY)) {
            addError(agName, "route_blocked", "(" + targetX + "," + targetY + ")");
            return false;
        }

        int destinationX = targetX;
        int destinationY = targetY;
        int waitAttempts = 0;
        while (robot.getX() != destinationX || robot.getY() != destinationY) {
            boolean moved = false;

            synchronized (movementLock) {
                int[] destination = chooseAvailableDestination(
                    agName, robot.getX(), robot.getY(), targetX, targetY
                );
                if (destination != null) {
                    destinationX = destination[0];
                    destinationY = destination[1];
                }

                List<int[]> path = findPath(agName, robot.getX(), robot.getY(), destinationX, destinationY);
                if (!path.isEmpty()) {
                    int[] step = path.get(0);
                    robot.setPosition(step[0], step[1]);
                    removePerceptsByUnif(agName, Literal.parseLiteral("robot_at(_,_)"));
                    addPercept(agName, Literal.parseLiteral("robot_at(" + step[0] + "," + step[1] + ")"));
                    updateView();
                    waitAttempts = 0;
                    moved = true;
                }
            }

            if (!moved) {
                waitAttempts++;
                if (waitAttempts > MAX_REPATH_ATTEMPTS) {
                    addError(agName, "route_blocked", "(" + destinationX + "," + destinationY + ")");
                    return false;
                }
            }

            Thread.sleep(moved ? movementDelayMs(robot) : 120);
        }
        return true;
    }

    private int[] chooseAvailableDestination(String agName, int startX, int startY, int targetX, int targetY) {
        if (isReachableDestination(agName, startX, startY, targetX, targetY)) {
            return new int[] {targetX, targetY};
        }

        int bestX = -1;
        int bestY = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) + Math.abs(dy) > radius) {
                        continue;
                    }
                    int candidateX = targetX + dx;
                    int candidateY = targetY + dy;
                    if (!isInBounds(candidateX, candidateY)
                        || !isReachableDestination(agName, startX, startY, candidateX, candidateY)) {
                        continue;
                    }

                    int distance = Math.abs(startX - candidateX) + Math.abs(startY - candidateY);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestX = candidateX;
                        bestY = candidateY;
                    }
                }
            }

            if (bestX != -1) {
                return new int[] {bestX, bestY};
            }
        }

        return null;
    }

    private boolean isReachableDestination(String agName, int startX, int startY, int targetX, int targetY) {
        if (!isInBounds(targetX, targetY)
            || isBlockedForTravel(targetX, targetY)
            || isOccupiedByOtherRobot(targetX, targetY, agName)) {
            return false;
        }
        return startX == targetX && startY == targetY
            || !findPath(agName, startX, startY, targetX, targetY).isEmpty();
    }

    private List<int[]> findPath(String movingRobotId, int startX, int startY, int targetX, int targetY) {
        if (startX == targetX && startY == targetY) {
            return List.of();
        }

        boolean[][] visited = new boolean[GRID_WIDTH][GRID_HEIGHT];
        int[][] previousX = new int[GRID_WIDTH][GRID_HEIGHT];
        int[][] previousY = new int[GRID_WIDTH][GRID_HEIGHT];
        for (int x = 0; x < GRID_WIDTH; x++) {
            Arrays.fill(previousX[x], -1);
            Arrays.fill(previousY[x], -1);
        }

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {startX, startY});
        visited[startX][startY] = true;

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            for (int[] direction : directions) {
                int nx = current[0] + direction[0];
                int ny = current[1] + direction[1];
                if (nx < 0 || nx >= GRID_WIDTH || ny < 0 || ny >= GRID_HEIGHT) {
                    continue;
                }
                if (visited[nx][ny]
                    || isBlockedForTravel(nx, ny)
                    || isOccupiedByOtherRobot(nx, ny, movingRobotId)) {
                    continue;
                }

                visited[nx][ny] = true;
                previousX[nx][ny] = current[0];
                previousY[nx][ny] = current[1];

                if (nx == targetX && ny == targetY) {
                    return reconstructPath(startX, startY, targetX, targetY, previousX, previousY);
                }
                queue.addLast(new int[] {nx, ny});
            }
        }

        return List.of();
    }

    private List<int[]> reconstructPath(int startX, int startY, int targetX, int targetY,
                                        int[][] previousX, int[][] previousY) {
        List<int[]> path = new ArrayList<>();
        int x = targetX;
        int y = targetY;

        while (x != startX || y != startY) {
            path.add(new int[] {x, y});
            int px = previousX[x][y];
            int py = previousY[x][y];
            x = px;
            y = py;
        }

        Collections.reverse(path);
        return path;
    }

    private boolean isBlockedForTravel(int x, int y) {
        return grid[x][y] == CellType.BLOCKED || grid[x][y] == CellType.SHELF;
    }

    private boolean isInBounds(int x, int y) {
        return x >= 0 && x < GRID_WIDTH && y >= 0 && y < GRID_HEIGHT;
    }

    private boolean isOccupiedByOtherRobot(int x, int y, String movingRobotId) {
        for (Robot other : robots.values()) {
            if (!other.getId().equals(movingRobotId) && other.getX() == x && other.getY() == y) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean executeClaimStorage(String agName, Structure action) {
        // Robots are not assigned directly. They see a container and try to
        // claim it. Only one robot can win the claim.
        String containerId = cleanId(action.getTerm(0).toString());
        Robot robot = robots.get(agName);
        Container container = containers.get(containerId);

        if (robot == null || container == null) {
            addClaimFailed(agName, containerId);
            return true;
        }
        if (robot.isBusy()
            || robot.isCarrying()
            || !"inbound".equals(container.getStatus())
            || activeCycles.containsKey(container.getType())
            || !robot.canCarry(container)) {
            addClaimFailed(agName, containerId);
            return true;
        }

        Shelf shelf = findShelfForType(container);
        if (shelf == null) {
            addClaimFailed(agName, containerId);
            return true;
        }

        container.setStatus("reserved_storage");
        reserveShelfSpace(shelf, container);
        robot.setBusy(true);
        robot.setCurrentTask(containerId);
        removeInboundContainerPercept(container);
        System.out.println("Task accepted by " + agName + ": store " + containerId
            + " (" + container.getType() + ") -> " + shelf.getId());
        logView(agName + " accepted storage task: " + containerId
            + " (" + container.getType() + ") -> " + shelf.getId());
        addPercept(agName, Literal.parseLiteral(
            "storage_task(\"" + containerId + "\",\"" + shelf.getId() + "\")"
        ));
        return true;
    }

    private synchronized boolean executeClaimOutput(String agName, Structure action) {
        // Output claims only work for the active cycle type.
        String containerId = cleanId(action.getTerm(0).toString());
        Robot robot = robots.get(agName);
        Container container = containers.get(containerId);

        if (robot == null
            || container == null
            || robot.isBusy()
            || robot.isCarrying()
            || !robot.canCarry(container)) {
            addClaimFailed(agName, containerId);
            return true;
        }
        ActiveCycle cycle = activeCycles.get(container.getType());
        if (cycle == null || !cycle.containerIds.contains(containerId) || !"stored".equals(container.getStatus())) {
            addClaimFailed(agName, containerId);
            return true;
        }
        if (agName.equals(lastOutputAgent) && hasOtherAvailableOutputRobot(agName, cycle)) {
            addClaimFailed(agName, containerId);
            return true;
        }

        String shelfId = container.getAssignedShelf();
        container.setStatus("reserved_output");
        robot.setBusy(true);
        robot.setCurrentTask(containerId);
        lastOutputAgent = agName;
        removeOutputCandidatePercept(container);
        System.out.println("Task accepted by " + agName + ": output " + containerId
            + " (" + container.getType() + ") from " + shelfId);
        logView(agName + " accepted output task: " + containerId
            + " (" + container.getType() + ") from " + shelfId);
        addPercept(agName, Literal.parseLiteral(
            "output_task(\"" + containerId + "\",\"" + shelfId + "\"," + container.getType() + ")"
        ));
        return true;
    }

    private boolean executePickup(String agName, Structure action) {
        String containerId = cleanId(action.getTerm(0).toString());
        Robot robot = robots.get(agName);
        Container container = containers.get(containerId);

        if (robot == null || container == null) {
            addError(agName, "invalid_pickup", containerId);
            return false;
        }
        if (!robot.canCarry(container) || robot.isCarrying()) {
            addError(agName, "invalid_pickup", containerId);
            return false;
        }
        if (!isRobotNearContainer(robot, container)) {
            addError(agName, "too_far", containerId);
            return false;
        }

        if ("reserved_output".equals(container.getStatus()) && container.getAssignedShelf() != null) {
            Shelf shelf = shelves.get(container.getAssignedShelf());
            if (shelf != null) {
                shelf.remove(containerId, container.getWeight(), container.getArea());
                refreshShelfPercepts();
            }
            container.setAssignedShelf(null);
        }

        if (!robot.pickup(container)) {
            addError(agName, "invalid_pickup", containerId);
            return false;
        }

        container.setPicked(true);
        container.setStatus("carried");
        addPercept(agName, Literal.parseLiteral("picked(\"" + containerId + "\")"));
        updateView();
        return true;
    }

    private synchronized boolean executeDropAt(String agName, Structure action) {
        String shelfId = cleanId(action.getTerm(0).toString());
        Robot robot = robots.get(agName);
        Shelf shelf = shelves.get(shelfId);

        if (robot == null || shelf == null || !robot.isCarrying()) {
            addError(agName, "invalid_drop", shelfId);
            return false;
        }
        if (robot.distanceTo(shelf.getX(), shelf.getY()) > 2) {
            addError(agName, "too_far", shelfId);
            return false;
        }

        Container container = robot.getCarriedContainer();
        if (!isShelfAllowedForType(shelfId, container.getType()) || !shelf.canStore(container)) {
            addError(agName, "shelf_full", shelfId);
            return false;
        }

        shelf.store(container);
        releaseShelfReservation(container.getId());
        robot.drop();
        robot.setBusy(false);
        robot.setCurrentTask(null);

        container.setPicked(false);
        container.setAssignedShelf(shelfId);
        container.setPosition(shelf.getX(), shelf.getY());
        container.setStatus("stored");
        totalContainersProcessed++;

        removePerceptsByUnif(agName, Literal.parseLiteral("picked(_)"));
        addPercept(agName, Literal.parseLiteral(
            "stored(\"" + container.getId() + "\",\"" + shelfId + "\")"
        ));
        logView(container.getId() + " stored at " + shelfId);
        updateView();
        return true;
    }

    private synchronized boolean executeDeliver(String agName, Structure action) {
        String containerId = cleanId(action.getTerm(0).toString());
        Robot robot = robots.get(agName);
        Container container = containers.get(containerId);

        if (robot == null || container == null || !robot.isCarrying()) {
            addError(agName, "invalid_delivery", containerId);
            return false;
        }
        if (!robot.getCarriedContainer().getId().equals(containerId)
            || robot.distanceTo(OUTBOUND_X, OUTBOUND_Y) > 2) {
            addError(agName, "invalid_delivery", containerId);
            return false;
        }

        robot.drop();
        robot.setBusy(false);
        robot.setCurrentTask(null);

        container.setPicked(false);
        container.setAssignedShelf(null);
        container.setPosition(OUTBOUND_X, OUTBOUND_Y);
        container.setStatus("outbound");
        totalDelivered++;

        removePerceptsByUnif(agName, Literal.parseLiteral("picked(_)"));
        addPercept(agName, Literal.parseLiteral(
            "delivered(\"" + containerId + "\"," + currentTimeSeconds() + ")"
        ));
        removeOutputPendingPercept(container);
        logView(containerId + " delivered to outbound");
        updateView();
        return true;
    }

    private boolean executeReadTime(String agName) {
        removePerceptsByUnif(agName, Literal.parseLiteral("time(_)"));
        addPercept(agName, Literal.parseLiteral("time(" + currentTimeSeconds() + ")"));
        return true;
    }

    private synchronized boolean executeOpenOutputCycle(Structure action) {
        String type = cleanId(action.getTerm(0).toString());
        if (!activeCycles.isEmpty()) {
            return true;
        }

        List<String> cycleContainers = containers.values().stream()
            .filter(c -> type.equals(c.getType()))
            .filter(c -> "stored".equals(c.getStatus()))
            .map(Container::getId)
            .sorted()
            .collect(Collectors.toList());

        if (cycleContainers.isEmpty()) {
            refreshInboundPercepts(type);
            return true;
        }

        int now = currentTimeSeconds();
        ActiveCycle cycle = new ActiveCycle(type, now, cycleContainers);
        activeCycles.put(type, cycle);

        removeInboundPercepts(type);
        addPercept(Literal.parseLiteral("cycle_active(" + type + ")"));
        for (String containerId : cycleContainers) {
            Container container = containers.get(containerId);
            if (container != null) {
                publishOutputCandidate(container);
                publishOutputPending(container);
            }
        }
        return true;
    }

    private synchronized boolean executeCloseOutputCycle(Structure action) {
        String type = cleanId(action.getTerm(0).toString());
        if (activeCycles.containsKey(type)) {
            activeCycles.remove(type);
            lastOutputAgent = null;
            removeCyclePercept(type);
            removeOutputPercepts(type);
            removeOutputPendingPercepts(type);
            refreshInboundPercepts(type);
        }
        return true;
    }

    private synchronized boolean executeRemoveOutbound(Structure action) {
        String type = cleanId(action.getTerm(0).toString());
        List<String> removable = containers.values().stream()
            .filter(c -> type.equals(c.getType()))
            .filter(c -> "outbound".equals(c.getStatus()))
            .map(Container::getId)
            .collect(Collectors.toList());

        for (String containerId : removable) {
            containers.remove(containerId);
            totalRemoved++;
        }
        updateView();
        return true;
    }

    private boolean executeScanSurroundings(String agName) {
        Robot robot = robots.get(agName);
        if (robot == null) {
            return false;
        }
        int x = robot.getX();
        int y = robot.getY();
        removePerceptsByUnif(agName, Literal.parseLiteral("cell(_,_,_)"));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < GRID_WIDTH && ny >= 0 && ny < GRID_HEIGHT) {
                    addPercept(agName, Literal.parseLiteral(
                        "cell(" + nx + "," + ny + "," + grid[nx][ny].name().toLowerCase(Locale.ROOT) + ")"
                    ));
                }
            }
        }
        return true;
    }

    private synchronized boolean executeRecoverTask(String agName) {
        Robot robot = robots.get(agName);
        if (robot == null) {
            return false;
        }

        String taskId = robot.getCurrentTask();
        if (taskId != null) {
            Container container = containers.get(taskId);
            if (container != null && storageReservations.containsKey(taskId)) {
                releaseShelfReservation(taskId);
                if (robot.isCarrying()
                    && robot.getCarriedContainer() != null
                    && taskId.equals(robot.getCarriedContainer().getId())) {
                    robot.drop();
                }
                container.setPicked(false);
                container.setAssignedShelf(null);
                container.setPosition(INBOUND_X, INBOUND_Y);
                container.setStatus("inbound");
                publishInboundContainer(container);
            }
        }

        robot.setBusy(false);
        robot.setCurrentTask(null);
        removePerceptsByUnif(agName, Literal.parseLiteral("picked(_)"));
        return true;
    }

    private boolean hasOtherAvailableOutputRobot(String agName, ActiveCycle cycle) {
        return robots.values().stream()
            .filter(robot -> !robot.getId().equals(agName))
            .filter(robot -> !robot.isBusy() && !robot.isCarrying())
            .anyMatch(robot -> cycle.containerIds.stream()
                .map(containers::get)
                .anyMatch(container -> container != null
                    && "stored".equals(container.getStatus())
                    && robot.canCarry(container)));
    }

    private Shelf findShelfForType(Container container) {
        return shelfPolicy.getOrDefault(container.getType(), List.of()).stream()
            .map(shelves::get)
            .filter(shelf -> shelf != null && canReserveShelf(shelf, container))
            .min(Comparator.comparingDouble(this::getReservedOccupancyPercentage))
            .orElse(null);
    }

    private boolean canReserveShelf(Shelf shelf, Container container) {
        double reservedWeight = reservedShelfWeight.getOrDefault(shelf.getId(), 0.0);
        int reservedVolume = reservedShelfVolume.getOrDefault(shelf.getId(), 0);
        return shelf.getCurrentWeight() + reservedWeight + container.getWeight() <= shelf.getMaxWeight()
            && shelf.getCurrentVolume() + reservedVolume + container.getArea() <= shelf.getMaxVolume();
    }

    private double getReservedOccupancyPercentage(Shelf shelf) {
        double reservedWeight = reservedShelfWeight.getOrDefault(shelf.getId(), 0.0);
        int reservedVolume = reservedShelfVolume.getOrDefault(shelf.getId(), 0);
        double weightPercent = ((shelf.getCurrentWeight() + reservedWeight) / shelf.getMaxWeight()) * 100;
        double volumePercent = ((double) (shelf.getCurrentVolume() + reservedVolume) / shelf.getMaxVolume()) * 100;
        return Math.max(weightPercent, volumePercent);
    }

    private void reserveShelfSpace(Shelf shelf, Container container) {
        storageReservations.put(container.getId(), shelf.getId());
        reservedShelfWeight.merge(shelf.getId(), container.getWeight(), Double::sum);
        reservedShelfVolume.merge(shelf.getId(), container.getArea(), Integer::sum);
        refreshShelfPercepts();
    }

    private void releaseShelfReservation(String containerId) {
        String shelfId = storageReservations.remove(containerId);
        if (shelfId == null) {
            return;
        }

        Container container = containers.get(containerId);
        if (container == null) {
            return;
        }

        reservedShelfWeight.computeIfPresent(shelfId, (id, value) ->
            Math.max(0.0, value - container.getWeight())
        );
        reservedShelfVolume.computeIfPresent(shelfId, (id, value) ->
            Math.max(0, value - container.getArea())
        );
        refreshShelfPercepts();
    }

    private boolean isShelfAllowedForType(String shelfId, String type) {
        return shelfPolicy.getOrDefault(type, List.of()).contains(shelfId);
    }

    private boolean isRobotNearContainer(Robot robot, Container container) {
        return robot.distanceTo(container.getX(), container.getY()) <= 2;
    }

    private long movementDelayMs(Robot robot) {
        switch (robot.getSpeed()) {
            case 3:
                return 90;
            case 2:
                return 140;
            default:
                return 220;
        }
    }

    private boolean isDeliveredOrRemoved(Container container) {
        return "outbound".equals(container.getStatus()) || "removed".equals(container.getStatus());
    }

    private void publishInboundContainer(Container container) {
        // Robots use this percept to choose storage tasks.
        if (!"inbound".equals(container.getStatus())
            || activeCycles.containsKey(container.getType())) {
            return;
        }
        addPercept(Literal.parseLiteral(String.format(Locale.US,
            "container_available(\"%s\",%d,%d,%.2f,%s)",
            container.getId(),
            container.getWidth(),
            container.getHeight(),
            container.getWeight(),
            container.getType()
        )));
    }

    private void publishOutputCandidate(Container container) {
        // Robots use this percept to choose output tasks.
        if (!"stored".equals(container.getStatus()) || container.getAssignedShelf() == null) {
            return;
        }
        addPercept(Literal.parseLiteral(String.format(Locale.US,
            "output_candidate(\"%s\",%d,%d,%.2f,%s,\"%s\")",
            container.getId(),
            container.getWidth(),
            container.getHeight(),
            container.getWeight(),
            container.getType(),
            container.getAssignedShelf()
        )));
    }

    private void publishOutputPending(Container container) {
        addPercept(Literal.parseLiteral(
            "output_pending(\"" + container.getId() + "\"," + container.getType() + ")"
        ));
    }

    private void refreshShelfPercepts() {
        removePerceptsByUnif(Literal.parseLiteral("shelf_state(_,_,_)"));
        for (Shelf shelf : shelves.values()) {
            double reservedWeight = reservedShelfWeight.getOrDefault(shelf.getId(), 0.0);
            int reservedVolume = reservedShelfVolume.getOrDefault(shelf.getId(), 0);
            double freeWeight = Math.max(0.0, shelf.getMaxWeight() - shelf.getCurrentWeight() - reservedWeight);
            int freeVolume = Math.max(0, shelf.getMaxVolume() - shelf.getCurrentVolume() - reservedVolume);
            addPercept(Literal.parseLiteral(String.format(Locale.US,
                "shelf_state(\"%s\",%.2f,%d)",
                shelf.getId(),
                freeWeight,
                freeVolume
            )));
        }
    }

    private void refreshInboundPercepts(String type) {
        removeInboundPercepts(type);
        containers.values().stream()
            .filter(c -> type.equals(c.getType()))
            .filter(c -> "inbound".equals(c.getStatus()))
            .forEach(this::publishInboundContainer);
    }

    private void removeInboundPercepts(String type) {
        removePerceptsByUnif(Literal.parseLiteral("container_available(_,_,_,_," + type + ")"));
    }

    private void removeInboundContainerPercept(Container container) {
        removePerceptsByUnif(Literal.parseLiteral(
            "container_available(\"" + container.getId() + "\",_,_,_,_)"
        ));
    }

    private void removeOutputCandidatePercept(Container container) {
        removePerceptsByUnif(Literal.parseLiteral(
            "output_candidate(\"" + container.getId() + "\",_,_,_,_,_)"
        ));
    }

    private void removeOutputPercepts(String type) {
        removePerceptsByUnif(Literal.parseLiteral("output_candidate(_,_,_,_," + type + ",_)"));
    }

    private void removeOutputPendingPercept(Container container) {
        removePerceptsByUnif(Literal.parseLiteral(
            "output_pending(\"" + container.getId() + "\",_)"
        ));
    }

    private void removeOutputPendingPercepts(String type) {
        removePerceptsByUnif(Literal.parseLiteral("output_pending(_," + type + ")"));
    }

    private void removeCyclePercept(String type) {
        removePerceptsByUnif(Literal.parseLiteral("cycle_active(" + type + ")"));
    }

    private void addClaimFailed(String agName, String containerId) {
        removePerceptsByUnif(agName, Literal.parseLiteral("claim_failed(\"" + containerId + "\")"));
        addPercept(agName, Literal.parseLiteral("claim_failed(\"" + containerId + "\")"));
    }

    private String cleanId(String raw) {
        return raw.replace("\"", "");
    }

    private int currentTimeSeconds() {
        return (int) ((System.currentTimeMillis() - startTime) / 1000);
    }

    private void addError(String agName, String errorType, String data) {
        totalErrors++;
        addPercept(agName, Literal.parseLiteral("error(" + errorType + ",\"" + data + "\")"));
        System.err.println("ERROR [" + agName + "]: " + errorType + " - " + data);
    }

    private void logView(String message) {
        if (view != null) {
            view.logMessage(message);
        }
    }

    private void updateView() {
        if (view != null) {
            view.update();
        }
    }

    public CellType[][] getGrid() {
        return grid;
    }

    public Map<String, Robot> getRobots() {
        return robots;
    }

    public Map<String, Container> getContainers() {
        return containers;
    }

    public Map<String, Shelf> getShelves() {
        return shelves;
    }

    public int getPendingContainersCount() {
        return (int) containers.values().stream()
            .filter(c -> "inbound".equals(c.getStatus()))
            .count();
    }

    public int getTotalContainersProcessed() {
        return totalContainersProcessed;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public String getStatistics() {
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        String cycles = activeCycles.keySet().isEmpty()
            ? "none"
            : String.join(",", activeCycles.keySet());
        return String.format(Locale.US,
            "Time: %ds | Stored: %d | Delivered: %d | Removed: %d | Pending inbound: %d | Errors: %d | Cycles: %s",
            elapsedTime,
            totalContainersProcessed,
            totalDelivered,
            totalRemoved,
            getPendingContainersCount(),
            totalErrors,
            cycles
        );
    }

    private static class ActiveCycle {
        private final String type;
        private final int startSeconds;
        private final Set<String> containerIds;

        private ActiveCycle(String type, int startSeconds, List<String> containerIds) {
            this.type = type;
            this.startSeconds = startSeconds;
            this.containerIds = new HashSet<>(containerIds);
        }
    }
}
