package net.mackenziemolloy.shopguiplus.sellgui.command;

import com.tcoded.folialib.impl.PlatformScheduler;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mackenziemolloy.shopguiplus.sellgui.SellGUI;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellResult;
import net.mackenziemolloy.shopguiplus.sellgui.objects.ShopItemPriceValue;
import net.mackenziemolloy.shopguiplus.sellgui.utility.*;
import net.mackenziemolloy.shopguiplus.sellgui.utility.sirblobman.HexColorUtility;
import net.mackenziemolloy.shopguiplus.sellgui.utility.sirblobman.MessageUtility;
import net.mackenziemolloy.shopguiplus.sellgui.utility.sirblobman.VersionUtility;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public final class CommandSellGUI implements TabExecutor {

    private final SellGUI plugin;
    private final PlatformScheduler scheduler;

    public CommandSellGUI(SellGUI plugin) {
        this.plugin = Objects.requireNonNull(plugin, "The plugin must not be null!");
        this.scheduler = SellGUI.scheduler();
    }

    public void register() {
        PluginCommand pluginCommand = this.plugin.getCommand("sellgui");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            List<String> valueSet = Arrays.asList("rl", "reload", "debug", "dump");
            return StringUtil.copyPartialMatches(args[0], valueSet, new ArrayList<>());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!plugin.compatible) {
            sender.sendMessage(MessageUtility.color("&7\n&7\n&a&lUPDATE REQUIRED \n&7\n&7Unfortunately &fSellGUI &7will not work until you update &cShopGUIPlus&7 to version &c1.78.0&7 or above.\n&7\n&eDownload: https://spigotmc.org/resources/6515/\n&7\n&7"));
            return false;
        }
        if (args.length == 0) {
            return commandBase(sender);
        }
        String sub = args[0].toLowerCase(Locale.US);
        return switch (sub) {
            case "rl", "reload" -> commandReload(sender);
            case "debug", "dump" -> commandDebug(sender);
            default -> false;
        };
    }

    private boolean commandReload(CommandSender sender) {
        if (!sender.hasPermission("sellgui.reload")) {
            sendMessage(sender, "no_permission");
            return true;
        }
        CompletableFuture.runAsync(this.plugin::generateFiles).whenComplete((success, error) -> {
            if (error != null) {
                sender.sendMessage(ChatColor.RED + "An error occurred, please check the server console.");
                error.printStackTrace();
                return;
            }
            if (!this.plugin.getConfiguration().getBoolean("options.transaction_log.enabled", false)) {
                this.plugin.closeLogger();
            } else if (this.plugin.fileLogger == null) {
                this.plugin.initLogger();
            }
            sendMessage(sender, "reloaded_config");
            if (sender instanceof Player player) {
                scheduler.runAtEntity(player, task -> PlayerHandler.playSound(player, "success"));
            }
        });
        return true;
    }

    private boolean commandDebug(CommandSender sender) {
        if (!sender.hasPermission("sellgui.dump")) {
            sendMessage(sender, "no_permission");
            return true;
        }
        CommentedConfiguration configuration = this.plugin.getConfiguration();
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin[] pluginArray = pluginManager.getPlugins();
        List<String> pluginInfoList = new ArrayList<>();
        for (Plugin plugin : pluginArray) {
            pluginInfoList.add(String.format(Locale.US, "- %s v%s by %s",
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    String.join(", ", plugin.getDescription().getAuthors())));
        }
        String pasteRaw = "| System Information\n\n" +
                "- OS Type: " + System.getProperty("os.name") + "\n" +
                "- OS Version: " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n" +
                "- Processor: " + System.getenv("PROCESSOR_IDENTIFIER") +
                "\n\n| Server Information\n\n" +
                "- Version: " + Bukkit.getBukkitVersion() + "\n" +
                "- Online Mode: " + Bukkit.getOnlineMode() + "\n" +
                "- Memory Usage: " + getMemoryUsage() +
                "\n\n| Plugins\n" + String.join("\n", pluginInfoList) +
                "\n\n| Plugin Configuration\n\n" + configuration.saveToString();
        try {
            String pasteUrl = new Hastebin().post(pasteRaw, true);
            String message = String.format(Locale.US,
                    ChatColor.translateAlternateColorCodes('&', "&c[ShopGUIPlus-SellGUI] Successfully dumped server information here: %s."),
                    pasteUrl);
            Bukkit.getConsoleSender().sendMessage(message);
            if (sender instanceof Player player) {
                sender.sendMessage(message);
                scheduler.runAtEntity(player, task -> PlayerHandler.playSound(player, "success"));
            }
        } catch (IOException ex) {
            sender.sendMessage(ChatColor.RED + "An error occurred, please check the console:");
            ex.printStackTrace();
        }
        return true;
    }

    private boolean commandBase(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can execute this command.");
            return true;
        }
        if (!player.hasPermission("sellgui.use")) {
            sendMessage(player, "no_permission");
            return true;
        }
        if (!checkGameMode(player)) {
            return true;
        }
        CommentedConfiguration configuration = this.plugin.getConfiguration();
        int guiSize = configuration.getInt("options.rows", 6);
        if (guiSize > 6 || guiSize < 1) guiSize = 6;

        String sellGuiTitle = getMessage("sellgui_title", null);
        Component sellGuiTitleComponent = LegacyComponentSerializer.legacySection().deserialize(sellGuiTitle);
        Gui gui = Gui.gui().title(sellGuiTitleComponent).rows(guiSize).create();

        Set<Integer> ignoredSlotSet = new HashSet<>();
        setDecorationItems(configuration, gui, ignoredSlotSet);
        gui.setCloseGuiAction(event -> scheduler.runAtEntity(player, task -> onGuiClose(player, event, ignoredSlotSet)));

        scheduler.runAtEntity(player, task -> {
            PlayerHandler.playSound(player, "open");
            gui.open(player);
        });
        return true;
    }

    private boolean checkGameMode(Player player) {
        ShopGuiPlugin shopGui = ShopGuiPlusApi.getPlugin();
        FileConfiguration configuration = shopGui.getConfigMain().getConfig();
        List<String> disabledGameModeList = configuration.getStringList("disableShopsInGamemodes");
        GameMode gameMode = player.getGameMode();
        String gameModeName = gameMode.name();
        if (disabledGameModeList.contains(gameModeName)) {
            sendMessage(player, "gamemode_not_allowed", message ->
                    message.replace("{gamemode}", StringFormatter.capitalize(gameModeName)));
            return false;
        }
        return true;
    }

    private void setDecorationItems(ConfigurationSection configuration, Gui gui, Set<Integer> ignoredSlotSet) {
        ConfigurationSection sectionDecorations = configuration.getConfigurationSection("options.decorations");
        if (sectionDecorations == null) return;

        for (String key : sectionDecorations.getKeys(false)) {
            ConfigurationSection section = sectionDecorations.getConfigurationSection(key);
            if (section == null) continue;

            Material material;
            String materialName = section.getString("item.material");
            if (materialName == null || (material = Material.matchMaterial(materialName)) == null
                    || !section.isInt("slot")
                    || section.getInt("slot") > ((gui.getRows() * 9) - 1)
                    || section.getInt("slot") < 0) {
                this.plugin.getLogger().warning("Failed to load decoration item with id '" + key + "'.");
                continue;
            }

            ItemStack item = new ItemStack(material);
            setItemDamage(item, section.getInt("item.damage", 0));
            item.setAmount(section.getInt("item.quantity", 1));

            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                String displayName = section.getString("item.name");
                if (displayName != null) itemMeta.setDisplayName(MessageUtility.color(displayName));

                List<String> loreList = section.getStringList("item.lore");
                if (!loreList.isEmpty()) {
                    List<String> processedLore = new ArrayList<>(loreList.size());
                    for (String line : loreList) {
                        processedLore.add(MessageUtility.color(HexColorUtility.replaceHexColors('&', line)));
                    }
                    itemMeta.setLore(processedLore);
                }

                int customModelData = section.getInt("item.customModelData");
                if (customModelData != 0) itemMeta.setCustomModelData(customModelData);
                item.setItemMeta(itemMeta);
            }

            List<String> consoleCommandList = section.getStringList("commandsOnClickConsole");
            List<String> playerCommandList = section.getStringList("commandsOnClick");

            GuiItem guiItem = new GuiItem(item, e -> {
                e.setCancelled(true);
                HumanEntity human = e.getWhoClicked();
                String humanName = human.getName();

                for (String consoleCommand : consoleCommandList) {
                    String cmd = consoleCommand.replace("%PLAYER%", humanName);
                    scheduler.runNextTick(task -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
                }
                for (String playerCommand : playerCommandList) {
                    String cmd = playerCommand.replace("%PLAYER%", humanName);
                    scheduler.runAtEntity(human, task -> Bukkit.dispatchCommand(human, cmd));
                }
                if (section.getBoolean("item.sellinventory")) {
                    scheduler.runAtEntity(human, task -> {
                        human.closeInventory();
                        commandBase(Bukkit.getPlayer(humanName));
                    });
                }
            });

            int slot = section.getInt("slot");
            gui.setItem(slot, guiItem);
            ignoredSlotSet.add(slot);
        }
    }

    private void onGuiClose(Player player, InventoryCloseEvent event, Set<Integer> ignoredSlotSet) {
        int minorVersion = VersionUtility.getMinorVersion();
        CommentedConfiguration configuration = this.plugin.getConfiguration();

        Map<ItemStack, ShopItemPriceValue> priceCache = new HashMap<>();
        Map<ItemStack, Map<Short, Integer>> soldMap = new HashMap<>();
        Map<EconomyType, Double> moneyMap = new EnumMap<>(EconomyType.class);

        double totalPrice = 0;
        int itemAmount = 0;
        boolean excessItems = false;
        boolean itemsPlacedInGui = false;

        List<ItemStack> shulkersToReturn = new LinkedList<>();
        Location dropLocation = player.getLocation().add(0.0D, 0.5D, 0.0D);

        Inventory inventory = event.getInventory();
        for (int a = 0; a < inventory.getSize(); a++) {
            ItemStack i = inventory.getItem(a);
            if (i == null || ignoredSlotSet.contains(a)) continue;

            itemsPlacedInGui = true;

            if (isShulkerBox(i)) {
                SellResult result = sellShulkerContents(i, player, priceCache, soldMap, moneyMap);
                totalPrice += result.totalPrice();
                itemAmount += result.itemAmount();
                shulkersToReturn.add(result.shulker());
                continue;
            }

            ItemStack singleItem = new ItemStack(i);
            singleItem.setAmount(1);

            double cachedPrice = priceCache.getOrDefault(singleItem, new ShopItemPriceValue(null, 0.0)).getSellPrice();
            double apiPrice = cachedPrice > 0 ? cachedPrice : ShopGuiPlusApi.getItemStackPriceSell(player, i);

            if (apiPrice > 0) {
                int amount = i.getAmount();
                @Deprecated short materialDamage = i.getDurability();

                double itemSellPrice = priceCache.containsKey(singleItem)
                        ? priceCache.get(singleItem).getSellPrice() * amount
                        : ShopGuiPlusApi.getItemStackPriceSell(player, i);

                totalPrice += itemSellPrice;
                itemAmount += amount;

                EconomyType economyType = ShopHandler.getEconomyType(i);

                ItemStack singleClone = new ItemStack(i);
                singleClone.setAmount(1);

                priceCache.putIfAbsent(singleClone, new ShopItemPriceValue(economyType, itemSellPrice / amount));

                soldMap.computeIfAbsent(singleClone, k -> new HashMap<>())
                        .merge(materialDamage, amount, Integer::sum);

                moneyMap.merge(economyType, itemSellPrice, Double::sum);
            } else {
                excessItems = true;
                Map<Integer, ItemStack> fallenItems = event.getPlayer().getInventory().addItem(i);
                if (!fallenItems.isEmpty()) {
                    scheduler.runAtLocation(dropLocation, task -> {
                        World world = player.getWorld();
                        fallenItems.values().forEach(item -> world.dropItemNaturally(dropLocation, item));
                    });
                }
            }
        }

        if (!shulkersToReturn.isEmpty()) {
            for (ItemStack shulker : shulkersToReturn) {
                Map<Integer, ItemStack> overflow = event.getPlayer().getInventory().addItem(shulker);
                if (!overflow.isEmpty()) {
                    scheduler.runAtLocation(dropLocation, task -> {
                        World world = player.getWorld();
                        overflow.values().forEach(item -> world.dropItemNaturally(dropLocation, item));
                    });
                }
            }
        }

        if (excessItems) sendMessage(player, "inventory_full");

        if (totalPrice == 0) {
            PlayerHandler.playSound(player, "failed");
            sendMessage(player, itemsPlacedInGui ? "no_items_sold" : "no_items_in_gui");
            return;
        }

        PlayerHandler.playSound(player, "success");

        StringBuilder formattedPricing = new StringBuilder();
        for (Entry<EconomyType, Double> entry : moneyMap.entrySet()) {
            EconomyProvider economyProvider = ShopGuiPlusApi.getPlugin().getEconomyManager()
                    .getEconomyProvider(entry.getKey());
            economyProvider.deposit(player, entry.getValue());
            formattedPricing.append(economyProvider.getCurrencyPrefix())
                    .append(StringFormatter.getFormattedNumber(entry.getValue()))
                    .append(economyProvider.getCurrencySuffix())
                    .append(", ");
        }
        if (formattedPricing.toString().endsWith(", ")) {
            formattedPricing = new StringBuilder(formattedPricing.substring(0, formattedPricing.length() - 2));
        }

        List<String> receiptList = new LinkedList<>();
        List<String> itemList = new LinkedList<>();

        if (configuration.getInt("options.receipt_type", 0) == 1
                || configuration.getString("messages.items_sold", "").contains("{list}")) {
            for (Entry<ItemStack, Map<Short, Integer>> entry : soldMap.entrySet()) {
                for (Entry<Short, Integer> damageEntry : entry.getValue().entrySet()) {
                    @Deprecated ItemStack materialItemStack = entry.getKey();

                    double profits = ShopGuiPlusApi.getItemStackPriceSell(player, materialItemStack) * damageEntry.getValue();
                    EconomyProvider ep = ShopGuiPlusApi.getPlugin().getEconomyManager()
                            .getEconomyProvider(ShopHandler.getEconomyType(materialItemStack));
                    String profitsFormatted = ep.getCurrencyPrefix() + StringFormatter.getFormattedNumber(profits) + ep.getCurrencySuffix();

                    String itemNameFormatted = StringFormatter.capitalize(materialItemStack.getType()
                            .name()
                            .replace("AETHER_LEGACY_", "")
                            .replace("LOST_AETHER_", "")
                            .replace("_", " ")
                            .toLowerCase());

                    ItemMeta itemMeta = materialItemStack.getItemMeta();
                    if (itemMeta != null && itemMeta.hasDisplayName()) {
                        String displayName = itemMeta.getDisplayName();
                        if (!displayName.isEmpty()) itemNameFormatted = displayName;
                    }

                    if (minorVersion <= 12 && !configuration.getBoolean("options.show_item_damage", false)) {
                        itemNameFormatted += ":" + damageEntry.getKey();
                    }

                    String finalName = itemNameFormatted;
                    receiptList.add(getMessage("receipt_item_layout", msg -> msg
                            .replace("{amount}", String.valueOf(damageEntry.getValue()))
                            .replace("{item}", finalName)
                            .replace("{price}", profitsFormatted)));
                    itemList.add(itemNameFormatted);
                }
            }
        }

        String itemAmountFormatted = StringFormatter.getFormattedNumber((double) itemAmount);

        if (configuration.getInt("options.receipt_type", 0) == 1) {
            int finalItemAmount = itemAmount;
            StringBuilder finalPricing = formattedPricing;

            TextComponent itemsSoldComponent = getTextComponentMessage("items_sold", msg -> msg
                    .replace("{earning}", finalPricing)
                    .replace("{receipt}", "")
                    .replace("{list}", String.join(", ", itemList))
                    .replace("{amount}", String.valueOf(finalItemAmount)));
            itemsSoldComponent.addExtra(" ");

            TextComponent receiptNameComponent = getTextComponentMessage("receipt_text", null);
            String receiptHoverMessage = getMessage("receipt_title", null)
                    + ChatColor.RESET
                    + String.join("\n", receiptList)
                    + ChatColor.RESET;
            receiptNameComponent.setHoverEvent(new HoverEvent(Action.SHOW_TEXT,
                    TextComponent.fromLegacyText(receiptHoverMessage)));

            sendMessage(player, Arrays.asList(itemsSoldComponent, receiptNameComponent));
        } else {
            StringBuilder finalPricing = formattedPricing;
            sendMessage(player, "items_sold", msg -> msg
                    .replace("{earning}", finalPricing)
                    .replace("{receipt}", "")
                    .replace("{list}", String.join(", ", itemList))
                    .replace("{amount}", itemAmountFormatted));
        }

        if (plugin.fileLogger != null) {
            plugin.fileLogger.info(player.getName() + " (" + player.getUniqueId() + ") sold: {"
                    + HexColorUtility.purgeAllColor(String.join(", ", receiptList)) + "}");
        }

        if (configuration.getBoolean("options.sell_titles", false)) {
            sendSellTitles(player, formattedPricing, itemAmountFormatted);
        }

        if (configuration.getBoolean("options.action_bar_msgs", false) && minorVersion >= 9) {
            sendActionBar(player, formattedPricing, itemAmountFormatted);
        }
    }

    private boolean isShulkerBox(ItemStack item) {
        return item.getType().name().endsWith("SHULKER_BOX");
    }

    private SellResult sellShulkerContents(
            ItemStack shulkerItem,
            Player player,
            Map<ItemStack, ShopItemPriceValue> priceCache,
            Map<ItemStack, Map<Short, Integer>> soldMap,
            Map<EconomyType, Double> moneyMap) {

        ItemMeta meta = shulkerItem.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm) || !(bsm.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return new SellResult(0, 0, shulkerItem);
        }

        Inventory shulkerInventory = shulkerBox.getInventory();
        double totalPrice = 0.0;
        int totalAmount = 0;

        for (ItemStack contents : shulkerInventory.getContents()) {
            if (contents == null) continue;

            double basePrice = ShopGuiPlusApi.getItemStackPriceSell(player, contents);
            if (basePrice <= 0.0) continue;

            ItemStack singleItem = new ItemStack(contents);
            singleItem.setAmount(1);

            int amount = contents.getAmount();
            @Deprecated short durability = contents.getDurability();
            EconomyType economyType = ShopHandler.getEconomyType(contents);

            double price = Optional.ofNullable(priceCache.get(singleItem))
                    .map(v -> v.getSellPrice() * amount)
                    .orElse(basePrice);

            priceCache.putIfAbsent(singleItem, new ShopItemPriceValue(economyType, price / amount));
            soldMap.computeIfAbsent(singleItem, k -> new HashMap<>()).merge(durability, amount, Integer::sum);
            moneyMap.merge(economyType, price, Double::sum);

            totalPrice += price;
            totalAmount += amount;
        }

        Inventory unsellable = Bukkit.createInventory(null, shulkerInventory.getSize());
        for (ItemStack c : shulkerInventory.getContents()) {
            if (c != null && ShopGuiPlusApi.getItemStackPriceSell(player, c) <= 0.0) {
                unsellable.addItem(c);
            }
        }

        shulkerBox.getInventory().clear();
        for (ItemStack i : unsellable.getContents()) {
            if (i != null) shulkerBox.getInventory().addItem(i);
        }

        bsm.setBlockState(shulkerBox);

        ItemStack returnShulker = shulkerItem.clone();
        returnShulker.setAmount(1);
        returnShulker.setItemMeta(bsm);

        return new SellResult(totalPrice, totalAmount, returnShulker);
    }

    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;
        long maxMemoryMB = runtime.maxMemory() / 1_048_576L;
        return String.format(Locale.US, "%s / %s MiB", usedMemoryMB, maxMemoryMB);
    }

    private void setItemDamage(ItemStack item, int damage) {
        if (VersionUtility.getMinorVersion() < 13) {
            item.setDurability((short) damage);
            return;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta instanceof Damageable damageable) {
            damageable.setDamage(damage);
            item.setItemMeta(itemMeta);
        }
    }

    private String getMessage(String path, @Nullable Function<String, String> replacer) {
        CommentedConfiguration configuration = this.plugin.getConfiguration();
        String message = configuration.getString("messages." + path, "");
        if (message.isEmpty()) return "";
        if (replacer != null) message = replacer.apply(message);
        return MessageUtility.color(HexColorUtility.replaceHexColors('&', message));
    }

    private TextComponent getTextComponentMessage(String path, @Nullable Function<String, String> replacer) {
        String message = getMessage(path, replacer);
        if (message.isEmpty()) return new TextComponent("");
        BaseComponent[] components = TextComponent.fromLegacyText(message);
        TextComponent root = new TextComponent("");
        for (BaseComponent component : components) root.addExtra(component);
        return root;
    }

    private void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, null);
    }

    private void sendMessage(CommandSender sender, String path, @Nullable Function<String, String> replacer) {
        String message = getMessage(path, replacer);
        if (message.isEmpty()) return;
        if (sender instanceof Player player) {
            player.spigot().sendMessage(TextComponent.fromLegacyText(message));
        } else {
            sender.sendMessage(message);
        }
    }

    private void sendMessage(Player player, List<TextComponent> textComponents) {
        boolean isTextPresent = textComponents.stream()
                .anyMatch(c -> (c.getText() != null && !c.getText().isEmpty())
                        || (c.getExtra() != null && !c.getExtra().isEmpty()));
        if (!isTextPresent) return;
        player.spigot().sendMessage(textComponents.toArray(new BaseComponent[0]));
    }

    private void sendSellTitles(Player player, CharSequence price, String amount) {
        Function<String, String> replacer = msg -> msg.replace("{earning}", price).replace("{amount}", amount);
        player.sendTitle(getMessage("sell_title", replacer), getMessage("sell_subtitle", replacer));
    }

    private void sendActionBar(Player player, CharSequence price, String amount) {
        Function<String, String> replacer = msg -> msg.replace("{earning}", price).replace("{amount}", amount);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, getTextComponentMessage("action_bar_items_sold", replacer));
    }
}
