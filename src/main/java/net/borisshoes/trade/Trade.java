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
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.borisshoes.borislib.BorisLib.registerGraphicItem;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Trade implements ModInitializer {
   private static final Logger logger = LogManager.getLogger("Trade");
   private static final String CONFIG_NAME = "Trade.properties";
   public static final String MOD_ID = "trade";
   public static final Registry<IConfigSetting<?>> CONFIG_SETTINGS = new SimpleRegistry<>(RegistryKey.ofRegistry(Identifier.of(MOD_ID,"config_settings")), Lifecycle.stable());
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
      Registry.register(CONFIG_SETTINGS,Identifier.of(MOD_ID,setting.getId()),setting);
      return setting;
   }
   
   public static final GraphicalItem.GraphicElement GREEN_CONFIRM = registerGraphicItem(new GraphicalItem.GraphicElement(Identifier.of(MOD_ID, "trade_confirm_green"), Items.GREEN_CONCRETE, false));
   public static final GraphicalItem.GraphicElement YELLOW_CONFIRM = registerGraphicItem(new GraphicalItem.GraphicElement(Identifier.of(MOD_ID, "trade_confirm_yellow"), Items.YELLOW_CONCRETE, false));
   public static final GraphicalItem.GraphicElement RED_CONFIRM = registerGraphicItem(new GraphicalItem.GraphicElement(Identifier.of(MOD_ID, "trade_confirm_red"), Items.RED_CONCRETE, false));
   
   @Nullable
   private static CompletableFuture<Suggestions> filterSuggestionsByInput(SuggestionsBuilder builder, List<String> values) {
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   private CompletableFuture<Suggestions> getTradeInitSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      ServerCommandSource scs = context.getSource();
      
      List<String> activeTargets = Stream.concat(
            ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()),
            ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tFrom.getName().getString())
      ).toList();
      List<String> others = Arrays.stream(scs.getServer().getPlayerNames())
            .filter(s -> !s.equals(scs.getName()) && !activeTargets.contains(s))
            .collect(Collectors.toList());
      return filterSuggestionsByInput(builder, others);
   }
   
   private CompletableFuture<Suggestions> getTradeTargetSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tFrom.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private CompletableFuture<Suggestions> getTradeSenderSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = ACTIVE_TRADES.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   @Override
   public void onInitialize(){
      logger.info("Initializing Trade...");
      CONFIG = new ConfigManager(MOD_ID,"Trade",CONFIG_NAME,CONFIG_SETTINGS);
      
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
         dispatcher.register(literal("trade").executes(Trade::openTradeSelector)
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTradeInitSuggestions)
                     .executes(ctx -> tradeInit(ctx, getPlayer(ctx, "target")))));
      
         dispatcher.register(literal("tradeaccept")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTradeTargetSuggestions)
                     .executes(ctx -> tradeAccept(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tradeAccept(ctx, null)));
      
         dispatcher.register(literal("tradedeny")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTradeTargetSuggestions)
                     .executes(ctx -> tradeDeny(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tradeDeny(ctx, null)));
         
         dispatcher.register(literal("tradecancel")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTradeSenderSuggestions)
                     .executes(ctx -> tradeCancel(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tradeCancel(ctx, null)));
         
         dispatcher.register(CONFIG.generateCommand("trademod","config"));
      });
      
      PolymerResourcePackUtils.addModAssets(MOD_ID);
   }
   
   private static int openTradeSelector(CommandContext<ServerCommandSource> ctx){
      final ServerPlayerEntity trader = ctx.getSource().getPlayer();
      if(trader == null){
         ctx.getSource().sendMessage(Text.translatable("text.trade.must_be_player").formatted(Formatting.RED));
         return -1;
      }
      TradeSelectionGui gui = new TradeSelectionGui(trader);
      return 1;
   }
   
   public static int tradeInit(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo) throws CommandSyntaxException {
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      if(tFrom == null){
         ctx.getSource().sendMessage(Text.translatable("text.trade.must_be_player").formatted(Formatting.RED));
         return -1;
      }
      return tradeInit(tFrom,tTo);
   }
   
   public static int tradeInit(ServerPlayerEntity tFrom, ServerPlayerEntity tTo){
      if(tFrom == null){
         return -1;
      }
      
      if (tFrom.equals(tTo)) {
         tFrom.sendMessage(Text.translatable("text.trade.cannot_trade_self").formatted(Formatting.RED), false);
         return -1;
      }
      
      if (checkCooldown(tFrom)) return 1;
      
      TradeRequest tr = new TradeRequest(tFrom, tTo, CONFIG.getInt(TIMEOUT_CFG) * 1000);
      if (ACTIVE_TRADES.stream().anyMatch(tpaRequest -> tpaRequest.equals(tr))) {
         tFrom.sendMessage(Text.translatable("text.trade.already_requested").formatted(Formatting.RED), false);
         return -1;
      }
      tr.setTimeoutCallback(() -> {
         ACTIVE_TRADES.remove(tr);
         tFrom.sendMessage(Text.translatable("text.trade.trade_to_timeout",tTo.getName().getString()).formatted(Formatting.RED), false);
         tTo.sendMessage(Text.translatable("text.trade.trade_from_timeout",tFrom.getName().getString()).formatted(Formatting.RED), false);
      });
      ACTIVE_TRADES.add(tr);
      
      tFrom.sendMessage(Text.translatable("text.trade.you_requested",
                  Text.literal(tTo.getName().getString()).formatted(Formatting.AQUA),
                  Text.literal("/tradecancel [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradecancel " + tTo.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tradecancel " + tTo.getName().getString())))
                              .withColor(Formatting.GOLD)),
                  Text.literal(TextUtils.readableInt(CONFIG.getInt(TIMEOUT_CFG)))
            ).formatted(Formatting.GREEN),
            false);
      
      tTo.sendMessage(
            Text.translatable("text.trade.they_requested",
                  Text.literal(tFrom.getName().getString()).formatted(Formatting.AQUA),
                  Text.literal("/tradeaccept [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradeaccept " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tradeaccept " + tFrom.getName().getString())))
                              .withColor(Formatting.GOLD)),
                  Text.literal("/tradedeny [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradedeny " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tradedeny " + tFrom.getName().getString())))
                              .withColor(Formatting.GOLD)),
                  Text.literal(TextUtils.readableInt(CONFIG.getInt(TIMEOUT_CFG)))
            ).formatted(Formatting.GREEN),
            false);
      return 1;
   }
   
   public static int tradeAccept(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom) throws CommandSyntaxException {
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      if(tTo == null){
         ctx.getSource().sendMessage(Text.translatable("text.trade.must_be_player").formatted(Formatting.RED));
         return -1;
      }
      
      if (tFrom == null) {
         TradeRequest[] candidates;
         candidates = ACTIVE_TRADES.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableText text = Text.translatable("text.trade.accept_specify").append(Text.literal("\n")).formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tFrom.getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradeaccept " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tradeaccept " + name)))
                              .withColor(Formatting.GOLD))).append(" "));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.sendMessage(Text.translatable("text.trade.no_requests").formatted(Formatting.RED), false);
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
      
      tr.tTo.sendMessage(Text.translatable("text.trade.you_accept"), false);
      tr.tFrom.sendMessage(Text.translatable("text.trade.they_accept",Text.literal(tr.tTo.getName().getString()).formatted(Formatting.AQUA)).formatted(Formatting.GREEN), false);
      return 1;
   }
   
   
   public static int tradeDeny(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom) throws CommandSyntaxException{
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         TradeRequest[] candidates;
         candidates = ACTIVE_TRADES.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableText text = Text.translatable("text.trade.deny_specify").append(Text.literal("\n")).formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tFrom.getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradedeny " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tradedeny " + name)))
                              .withColor(Formatting.GOLD))).append(" "));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.sendMessage(Text.translatable("text.trade.no_requests").formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
   
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.DENY);
      if (tr == null) return 1;
      tr.cancelTimeout();
      ACTIVE_TRADES.remove(tr);
      tr.tTo.sendMessage(Text.translatable("gui.trade.you_cancelled"), false);
      tr.tFrom.sendMessage(Text.translatable("gui.trade.they_cancelled",Text.literal(tr.tTo.getName().getString()).formatted(Formatting.AQUA)).formatted(Formatting.RED), false);
      return 1;
   }
   
   public static int tradeCancel(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo) throws CommandSyntaxException {
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if (tTo == null) {
         TradeRequest[] candidates;
         candidates = ACTIVE_TRADES.stream().filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            
            MutableText text = Text.translatable("text.trade.cancel_specify").append(Text.literal("\n")).formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tTo.getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tradecancel " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tradecancel " + name)))
                              .withColor(Formatting.GOLD))).append(" "));
            tFrom.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tFrom.sendMessage(Text.translatable("text.trade.no_requests").formatted(Formatting.RED), false);
            return 1;
         }
         tTo = candidates[0].tTo;
      }
      
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.CANCEL);
      if (tr == null) return 1;
      tr.cancelTimeout();
      ACTIVE_TRADES.remove(tr);
      tr.tFrom.sendMessage(Text.translatable("gui.trade.you_cancelled").formatted(Formatting.RED), false);
      tr.tTo.sendMessage(Text.translatable("gui.trade.they_cancelled",Text.literal(tr.tFrom.getName().getString()).formatted(Formatting.AQUA)).formatted(Formatting.RED), false);
      return 1;
   }
   
   private static TradeRequest getTradeRequest(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, TradeAction action) {
      Optional<TradeRequest> otr = ACTIVE_TRADES.stream()
            .filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom) && tpaRequest.tTo.equals(tTo)).findFirst();
      
      if (otr.isEmpty()) {
         if (action == TradeAction.CANCEL) {
            tFrom.sendMessage(Text.translatable("text.trade.no_request").formatted(Formatting.RED), false);
         } else {
            tTo.sendMessage(Text.translatable("text.trade.no_request").formatted(Formatting.RED), false);
         }
         return null;
      }
      
      return otr.get();
   }
   
   
   private static boolean checkCooldown(ServerPlayerEntity tFrom) {
      if (RECENT_REQUESTS.containsKey(tFrom.getUuid())) {
         long diff = Instant.now().getEpochSecond() - RECENT_REQUESTS.get(tFrom.getUuid());
         if (diff < CONFIG.getInt(COOLDOWN_CFG)) {
            tFrom.sendMessage(Text.translatable("text.trade.on_cooldown", TextUtils.readableInt((int) (CONFIG.getInt(COOLDOWN_CFG) - diff))).formatted(Formatting.RED), false);
            return true;
         }
      }
      return false;
   }
   
   private enum TradeAction {
      ACCEPT, DENY, CANCEL
   }
   
   public static class TradeRequest {
      ServerPlayerEntity tFrom;
      ServerPlayerEntity tTo;
      
      long timeout;
      
      Timer timer;
      
      public TradeRequest(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, int timeoutMS) {
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
         this.tFrom = tFrom.getEntityWorld().getServer().getPlayerManager().getPlayer(tFrom.getUuid());
         this.tTo = tTo.getEntityWorld().getServer().getPlayerManager().getPlayer(tTo.getUuid());
         assert tFrom != null && tTo != null;
      }
   }
   
   enum TradeCooldownMode implements StringIdentifiable {
      WHO_INITIATED("WHO_INITIATED"), BOTH_USERS("BOTH_USERS");
      
      private final String name;
      
      TradeCooldownMode(String name){
         this.name = name;
      }
      
      @Override
      public String asString(){
         return name;
      }
   }
   
   interface Timeout {
      void onTimeout();
   }
   
   public static void completeSession(TradeRequest tr){
      switch ((TradeCooldownMode) CONFIG.getValue(COOLDOWN_MODE_CFG.getName())) {
         case BOTH_USERS -> {
            RECENT_REQUESTS.put(tr.tFrom.getUuid(), Instant.now().getEpochSecond());
            RECENT_REQUESTS.put(tr.tTo.getUuid(), Instant.now().getEpochSecond());
         }
         case WHO_INITIATED -> RECENT_REQUESTS.put(tr.tFrom.getUuid(), Instant.now().getEpochSecond());
      }
   }
}
