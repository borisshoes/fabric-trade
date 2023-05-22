package net.borisshoes.trade;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.trade.utils.ConfigUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Trade implements ModInitializer {
   private static final Logger logger = LogManager.getLogger("Trade");
   private static final String CONFIG_NAME = "Trade.properties";
   
   private final ArrayList<TradeRequest> activeTrades = new ArrayList<>();
   private final HashMap<UUID, Long> recentRequests = new HashMap<>();
   private ConfigUtils config;
   
   @Nullable
   private static CompletableFuture<Suggestions> filterSuggestionsByInput(SuggestionsBuilder builder, List<String> values) {
      String start = builder.getRemaining().toLowerCase();
      values.stream().filter(s -> s.toLowerCase().startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   private CompletableFuture<Suggestions> getTradeInitSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      ServerCommandSource scs = context.getSource();
      
      List<String> activeTargets = Stream.concat(
            activeTrades.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()),
            activeTrades.stream().map(tradeRequest -> tradeRequest.tFrom.getName().getString())
      ).collect(Collectors.toList());
      List<String> others = Arrays.stream(scs.getServer().getPlayerNames())
            .filter(s -> !s.equals(scs.getName()) && !activeTargets.contains(s))
            .collect(Collectors.toList());
      return filterSuggestionsByInput(builder, others);
   }
   
   private CompletableFuture<Suggestions> getTradeTargetSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = activeTrades.stream().map(tradeRequest -> tradeRequest.tFrom.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private CompletableFuture<Suggestions> getTradeSenderSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = activeTrades.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   static class CooldownModeConfigValue extends ConfigUtils.IConfigValue<TradeCooldownMode> {
      public CooldownModeConfigValue(@NotNull String name, TradeCooldownMode defaultValue, @Nullable ConfigUtils.Command command) {
         super(name, defaultValue, null, command, (context, builder) -> {
            List<String> tcmValues = Arrays.stream(TradeCooldownMode.values()).map(String::valueOf).collect(Collectors.toList());
            return filterSuggestionsByInput(builder, tcmValues);
         });
      }
      
      @Override
      public TradeCooldownMode getFromProps(Properties props) {
         return TradeCooldownMode.valueOf(props.getProperty(name));
      }
      
      @Override
      public ArgumentType<?> getArgumentType() {
         return StringArgumentType.string();
      }
      
      @Override
      public TradeCooldownMode parseArgumentValue(CommandContext<ServerCommandSource> ctx) {
         return TradeCooldownMode.valueOf(StringArgumentType.getString(ctx, name));
      }
   }
   
   @Override
   public void onInitialize(){
      logger.info("Initializing Trade...");
   
      config = new ConfigUtils(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME).toFile(), logger, Arrays.asList(new ConfigUtils.IConfigValue[] {
            new ConfigUtils.IntegerConfigValue("timeout", 60, new ConfigUtils.IntegerConfigValue.IntLimits(0),
                  new ConfigUtils.Command("Timeout is %s seconds", "Timeout set to %s seconds")),
            new ConfigUtils.IntegerConfigValue("cooldown", 60, new ConfigUtils.IntegerConfigValue.IntLimits(0),
                  new ConfigUtils.Command("Cooldown is %s seconds", "Cooldown set to %s seconds")),
            new CooldownModeConfigValue("cooldown-mode", TradeCooldownMode.WhoInitiated,
                  new ConfigUtils.Command("Cooldown Mode is %s", "Cooldown Mode set to %s"))
      }));
   
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
         dispatcher.register(literal("trade")
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
      
         dispatcher.register(config.generateCommand("tradeconfig"));
      });
      
   }
   
   public int tradeInit(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo) throws CommandSyntaxException {
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if (tFrom.equals(tTo)) {
         tFrom.sendMessage(MutableText.of(new LiteralTextContent("You cannot request to trade with yourself!")).formatted(Formatting.RED), false);
         return 1;
      }
      
      if (checkCooldown(tFrom)) return 1;
      
      TradeRequest tr = new TradeRequest(tFrom, tTo, (int) config.getValue("timeout") * 1000);
      if (activeTrades.stream().anyMatch(tpaRequest -> tpaRequest.equals(tr))) {
         tFrom.sendMessage(MutableText.of(new LiteralTextContent("There is already an ongoing request like this!")).formatted(Formatting.RED), false);
         return 1;
      }
      tr.setTimeoutCallback(() -> {
         activeTrades.remove(tr);
         tFrom.sendMessage(MutableText.of(new LiteralTextContent("Your trade request to " + tTo.getName().getString() + " has timed out!")).formatted(Formatting.RED), false);
         tTo.sendMessage(MutableText.of(new LiteralTextContent("Trade request from " + tFrom.getName().getString() + " has timed out!")).formatted(Formatting.RED), false);
      });
      activeTrades.add(tr);
      
      tFrom.sendMessage(
            MutableText.of(new LiteralTextContent("You have requested to trade with ")).formatted(Formatting.GREEN)
                  .append(MutableText.of(new LiteralTextContent(tTo.getName().getString())).formatted(Formatting.AQUA))
                  .append(MutableText.of(new LiteralTextContent("\nTo cancel type ")).formatted(Formatting.GREEN))
                  .append(MutableText.of(new LiteralTextContent("/tradecancel [<player>]")).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradecancel " + tTo.getName().getString()))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("/tradecancel " + tTo.getName().getString()))))
                              .withColor(Formatting.GOLD)))
                  .append(MutableText.of(new LiteralTextContent("\nThis request will timeout in " + config.getValue("timeout") + " seconds.")).formatted(Formatting.GREEN)),
            false);
      
      tTo.sendMessage(
            MutableText.of(new LiteralTextContent(tFrom.getName().getString())).formatted(Formatting.AQUA)
                  .append(MutableText.of(new LiteralTextContent(" has requested to trade with you!")).formatted(Formatting.GREEN))
                  .append(MutableText.of(new LiteralTextContent("\nTo accept type ")).formatted(Formatting.GREEN))
                  .append(MutableText.of(new LiteralTextContent("/tradeaccept [<player>]")).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradeaccept " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("/tradeaccept " + tFrom.getName().getString()))))
                              .withColor(Formatting.GOLD)))
                  .append(MutableText.of(new LiteralTextContent("\nTo deny type ")).formatted(Formatting.GREEN))
                  .append(MutableText.of(new LiteralTextContent("/tradedeny [<player>]")).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradedeny " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("/tradedeny " + tFrom.getName().getString()))))
                              .withColor(Formatting.GOLD)))
                  .append(MutableText.of(new LiteralTextContent("\nThis request will timeout in " + config.getValue("timeout") + " seconds.")).formatted(Formatting.GREEN)),
            false);
      return 1;
   }
   
   public int tradeAccept(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom) throws CommandSyntaxException {
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         TradeRequest[] candidates;
         candidates = activeTrades.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableText text = MutableText.of(new LiteralTextContent("You currently have multiple active trade requests! Please specify whose request to accept.\n")).formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tFrom.getName().getString()).forEach(name ->
                  text.append(MutableText.of(new LiteralTextContent(name)).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradeaccept " + name))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("/tradeaccept " + name))))
                              .withColor(Formatting.GOLD))).append(" "));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.sendMessage(MutableText.of(new LiteralTextContent("You currently don't have any trade requests!")).formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
   
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.ACCEPT);
      if (tr == null) return 1;
      
      // Do the trade thing
      new TradeSession(tFrom,tTo,tr,this);
      
      tr.cancelTimeout();
      activeTrades.remove(tr);
      tr.tTo.sendMessage(MutableText.of(new LiteralTextContent("You have accepted the trade request!")), false);
      tr.tFrom.sendMessage(MutableText.of(new LiteralTextContent(tr.tTo.getName().getString())).formatted(Formatting.AQUA)
            .append(MutableText.of(new LiteralTextContent(" has accepted the trade request!")).formatted(Formatting.GREEN)), false);
      return 1;
   }
   
   
   public int tradeDeny(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom) throws CommandSyntaxException{
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         TradeRequest[] candidates;
         candidates = activeTrades.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableText text = MutableText.of(new LiteralTextContent("You currently have multiple active trade requests! Please specify whose request to deny.\n")).formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tFrom.getName().getString()).forEach(name ->
                  text.append(MutableText.of(new LiteralTextContent(name)).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradedeny " + name))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("/tradedeny " + name))))
                              .withColor(Formatting.GOLD))).append(" "));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.sendMessage(MutableText.of(new LiteralTextContent("You currently don't have any trade requests!")).formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
   
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.DENY);
      if (tr == null) return 1;
      tr.cancelTimeout();
      activeTrades.remove(tr);
      tr.tTo.sendMessage(MutableText.of(new LiteralTextContent("You have cancelled the trade request!")), false);
      tr.tFrom.sendMessage(MutableText.of(new LiteralTextContent(tr.tTo.getName().getString())).formatted(Formatting.AQUA)
            .append(MutableText.of(new LiteralTextContent(" has cancelled the trade request!")).formatted(Formatting.RED)), false);
      return 1;
   }
   
   public int tradeCancel(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo) throws CommandSyntaxException {
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if (tTo == null) {
         TradeRequest[] candidates;
         candidates = activeTrades.stream().filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom)).toArray(TradeRequest[]::new);
         if (candidates.length > 1) {
            MutableText text = MutableText.of(new LiteralTextContent("You currently have multiple active trade requests! Please specify which request to cancel.\n")).formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(tpaRequest -> tpaRequest.tTo.getName().getString()).forEach(name ->
                  text.append(MutableText.of(new LiteralTextContent(name)).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradecancel " + name))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("/tradecancel " + name))))
                              .withColor(Formatting.GOLD))).append(" "));
            tFrom.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tFrom.sendMessage(MutableText.of(new LiteralTextContent("You currently don't have any trade requests!")).formatted(Formatting.RED), false);
            return 1;
         }
         tTo = candidates[0].tTo;
      }
      
      TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.CANCEL);
      if (tr == null) return 1;
      tr.cancelTimeout();
      activeTrades.remove(tr);
      tr.tFrom.sendMessage(MutableText.of(new LiteralTextContent("You have cancelled the trade request!")).formatted(Formatting.RED), false);
      tr.tTo.sendMessage(MutableText.of(new LiteralTextContent(tr.tFrom.getName().getString())).formatted(Formatting.AQUA)
            .append(MutableText.of(new LiteralTextContent(" has cancelled the trade request!")).formatted(Formatting.RED)), false);
      return 1;
   }
   
   private TradeRequest getTradeRequest(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, TradeAction action) {
      Optional<TradeRequest> otr = activeTrades.stream()
            .filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom) && tpaRequest.tTo.equals(tTo)).findFirst();
      
      if (otr.isEmpty()) {
         if (action == TradeAction.CANCEL) {
            tFrom.sendMessage(MutableText.of(new LiteralTextContent("No ongoing request!")).formatted(Formatting.RED), false);
         } else {
            tTo.sendMessage(MutableText.of(new LiteralTextContent("No ongoing request!")).formatted(Formatting.RED), false);
         }
         return null;
      }
      
      return otr.get();
   }
   
   
   private boolean checkCooldown(ServerPlayerEntity tFrom) {
      if (recentRequests.containsKey(tFrom.getUuid())) {
         long diff = Instant.now().getEpochSecond() - recentRequests.get(tFrom.getUuid());
         if (diff < (int) config.getValue("cooldown")) {
            tFrom.sendMessage(MutableText.of(new LiteralTextContent("You cannot make a trade request for ")).append(String.valueOf((int) config.getValue("cooldown") - diff))
                  .append(" more seconds!").formatted(Formatting.RED), false);
            return true;
         }
      }
      return false;
   }
   
   private enum TradeAction {
      ACCEPT, DENY, CANCEL
   }
   
   static class TradeRequest {
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
         this.tFrom = tFrom.server.getPlayerManager().getPlayer(tFrom.getUuid());
         this.tTo = tTo.server.getPlayerManager().getPlayer(tTo.getUuid());
         assert tFrom != null && tTo != null;
      }
   }
   
   enum TradeCooldownMode {
      WhoInitiated, BothUsers
   }
   
   interface Timeout {
      void onTimeout();
   }
   
   public void completeSession(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, TradeRequest tr){
      switch ((TradeCooldownMode) config.getValue("cooldown-mode")) {
         case BothUsers -> {
            recentRequests.put(tr.tFrom.getUuid(), Instant.now().getEpochSecond());
            recentRequests.put(tr.tTo.getUuid(), Instant.now().getEpochSecond());
         }
         case WhoInitiated -> recentRequests.put(tr.tFrom.getUuid(), Instant.now().getEpochSecond());
      }
   }
}
