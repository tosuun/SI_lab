// Transport agent.
// It simulates the external system that removes containers from outbound.

transporting(none).

!start.

+!start : true <-
    true.

// Start clearing outbound containers for this type.
+!clear_outbound(Type) : transporting(none) <-
    -+transporting(Type);
    !transport_loop(Type).

+!clear_outbound(Type) : true <-
    true.

// Keep removing containers while the cycle is active.
+!transport_loop(Type) : cycle_active(Type) <-
    remove_outbound(Type);
    .wait(3000);
    !transport_loop(Type).

// Last cleanup after the cycle is closed.
+!transport_loop(Type) : not cycle_active(Type) <-
    remove_outbound(Type);
    -+transporting(none).
