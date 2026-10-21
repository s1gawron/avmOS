package avmos.memory;

import avmos.Fault;

/** An access to an address that does not exist. */
public class MemoryFault extends Fault {

    public MemoryFault(String message) {
        super(message);
    }
}
