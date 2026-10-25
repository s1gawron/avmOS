package avmos.process;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All processes that exist, found by pid and kept in the order they were loaded. */
public final class ProcessTable {

    private final Map<Integer, Pcb> processes = new LinkedHashMap<>();

    public void add(Pcb pcb) {
        if (processes.putIfAbsent(pcb.pid(), pcb) != null) {
            throw new IllegalArgumentException("process " + pcb.pid() + " already exists");
        }
    }

    /** Returns the process with this pid, or {@code null} if there is none. */
    public Pcb find(int pid) {
        return processes.get(pid);
    }

    public void remove(int pid) {
        processes.remove(pid);
    }

    /** A copy of the table: it can be used while processes are added or removed. */
    public List<Pcb> all() {
        return new ArrayList<>(processes.values());
    }

    public int size() {
        return processes.size();
    }
}
