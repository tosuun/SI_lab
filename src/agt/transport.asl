transporting(none).

!start.

+!start : true <-
    true.

+!clear_outbound(Type) : transporting(none) <-
    -+transporting(Type);
    !transport_loop(Type).

+!clear_outbound(Type) : true <-
    true.

+!transport_loop(Type) : cycle_active(Type) <-
    remove_outbound(Type);
    .wait(3000);
    !transport_loop(Type).

+!transport_loop(Type) : not cycle_active(Type) <-
    remove_outbound(Type);
    -+transporting(none).
