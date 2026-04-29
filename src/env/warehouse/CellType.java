package warehouse;

/**
 * Cell types used in the warehouse grid.
 */
public enum CellType {
    EMPTY,          // Empty aisle
    ENTRANCE,       // Inbound area
    CLASSIFICATION, // Classification area
    OUTBOUND,       // Outbound area
    STORAGE,        // Storage area
    SHELF,          // Shelf cell
    BLOCKED,        // Blocked cell
    ROBOT           // Robot position
}
