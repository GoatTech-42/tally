package dev.doughbay.fabric.automation;

import java.util.ArrayList;
import java.util.List;

/** A collection receipt is a batch, not necessarily one physical inventory stack. */
final class CollectedStacks {
    record Part(int count, long cost) { }

    static List<Part> split(int count, int maximumStack, long cost, long unitPrice) {
        if (count <= 0 || maximumStack <= 0 || maximumStack > 64 || cost <= 0)
            throw new IllegalArgumentException("Invalid collected batch");
        long totalCost = Math.max(cost, CollectedCost.fullStack(count, unitPrice));
        var parts = new ArrayList<Part>();
        int left = count;
        long remainingCost = totalCost;
        while (left > 0) {
            int quantity = Math.min(left, maximumStack);
            long share = quantity == left ? remainingCost : java.math.BigInteger.valueOf(totalCost)
                    .multiply(java.math.BigInteger.valueOf(quantity))
                    .divide(java.math.BigInteger.valueOf(count)).longValueExact();
            parts.add(new Part(quantity, share));
            remainingCost -= share;
            left -= quantity;
        }
        return List.copyOf(parts);
    }
    private CollectedStacks() { }
}
