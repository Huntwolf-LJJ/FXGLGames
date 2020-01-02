package com.almasb.fxglgames.ncc;

import com.almasb.fxgl.animation.Interpolators;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.ui.FXGLButton;
import com.almasb.fxgl.ui.FXGLScrollPane;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.stream.Collectors;

import static com.almasb.fxgl.dsl.FXGL.*;
import static com.almasb.fxglgames.ncc.Config.*;
import static com.almasb.fxglgames.ncc.NCCFactory.DECKS;
import static com.almasb.fxglgames.ncc.NCCType.ENEMY_CARD;
import static com.almasb.fxglgames.ncc.NCCType.PLAYER_CARD;

/**
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
public class NCCApp extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("FXGL Ninja Card Combat");
        settings.setVersion("0.1");
        settings.setWidth(APP_WIDTH);
        settings.setHeight(APP_HEIGHT);
    }

    @Override
    protected void initInput() {
        onKeyDown(KeyCode.ENTER, "Next Turn", this::nextTurn);
    }

    private Entity selectedCard;

    private Rectangle highlightRect;

    private int playerCardsSelected;

    @Override
    protected void initGame() {
        highlightRect = new Rectangle(CARD_WIDTH, CARD_HEIGHT, Color.color(0.7, 0.9, 0.8, 0.5));
        playerCardsSelected = 0;

        getGameScene().setBackgroundColor(Color.LIGHTGRAY);

        getGameWorld().addEntityFactory(new NCCFactory());




        var box = new HBox(25,
                DECKS.stream()
                        .flatMap(d -> d.getCards().stream())
                        .map(c -> getGameWorld().create("card", new SpawnData(0, 0).put("card", c)))
                        .map(e -> {
                            var view = e.getViewComponent().getParent();

                            view.setOnMouseClicked(event -> {
                                selectCard(e);
                            });

                            return view;
                        })
                        .collect(Collectors.toList())
                        .toArray(Node[]::new)
        );

        var deckView = new FXGLScrollPane(box);
        deckView.setMaxSize(APP_WIDTH * 0.75, CARD_HEIGHT + 50);
        deckView.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);






        for (int i = 0; i < 5; i++) {
            spawn("cardPlaceholder", new SpawnData(50 + i*(CARD_WIDTH + 25), 40 + 30).put("isPlayer", false));

            var playerPlaceholder = spawn("cardPlaceholder", new SpawnData(50 + i*(CARD_WIDTH + 25), 420 + 30).put("isPlayer", true));

            playerPlaceholder.getViewComponent().addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                var btn = new FXGLButton("SELECT");
                btn.setOnAction(event -> {
                    // done selecting card from dialog
                    selectedCard.getViewComponent().removeChild(highlightRect);
                    box.getChildren().remove(selectedCard.getViewComponent().getParent());

                    selectedCard.getViewComponent().getParent().setLayoutX(0);
                    selectedCard.getViewComponent().getParent().setLayoutY(0);
                    selectedCard.getViewComponent().getParent().setOnMouseClicked(null);

                    selectedCard.setPosition(playerPlaceholder.getPosition().subtract(0, 0));
                    selectedCard.setType(PLAYER_CARD);

                    getGameWorld().addEntity(selectedCard);

                    playerPlaceholder.removeFromWorld();

                    playerCardsSelected++;

                    selectCardAI();
                });

                getDisplay().showBox("Card Select", deckView, btn);
            });
        }
    }

    private void selectCard(Entity card) {
        if (selectedCard != null) {
            selectedCard.getViewComponent().removeChild(highlightRect);
        }

        selectedCard = card;
        selectedCard.getViewComponent().addChild(highlightRect);
    }

    private void selectCardAI() {
        if (playerCardsSelected == 1) {
            placeEnemyCards(2);

        } else if (playerCardsSelected == 3) {
            placeEnemyCards(2);

        } else if (playerCardsSelected == 5) {
            placeEnemyCards(1);
        }
    }

    private void placeEnemyCards(int num) {
        getGameWorld().getEntitiesFiltered(e -> e.getPropertyOptional("isPlayer").isPresent() && !e.getBoolean("isPlayer"))
                .stream()
                .limit(num)
                .forEach(e -> {
                    // this is where AI picks the cards based on player cards
                    spawn("card", new SpawnData(e.getPosition()).put("type", ENEMY_CARD).put("card", getRandomCard()));

                    e.removeFromWorld();
                });
    }

    @Override
    protected void initUI() {
        var btnNext = getUIFactory().newButton("Next Turn");
        btnNext.setOnAction(e -> nextTurn());

        addUINode(btnNext, getAppWidth() - 250, getAppHeight() - 40);
    }

    private void nextTurn() {
        var playerCards = byType(PLAYER_CARD);
        var enemyCards = byType(ENEMY_CARD);

        for (int i = 0; i < 5; i++) {
            Entity cardEntity = playerCards.get(i);
            var card = cardEntity.getComponent(CardComponent.class);

            if (card.isAlive()) {
                Entity cardEntity2 = enemyCards.get(i);
                var card2 = cardEntity2.getComponent(CardComponent.class);

                Entity targetEntity;

                if (card2.isAlive()) {
                    targetEntity = cardEntity2;
                } else {
                    targetEntity = enemyCards.stream()
                            .filter(e -> e.getComponent(CardComponent.class).isAlive())
                            .findAny()
                            .orElse(null);
                }

                // TODO: animations are delayed and start when targetEntity is alive
                // we still need to check targetEntity's hp during animation

                // TODO: add next turn button and disable while turn is being executed

                if (targetEntity != null) {
                    animationBuilder()
                            .delay(Duration.seconds(i))
                            .duration(Duration.seconds(0.5))
                            .interpolator(Interpolators.EXPONENTIAL.EASE_IN())
                            .repeat(2)
                            .autoReverse(true)
                            .translate(cardEntity)
                            .from(cardEntity.getPosition())
                            .to(cardEntity2.getPosition().add(0, CARD_HEIGHT / 2.0))
                            .buildAndPlay();

                    animationBuilder()
                            .delay(Duration.seconds(0.5 + i))
                            .duration(Duration.seconds(0.1))
                            .interpolator(Interpolators.ELASTIC.EASE_OUT())
                            .repeat(4)
                            .autoReverse(true)
                            .onFinished(() -> attack(cardEntity, targetEntity))
                            .translate(targetEntity)
                            .from(targetEntity.getPosition())
                            .to(targetEntity.getPosition().add(2.5, 0))
                            .buildAndPlay();
                }
            }
        }

        // TODO: remove duplicate

//        for (int i = 0; i < 5; i++) {
//            Entity cardEntity = enemyCards.get(i);
//            var card = cardEntity.getComponent(CardComponent.class);
//
//            if (card.isAlive()) {
//                Entity cardEntity2 = playerCards.get(i);
//                var card2 = cardEntity2.getComponent(CardComponent.class);
//
//                if (card2.isAlive()) {
//                    attack(cardEntity, cardEntity2);
//                } else {
//                    playerCards.stream().filter(e -> e.getComponent(CardComponent.class).isAlive()).findAny().ifPresent(c -> {
//                        attack(cardEntity, c);
//                    });
//                }
//            }
//        }
//
//        if (!isAlive(playerCards)) {
//            gameOver("You lose");
//        } else if (!isAlive(enemyCards)) {
//            gameOver("You win");
//        }
    }

    private void attack(Entity e1, Entity e2) {
        int atk = e1.getComponent(CardComponent.class).getAtk();
        int def = e2.getComponent(CardComponent.class).getDef();

        int hp = e2.getComponent(CardComponent.class).getHp() - (atk - def);
        e2.getComponent(CardComponent.class).setHp(hp);
    }

    private boolean isAlive(List<Entity> cards) {
        return cards.stream()
                .map(e -> e.getComponent(CardComponent.class))
                .anyMatch(CardComponent::isAlive);
    }

    private void onSkill(Entity user, Skill skill, List<Entity> targets) {

    }

    private void gameOver(String message) {
        getDisplay().showMessageBox(message, getGameController()::startNewGame);
    }

    private Card getRandomCard() {
        return FXGLMath.random(DECKS)
                .map(Deck::getCards)
                .flatMap(FXGLMath::random)
                .get();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
