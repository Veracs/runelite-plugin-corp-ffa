package com.corpffa;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.inject.Inject;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class CorpFfaOverlay extends OverlayPanel {
    private CorpFfaPlugin plugin;
    private CorpFfaConfig config;
    private Client client;

    @Inject
    public CorpFfaOverlay(CorpFfaPlugin plugin, Client client, CorpFfaConfig config) {
        super(plugin);

        setPosition(OverlayPosition.DYNAMIC);
        setPosition(OverlayPosition.DETACHED);
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredSize(new Dimension(100, 600));
        this.plugin = plugin;
        this.client = client;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics2D) {
        List<LayoutableRenderableEntity> renderableEntities = panelComponent.getChildren();
        renderableEntities.clear();
        Rectangle overlayPosition = super.getBounds();

        List<Entry<Player, CorpFfaPlugin.PlayerState>> playerStates = new ArrayList<>(plugin.PlayersInCave.entrySet());

        if (playerStates.size() == 0) {
            return super.render(graphics2D);
        }

        long numberOfSpeccedPlayers = playerStates.stream().filter(playerEntry -> playerEntry.getValue().SpecCount >= 2).count();
        boolean shouldHaveSpecced = numberOfSpeccedPlayers > 5;


        // Sort list alphabetically
        playerStates.sort((player1, player2) -> {
            String playerName1 = player1.getKey().getName();
            String playerName2 = player2.getKey().getName();
            CorpFfaPlugin.PlayerState playerState1 = player1.getValue();
            CorpFfaPlugin.PlayerState playerState2 = player2.getValue();

            if (config.groupRangers() && !(playerState1.IsRanger && playerState2.IsRanger)) {
                if (playerState1.IsRanger) {
                    return playerName1.compareToIgnoreCase("0000000000000000");
                }
                if (playerState2.IsRanger) {
                    return "0000000000000000".compareToIgnoreCase(playerName2);
                }
            }

            return playerName1.compareToIgnoreCase(playerName2);
        });

        renderableEntities.add(TitleComponent.builder().text("Corp FFA").color(config.defaultColor()).build());
        for (Entry<Player, CorpFfaPlugin.PlayerState> entry : playerStates) {
            CorpFfaPlugin.PlayerState playerState = entry.getValue();
            Player player = entry.getKey();

            boolean hasBannedGear = playerState.BannedGear.size() > 0;
            boolean hasSpecced = playerState.SpecCount >= 2;
            boolean allGood = !hasBannedGear && hasSpecced;
            boolean isNonSpeccer = !hasSpecced && shouldHaveSpecced && !playerState.IsRanger;

            String rightLabel = playerState.SpecCount + "";
            Color playerColor = config.defaultColor();
            boolean shouldRender = true;

            if (playerState.HasLeft){
                playerColor = config.gonePlayerColor();
                rightLabel = "-";
                if (config.hideTeledPlayers()){
                    shouldRender = false;
                }

            } else if (hasBannedGear && config.bannedItemCountToShow() > 0) {
                List<String> itemNames = playerState.BannedGear
                        .stream()
                        .limit(config.bannedItemCountToShow())
                        .map(gearId -> client.getItemDefinition(gearId).getName())
                        .collect(Collectors.toList());
                rightLabel = String.join(", ", itemNames);

                highlightPlayer(graphics2D, player, rightLabel, config.cheaterColor(), overlayPosition.x, overlayPosition.y);

                playerColor = config.cheaterColor();

            } else if (isNonSpeccer)  {
                playerColor = config.cheaterColor();
                highlightPlayer(graphics2D, player, playerState.SpecCount + " spec", config.cheaterColor(), overlayPosition.x, overlayPosition.y);

            } else if (playerState.IsRanger) {
                rightLabel = "Ranger";
                playerColor = config.rangerColor();
                if (config.hideRangers()) {
                    shouldRender = false;
                }

            } else if (allGood) {
                playerColor = config.goodColor();
                if (config.hideGoodPlayers()) {
                    shouldRender = false;
                }
            }

            String leftLabel = player.getName();
            if (playerState.IsTagged){
                leftLabel += "*";
                playerColor = Color.ORANGE;
            }

            if (shouldRender) {
                renderableEntities.add(
                        LineComponent.builder()
                                .leftColor(playerColor).left(leftLabel)
                                .rightColor(playerColor).right(rightLabel)
                                .build()
                );
            }

        }

        if (!config.hidePlayerCount()) {
            long playerCount = plugin.PlayersInCave.size();

            String playerCountText = playerCount + "";
            if (config.splitRangersInPlayerCount()) {
                long rangerCount = plugin.PlayersInCave.values().stream().filter(o -> o.IsRanger).count();
                playerCountText = (playerCount - rangerCount) + " (+" + rangerCount + ")";
            }
            renderableEntities.add(
                    LineComponent.builder()
                            .leftColor(config.playerCountColor()).left("Players")
                            .rightColor(config.playerCountColor()).right(playerCountText)
                            .build()
            );
        }

        return super.render(graphics2D);
    }


    /**
     * Highlight a player with text
     *
     * @param graphics    Graphics object
     * @param actor       The players to highlight
     * @param text        The text to show
     * @param color       The color of the txt
     * @param xTextOffSet The X offset of the text (usually the overlay X position)
     * @param yTextOffSet The Y offset of the text (usually the overlay Y position)
     */
    private void highlightPlayer(Graphics2D graphics, Actor actor, String text, Color color, int xTextOffSet, int yTextOffSet) {
        Point poly = actor.getCanvasTextLocation(graphics, text, 20);
        if (poly == null) {
            return;
        }

        Point offsetPoint = new Point(poly.getX() - xTextOffSet, poly.getY() - yTextOffSet);

        OverlayUtil.renderTextLocation(graphics, offsetPoint, text, color);

    }
}

