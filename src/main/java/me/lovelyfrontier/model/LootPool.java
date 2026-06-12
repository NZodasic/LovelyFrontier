package me.lovelyfrontier.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootPool {
    private String id;
    private double fillRate = 0.5; // default 50% of slots filled
    private final List<LootItem> items = new ArrayList<>();

    public LootPool() {}

    public LootPool(String id, double fillRate) {
        this.id = id;
        this.fillRate = fillRate;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getFillRate() { return fillRate; }
    public void setFillRate(double fillRate) { this.fillRate = fillRate; }

    public List<LootItem> getItems() { return items; }
    public void addItem(LootItem item) { this.items.add(item); }

    public static class LootItem {
        private ItemStack item;
        private int weight;
        private int minAmount;
        private int maxAmount;

        public LootItem() {}

        public LootItem(ItemStack item, int weight, int minAmount, int maxAmount) {
            this.item = item;
            this.weight = weight;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        public ItemStack getItem() { return item; }
        public void setItem(ItemStack item) { this.item = item; }

        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }

        public int getMinAmount() { return minAmount; }
        public void setMinAmount(int minAmount) { this.minAmount = minAmount; }

        public int getMaxAmount() { return maxAmount; }
        public void setMaxAmount(int maxAmount) { this.maxAmount = maxAmount; }
    }
}
