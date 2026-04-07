package net.mackenziemolloy.shopguiplus.sellgui.objects;

import org.bukkit.inventory.ItemStack;

public class SellResult {
    private final double totalPrice;
    private final int itemAmount;
    private final ItemStack shulker;

    public SellResult(double totalPrice, int itemAmount, ItemStack shulker) {
        this.totalPrice = totalPrice;
        this.itemAmount = itemAmount;
        this.shulker = shulker;
    }

    public double totalPrice() {
        return totalPrice;
    }

    public int itemAmount() {
        return itemAmount;
    }

    public ItemStack shulker() {
        return shulker;
    }
}
