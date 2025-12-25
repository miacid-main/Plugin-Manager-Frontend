package dev.silentacore.optimization;

public interface Optimizer {
    void enable();
    void disable();
    void tick(); // Called periodically to adjust settings or clean up
    String getName();
}
