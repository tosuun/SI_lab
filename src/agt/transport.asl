// Transport agent.
// It simulates the external system that removes containers from outbound.

// Belief: current transport state.
transporting(none).

// Desire/goal: wait until the scheduler asks for outbound removal.
!start.

+!start : true <-
    true.

// Plans/intentions: remove outbound containers for the active type.
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
