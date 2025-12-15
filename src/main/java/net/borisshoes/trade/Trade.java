package net.borisshoes.trade;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Lifecycle;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.config.ConfigManager;
import net.borisshoes.borislib.config.ConfigSetting;
import net.borisshoes.borislib.config.IConfigSetting;
import net.borisshoes.borislib.config.values.EnumConfigValue;
import net.borisshoes.borislib.config.values.IntConfigValue;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.utils.TextUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.item.Items;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.MappedRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.borisshoes.borislib.BorisLib.registerGraphicItem;
import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Trade implements ModInitializer {
   private static final Logger logger = LogManager.getLogger("Trade");
   private static final String CONFIG_NAME = "Trade.properties";
   public static final String MOD_ID = "trade";
   public static final Registry<IConfigSetting<?>> CONFIG_SETTINGS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID,"config_settings")), Lifecycle.stable());
   private static final ArrayList<TradeRequest> ACTIVE_TRADES = new ArrayList<>();
   private static final HashMap<UUID, Long> RECENT_REQUESTS = new HashMap<>();
   public static ConfigManager CONFIG;
   
   public static final IConfigSetting<?> TIMEOUT_CFG = registerConfigSetting(new ConfigSetting<>(
         new IntConfigValue("timeout", 60, new IntConfigValue.IntLimits(0))));
   
   public static final IConfigSetting<?> COOLDOWN_CFG = registerConfigSetting(new ConfigSetting<>(
         new IntConfigValue("cooldown", 60, new IntConfigValue.IntLimits(0))));
   
   public static final IConfigSetting<?> COOLDOWN_MODE_CFG = registerConfigSetting(new ConfigSetting<>(
         new EnumConfigValue<>("cooldown-mode", TradeCooldownMode.WHO_INITIATED, TradeCooldownMode.class)));
   
   private static IConfigSetting<?> registerConfigSetting(IConfigSetting<?> setting){
      Registry.register(CONFIG_SETTINGS, Identifier.fromNamespaceAndPath(MOD_ID,setting.getId()),setting);
      return setting;
   }
   
   public static final GraphicalItem.GraphicElement GREEN_CONFIRM = registerGraphicItem(new GraphicalItem.GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "trade_confirm_green"), Items.GREEN_CONCRETE, false));
   public static final GraphicalItem.GraphicElement YELLOW_CONFIRM = registerGraphicItem(new GraphicalItem.GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "trade_confirm_yellow"), Items.YELLOW_CONCRETE, false));
   public static final GraphicalItem.GraphicElement RED_CONFIRM = registerGraphicItem(new GraphicalItem.GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "trade_confirm_red"), Items.RED_CONCRETE, false));
   
   @Nullable
   private static CompletableFuture<Suggestions> filterSuggestionsByInput(SuggestionsBuilder builder, List<String> values) {
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   private CompletableFuture<Suggestions> getTradeInitSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      CommandSourceStack scs = context.getSource();
      
      List<String> activeTargets = Stream.concat(
            ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()),
            ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tFrom.getName().getString())
      ).toList();
      List<String> others = Arrays.stream(scs.getServer().getPlayerNames())
            .filter(s -> !s.equals(scs.getTextName()) && !activeTargets.contains(s))
            .collect(Collectors.toList());
      return filterSuggestionsByInput(builder, others);
   }
   
   private CompletableFuture<Suggestions> getTradeTargetSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      List<String> activeTargets = ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tFrom.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private CompletableFuture<Suggestions> getTradeSenderSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      List<String> activeTargets = ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   @Override
   public void onInitialize(){
      logger.info("Initializing Trade...");
      CONFIG = new ConfigManager(MOD_ID,"Trade",CONFIG_NAME,CONFIG_SETTINGS);
      
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
         dispatcher.register(literal("trade").executes(Trade::openTradeSelector)
               .then(argument("target", EntityArgument.player()).suggests(this::getTradeInitSuggestions)
                     .executes(ctx -> tradeInit(ctx, getPlayer(ctx, "target")))));
      
         dispatcher.register(literal("tradeaccept")
               .then(argument("target", EntityArgument.player()).suggests(this::getTradeTargetSuggestions)
                     .executes(ctx -> tradeAccept(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tradeAccept(ctx, null)));
      
         dispatcher.register(literal("tradedeny")
               .then(argument("target", EntityArgument.player()).suggests(this::getTradeTargetSuggestions)
                     .executes(ctx -> tradeDeny(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tradeDeny(ctx, null)));
         
         dispatcher.register(literal("tradecancel")
               .then(argument("target", EntityArgument.player()).suggests(this::getTradeSenderSuggestions)
                     .executes(ctx -> tradeCancel(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tradeCancel(ctx, null)));
         
         dispatcher.register(CONFIG.generateCommand("tradeconfig",""));
      });
      
      PolymerResourcePackUtils.addModAssets(MOD_ID);
   }
   
   private static int openTradeSelector(CommandContext<CommandSourceStack> ctx){
      final ServerPlayer trader = ctx.getSource().getPlayer();
      if(trader == null){
         ctx.getSource().sendSystemMessage(Component.translatable("text.trade.must_be_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      TradeSelectionGui gui = new TradeSelectionGui(trader);
      return 1;
   }
   
   public static int tradeInit(CommandContext<CommandSourceStack> ctx, ServerPlayer tTo) throws CommandSyntaxException {
      final ServerPlayer tFrom = ctx.getSource().getPlayer();
      if(tFrom == null){
         ctx.getSource().sendSystemMessage(Component.translatable("text.trade.must_be_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      return tradeInit(tFrom,tTo);
   }
   
   public static int tradeInit(ServerPlayer tFrom, ServerPlayer tTo){
      if(tFrom == null){
         return -1;
      }
      
      if (tFrom.equals(tTo)) {
         tFrom.displayClientMessage(Component.translatable("text.trade.cannot_trade_self").withStyle(ChatFormatting.RED), false);
         return -1;
      }
      
      if (checkCooldown(tFrom)) return 1;
      
      TradeRequest tr = new TradeRequest(tFrom, tTo, CONFIG.getInt(TIMEOUT_CFG) * 1000);
      if (ACTIVE_TRADES.stream().anyMatch(tpaRequest -> tpaRequest.equals(tr))) {
         tFrom.displayClientMessage(Component.translatable("text.trade.already_requested").withStyle(ChatFormatting.RED), false);
         return -1;
      }
      tr.setTimeoutCallback(() -> {
         ACTIVE_TRADES.remove(tr);
         tFrom.displayClientMessage(Component.translatable("text.trade.trade_to_timeout",tTo.getName().getString()).withStyle(ChatFormatting.RED), false);
         tTo.displayClientMessage(Component.translatable("text.trade.trade_from_timeout",tFrom.getName().getString()).withStyle(ChatFormatting.RED), false);
      });
      ACTIVE_TRADES.add(tr);
      
      tFrom.displayClientMessage(Component.translatable("text.trade.you_requested",
                  Component.literal(tTo.getName().getString()).withStyle(ChatFormatting.AQUA),
                  Component.literal("/tradecancel [<player>]").withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradecancel " + tTo.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradecancel " + tTo.getName().getString())))
                              .withColor(ChatFormatting.GOLD)),
                  Component.literal(TextUtils.readableInt(CONFIG.getInt(TIMEOUT_CFG)))
            ).withStyle(ChatFormatting.GREEN),
            false);
      
      tTo.displayClientMessage(
            Component.translatable("text.trade.they_requested",
                  Component.literal(tFrom.getName().getString()).withStyle(ChatFormatting.AQUA),
                  Component.literal("/tradeaccept [<player>]").withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradeaccept " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradeaccept " + tFrom.getName().getString())))
                              .withColor(ChatFormatting.GOLD)),
                  Component.literal("/tradedeny [<player>]").withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradedeny " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradedeny " + tFrom.getName().getString())))
                              .withColor(ChatFormatting.GOLD)),
                  Component.literal(TextUtils.readableInt(CONFIG.getInt(TIMEOUT_CFG)))
            ).withStyle(ChatFormatting.GREEN),
            false);
      return 1;
   }
   
   public static int tradeAccept(CommandContext<CommandSourceStack> ctx, ServerPlayer tFrom) throws CommandSyntaxException {
      final ServerPlayer tTo = ctx.getSource().getPlayer();
      if(tTo == null){
         ctx.getSource().sendSystemMessage(Component.translatable("text.trade.must_be_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      
      if (tFrom == null) {
         TradeRequest[] candidates;
         candidates = ACTIVE_TRADES.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableComponent text = Component.translatable("text.trade.accept_specify").append(Component.literal("\n")).withStyle(ChatFormatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tFrom.getName().getString()).forEach(name ->
                  text.append(Component.literal(name).withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradeaccept " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradeaccept " + name)))
                              .withColor(ChatFormatting.GOLD))).append(" "));
            tTo.displayClientMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.displayClientMessage(Component.translatable("text.trade.no_requests").withStyle(ChatFormatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
   
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.ACCEPT);
      if (tr == null) return 1;
      
      // Do the trade thing
      new TradeSession(tFrom,tTo,tr);
      
      tr.cancelTimeout();
      ACTIVE_TRADES.remove(tr);
      
      tr.tTo.displayClientMessage(Component.translatable("text.trade.you_accept"), false);
      tr.tFrom.displayClientMessage(Component.translatable("text.trade.they_accept", Component.literal(tr.tTo.getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GREEN), false);
      return 1;
   }
   
   
   public static int tradeDeny(CommandContext<CommandSourceStack> ctx, ServerPlayer tFrom) throws CommandSyntaxException{
      final ServerPlayer tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         TradeRequest[] candidates;
         candidates = ACTIVE_TRADES.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableComponent text = Component.translatable("text.trade.deny_specify").append(Component.literal("\n")).withStyle(ChatFormatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tFrom.getName().getString()).forEach(name ->
                  text.append(Component.literal(name).withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradedeny " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradedeny " + name)))
                              .withColor(ChatFormatting.GOLD))).append(" "));
            tTo.displayClientMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.displayClientMessage(Component.translatable("text.trade.no_requests").withStyle(ChatFormatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
   
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.DENY);
      if (tr == null) return 1;
      tr.cancelTimeout();
      ACTIVE_TRADES.remove(tr);
      tr.tTo.displayClientMessage(Component.translatable("gui.trade.you_cancelled"), false);
      tr.tFrom.displayClientMessage(Component.translatable("gui.trade.they_cancelled", Component.literal(tr.tTo.getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.RED), false);
      return 1;
   }
   
   public static int tradeCancel(CommandContext<CommandSourceStack> ctx, ServerPlayer tTo) throws CommandSyntaxException {
      final ServerPlayer tFrom = ctx.getSource().getPlayer();
      
      if (tTo == null) {
         TradeRequest[] candidates;
         candidates = ACTIVE_TRADES.stream().filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            
            MutableComponent text = Component.translatable("text.trade.cancel_specify").append(Component.literal("\n")).withStyle(ChatFormatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tTo.getName().getString()).forEach(name ->
                  text.append(Component.literal(name).withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradecancel " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradecancel " + name)))
                              .withColor(ChatFormatting.GOLD))).append(" "));
            tFrom.displayClientMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tFrom.displayClientMessage(Component.translatable("text.trade.no_requests").withStyle(ChatFormatting.RED), false);
            return 1;
         }
         tTo = candidates[0].tTo;
      }
      
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.CANCEL);
      if (tr == null) return 1;
      tr.cancelTimeout();
      ACTIVE_TRADES.remove(tr);
      tr.tFrom.displayClientMessage(Component.translatable("gui.trade.you_cancelled").withStyle(ChatFormatting.RED), false);
      tr.tTo.displayClientMessage(Component.translatable("gui.trade.they_cancelled", Component.literal(tr.tFrom.getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.RED), false);
      return 1;
   }
   
   private static TradeRequest getTradeRequest(ServerPlayer tFrom, ServerPlayer tTo, TradeAction action) {
      Optional<TradeRequest> otr = ACTIVE_TRADES.stream()
            .filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom) && tpaRequest.tTo.equals(tTo)).findFirst();
      
      if (otr.isEmpty()) {
         if (action == TradeAction.CANCEL) {
            tFrom.displayClientMessage(Component.translatable("text.trade.no_request").withStyle(ChatFormatting.RED), false);
         } else {
            tTo.displayClientMessage(Component.translatable("text.trade.no_request").withStyle(ChatFormatting.RED), false);
         }
         return null;
      }
      
      return otr.get();
   }
   
   
   private static boolean checkCooldown(ServerPlayer tFrom) {
      if (RECENT_REQUESTS.containsKey(tFrom.getUUID())) {
         long diff = Instant.now().getEpochSecond() - RECENT_REQUESTS.get(tFrom.getUUID());
         if (diff < CONFIG.getInt(COOLDOWN_CFG)) {
            tFrom.displayClientMessage(Component.translatable("text.trade.on_cooldown", TextUtils.readableInt((int) (CONFIG.getInt(COOLDOWN_CFG) - diff))).withStyle(ChatFormatting.RED), false);
            return true;
         }
      }
      return false;
   }
   
   private enum TradeAction {
      ACCEPT, DENY, CANCEL
   }
   
   public static class TradeRequest {
      ServerPlayer tFrom;
      ServerPlayer tTo;
      
      long timeout;
      
      Timer timer;
      
      public TradeRequest(ServerPlayer tFrom, ServerPlayer tTo, int timeoutMS) {
         this.tFrom = tFrom;
         this.tTo = tTo;
         this.timeout = timeoutMS;
      }
      
      void setTimeoutCallback(Timeout callback) {
         timer = new Timer();
         timer.schedule(new TimerTask() {
            @Override
            public void run() {
               callback.onTimeout();
            }
         }, timeout);
      }
      
      void cancelTimeout() {
         timer.cancel();
      }
      
      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         TradeRequest that = (TradeRequest) o;
         return tFrom.equals(that.tFrom) &&
               tTo.equals(that.tTo);
      }
      
      @Override
      public int hashCode() {
         return Objects.hash(tFrom, tTo);
      }
      
      @Override
      public String toString() {
         return "TradeRequest{" + "tFrom=" + tFrom +
               ", tTo=" + tTo +
               '}';
      }
      
      public void refreshPlayers() {
         this.tFrom = tFrom.level().getServer().getPlayerList().getPlayer(tFrom.getUUID());
         this.tTo = tTo.level().getServer().getPlayerList().getPlayer(tTo.getUUID());
         assert tFrom != null && tTo != null;
      }
   }
   
   enum TradeCooldownMode implements StringRepresentable {
      WHO_INITIATED("WHO_INITIATED"), BOTH_USERS("BOTH_USERS");
      
      private final String name;
      
      TradeCooldownMode(String name){
         this.name = name;
      }
      
      @Override
      public String getSerializedName(){
         return name;
      }
   }
   
   interface Timeout {
      void onTimeout();
   }
   
   public static void completeSession(TradeRequest tr){
      switch ((TradeCooldownMode) CONFIG.getValue(COOLDOWN_MODE_CFG.getName())) {
         case BOTH_USERS -> {
            RECENT_REQUESTS.put(tr.tFrom.getUUID(), Instant.now().getEpochSecond());
            RECENT_REQUESTS.put(tr.tTo.getUUID(), Instant.now().getEpochSecond());
         }
         case WHO_INITIATED -> RECENT_REQUESTS.put(tr.tFrom.getUUID(), Instant.now().getEpochSecond());
      }
   }
}
