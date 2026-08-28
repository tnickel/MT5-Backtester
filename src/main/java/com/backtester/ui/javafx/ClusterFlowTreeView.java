package com.backtester.ui.javafx;

import com.backtester.workflow.ClusterCensus.StageVerdict;
import com.backtester.workflow.ClusterFlowTreeModel;
import com.backtester.workflow.ClusterFlowTreeModel.Cell;
import com.backtester.workflow.ClusterFlowTreeModel.LayoutEdge;
import com.backtester.workflow.ClusterFlowTreeModel.LayoutNode;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Zoomable multipath canvas for Show Flow. Top-down Stammbaum; left-drag pans the view.
 */
public final class ClusterFlowTreeView extends BorderPane {

    private static final double NODE_W = 118;
    private static final double NODE_H = 56;
    private static final double STAGE_W = 148;
    private static final double STAGE_H = 78;
    private static final double ROOT_W = 220;
    private static final double ROOT_H = 52;
    private static final double MIN_SCALE = 0.2;
    private static final double MAX_SCALE = 3.0;
    private static final double PAN_THRESHOLD = 4.0;

    private final ClusterFlowTreeModel model;
    private final Pane world = new Pane();
    private final StackPane viewport = new StackPane();
    private final Map<String, StackPane> nodeFx = new HashMap<>();

    private Consumer<LayoutNode> onNodeSelected;
    private LayoutNode selected;
    private double scale = 1.0;
    private double panX;
    private double panY;
    private double dragStartX;
    private double dragStartY;
    private double panAtDragStartX;
    private double panAtDragStartY;
    private boolean panCandidate;
    private boolean panning;
    private boolean suppressClick;
    private boolean fittedOnce;

    public ClusterFlowTreeView(ClusterFlowTreeModel model) {
        this.model = Objects.requireNonNull(model, "model");
        setStyle("-fx-background-color: #10141e; -fx-border-color: #232a3b; "
                + "-fx-border-radius: 6; -fx-background-radius: 6;");
        setPadding(new Insets(6));

        world.setStyle("-fx-background-color: transparent;");
        world.setMouseTransparent(false);
        viewport.getChildren().add(world);
        viewport.setStyle("-fx-background-color: #0c1018;");
        viewport.setMinHeight(520);
        viewport.setPrefHeight(Region.USE_COMPUTED_SIZE);
        viewport.setCursor(Cursor.OPEN_HAND);
        StackPane.setAlignment(world, Pos.TOP_LEFT);
        BorderPane.setAlignment(viewport, Pos.CENTER);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        setTop(buildToolbar());
        setCenter(viewport);
        buildSceneGraph();
        installNavigation();

        viewport.layoutBoundsProperty().addListener((obs, o, n) -> {
            if (n.getWidth() > 40 && n.getHeight() > 40 && !fittedOnce) {
                fittedOnce = true;
                fitToView();
            }
        });
    }

    public void setOnNodeSelected(Consumer<LayoutNode> listener) {
        this.onNodeSelected = listener;
    }

    public void select(LayoutNode node) {
        this.selected = node;
        refreshSelectionStyles();
        if (onNodeSelected != null && node != null) {
            onNodeSelected.accept(node);
        }
    }

    public void fitToView() {
        Bounds vb = viewport.getLayoutBounds();
        if (vb.getWidth() <= 1 || vb.getHeight() <= 1) {
            return;
        }
        double contentW = model.layoutWidth() + NODE_W;
        double contentH = model.layoutHeight() + NODE_H;
        double sx = (vb.getWidth() - 24) / Math.max(1, contentW);
        double sy = (vb.getHeight() - 24) / Math.max(1, contentH);
        scale = clamp(Math.min(sx, sy), MIN_SCALE, MAX_SCALE);
        panX = (vb.getWidth() - contentW * scale) / 2.0;
        panY = 12;
        applyTransform();
    }

    public void zoomBy(double factor) {
        Bounds vb = viewport.getLayoutBounds();
        zoomAt(factor, vb.getWidth() / 2.0, vb.getHeight() / 2.0);
    }

    private HBox buildToolbar() {
        Button fit = toolButton("Fit");
        fit.setOnAction(e -> fitToView());
        Button zoomIn = toolButton("+");
        zoomIn.setOnAction(e -> zoomBy(1.15));
        Button zoomOut = toolButton("−");
        zoomOut.setOnAction(e -> zoomBy(1 / 1.15));
        Label hint = new Label("Ziehen = verschieben · Mausrad = Zoom · Klick = Detail");
        hint.setStyle("-fx-text-fill: #7a8496; -fx-font-size: 11px;");
        Label legend = new Label("grau=0  ·  gelb=1  ·  cyan=2–5  ·  grün=6+");
        legend.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        HBox bar = new HBox(10, fit, zoomIn, zoomOut, hint, legend);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 6, 2));
        return bar;
    }

    private static Button toolButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #e6e9f0; -fx-border-color: #596273; "
                + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold; -fx-min-width: 36;");
        return b;
    }

    private void buildSceneGraph() {
        world.getChildren().clear();
        nodeFx.clear();

        for (LayoutEdge edge : model.layoutEdges()) {
            world.getChildren().add(createEdge(edge));
        }

        for (LayoutNode node : model.layoutNodes()) {
            StackPane fx = createNodeGraphic(node);
            nodeFx.put(key(node), fx);
            world.getChildren().add(fx);
        }
        applyTransform();
    }

    private Node createEdge(LayoutEdge edge) {
        LayoutNode from = edge.getFrom();
        LayoutNode to = edge.getTo();
        boolean muted = liveCountOf(from) <= 0 && !from.isTrunk()
                || liveCountOf(to) <= 0 && !to.isTrunk();
        Color stroke = Color.web(muted ? "#2a3344" : "#6b7c96");
        double width = muted ? 1.2 : 2.0;

        if (from.isRoot()) {
            double fx = from.getX();
            double fy = from.getY() + ROOT_H / 2.0;
            double tx = to.getX();
            double ty = to.getY() - NODE_H / 2.0;
            double midY = fy + Math.max(28, (ty - fy) * 0.45);
            Path path = new Path(
                    new MoveTo(fx, fy),
                    new LineTo(fx, midY),
                    new LineTo(tx, midY),
                    new LineTo(tx, ty));
            path.setStroke(stroke);
            path.setStrokeWidth(width);
            path.setFill(null);
            path.setMouseTransparent(true);
            return path;
        }

        Line line = new Line(from.getX(), from.getY() + NODE_H / 2.0,
                to.getX(), to.getY() - NODE_H / 2.0);
        line.setStroke(stroke);
        line.setStrokeWidth(width);
        line.setMouseTransparent(true);
        return line;
    }

    private StackPane createNodeGraphic(LayoutNode node) {
        double w = nodeWidth(node);
        double h = nodeHeight(node);

        Rectangle body = new Rectangle(w, h);
        body.setArcWidth(10);
        body.setArcHeight(10);

        VBox content = new VBox(1);
        content.setAlignment(Pos.CENTER);
        content.setMouseTransparent(true);

        String[] lines = node.displayLabel().split("\n", 3);
        Text title = new Text(lines[0]);
        double titleSize = node.isRoot() ? 14 : (node.isTrunk() ? 13 : 12);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, titleSize));
        title.setTextAlignment(TextAlignment.CENTER);
        title.setWrappingWidth(w - 10);

        content.getChildren().add(title);
        Text countText = null;
        if (lines.length > 1 && !lines[1].isBlank()) {
            countText = new Text(lines[1]);
            double countSize = node.isTrunk() && !node.isRoot() ? 13 : 14;
            countText.setFont(Font.font("Segoe UI", FontWeight.BOLD, countSize));
            countText.setTextAlignment(TextAlignment.CENTER);
            countText.setWrappingWidth(w - 8);
            content.getChildren().add(countText);
        }

        StackPane box = new StackPane(body, content);
        box.setPrefSize(w, h);
        box.setLayoutX(node.getX() - w / 2.0);
        box.setLayoutY(node.getY() - h / 2.0);
        box.setCursor(Cursor.HAND);

        if (!node.isTrunk() && liveCountOf(node) > 0) {
            int live = liveCountOf(node);
            Circle badge = new Circle(10);
            badge.setFill(Color.web("#0b0d13"));
            badge.setStroke(Color.web("#e6e9f0"));
            badge.setStrokeWidth(1.1);
            Text badgeText = new Text(live > 99 ? "99+" : String.valueOf(live));
            badgeText.setFill(Color.web("#e6e9f0"));
            badgeText.setFont(Font.font("Segoe UI", FontWeight.BOLD, live > 9 ? 9 : 11));
            StackPane badgePane = new StackPane(badge, badgeText);
            badgePane.setMouseTransparent(true);
            StackPane.setAlignment(badgePane, Pos.TOP_RIGHT);
            badgePane.setTranslateX(-2);
            badgePane.setTranslateY(2);
            box.getChildren().add(badgePane);
        }

        styleNode(body, title, countText, node, false);

        if (!node.isTrunk() && node.getCell() != null) {
            int stageTotal = node.getRow() != null ? node.getRow().liveTotal() : 0;
            Tooltip.install(box, new Tooltip(node.getCell().hoverText()
                    + "\n" + node.getCell().fullLabel()
                    + "\nDiese Linie: " + liveCountOf(node)
                    + (stageTotal > 0 ? "\nStufe Σ: " + stageTotal : "")));
        } else if (node.isRoot()) {
            Tooltip.install(box, new Tooltip("Stamm — Übersicht der Cluster-Linien"));
        } else if (node.getRow() != null) {
            int total = node.getRow().liveTotal();
            Tooltip.install(box, new Tooltip(node.getRow().getTaskName()
                    + "\n" + node.getRow().actionVerb() + ": " + node.getRow().actionExplanation()
                    + (total > 0 ? "\nΣ " + total + " Strategien über alle Linien" : "")
                    + "\nKlick: Detail rechts"));
        }

        box.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (suppressClick || panning) {
                suppressClick = false;
                e.consume();
                return;
            }
            select(node);
            e.consume();
        });
        return box;
    }

    private static double nodeWidth(LayoutNode node) {
        if (node.isRoot()) {
            return ROOT_W;
        }
        if (node.isTrunk()) {
            return STAGE_W;
        }
        return NODE_W;
    }

    private static double nodeHeight(LayoutNode node) {
        if (node.isRoot()) {
            return ROOT_H;
        }
        if (node.isTrunk()) {
            return STAGE_H;
        }
        return NODE_H;
    }

    private void refreshSelectionStyles() {
        for (LayoutNode node : model.layoutNodes()) {
            StackPane box = nodeFx.get(key(node));
            if (box == null || box.getChildren().size() < 2) {
                continue;
            }
            Rectangle body = (Rectangle) box.getChildren().get(0);
            VBox content = (VBox) box.getChildren().get(1);
            Text title = (Text) content.getChildren().get(0);
            Text countText = content.getChildren().size() > 1 ? (Text) content.getChildren().get(1) : null;
            boolean sel = selected != null
                    && selected.isTrunk() == node.isTrunk()
                    && selected.getRowIndex() == node.getRowIndex()
                    && selected.getColIndex() == node.getColIndex();
            styleNode(body, title, countText, node, sel);
        }
    }

    private void styleNode(Rectangle body, Text title, Text countText, LayoutNode node, boolean selectedNow) {
        if (node.isRoot()) {
            body.setFill(Color.rgb(120, 150, 200, selectedNow ? 0.40 : 0.22));
            body.setStroke(Color.web(selectedNow ? "#00e5ff" : "#90caf9"));
            body.setStrokeWidth(selectedNow ? 2.8 : 1.8);
            title.setFill(Color.web("#eef2ff"));
            if (countText != null) {
                countText.setFill(Color.web("#eef2ff"));
            }
            return;
        }
        if (node.isTrunk()) {
            body.setFill(Color.rgb(100, 140, 200, selectedNow ? 0.35 : 0.14));
            body.setStroke(Color.web(selectedNow ? "#00e5ff" : "#64b5f6"));
            title.setFill(Color.web("#eef2ff"));
            body.setStrokeWidth(selectedNow ? 2.4 : 1.3);
            if (countText != null) {
                countText.setFill(Color.web("#ffd54f"));
            }
            return;
        }

        int live = liveCountOf(node);
        Cell cell = node.getCell();
        boolean died = cell != null && cell.getVerdict() == StageVerdict.DIED;
        Color fill;
        Color stroke;
        Color text;

        if (died || live <= 0) {
            fill = Color.rgb(55, 65, 80, selectedNow ? 0.55 : 0.35);
            stroke = Color.web(selectedNow ? "#94a3b8" : "#475569");
            text = Color.web("#94a3b8");
        } else if (live == 1) {
            fill = Color.rgb(245, 180, 40, selectedNow ? 0.45 : 0.28);
            stroke = Color.web(selectedNow ? "#ffd54f" : "#f9a825");
            text = Color.web("#1a1408");
        } else if (live <= 5) {
            fill = Color.rgb(0, 200, 220, selectedNow ? 0.42 : 0.26);
            stroke = Color.web(selectedNow ? "#18ffff" : "#00bcd4");
            text = Color.web("#041418");
        } else {
            fill = Color.rgb(0, 210, 120, selectedNow ? 0.45 : 0.30);
            stroke = Color.web(selectedNow ? "#69f0ae" : "#00c853");
            text = Color.web("#04140c");
        }

        body.setFill(fill);
        body.setStroke(stroke);
        body.setStrokeWidth(selectedNow ? 2.8 : 1.6);
        title.setFill(text);
        if (countText != null) {
            countText.setFill(text);
        }
    }

    private static int liveCountOf(LayoutNode node) {
        if (node == null || node.getCell() == null) {
            return 0;
        }
        return Math.max(0, node.getCell().getLiveCount());
    }

    private void installNavigation() {
        viewport.addEventFilter(ScrollEvent.SCROLL, e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            zoomAt(factor, e.getX(), e.getY());
            e.consume();
        });

        // Capture-phase so drag works even when starting over a node or empty space.
        viewport.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY
                    || e.getButton() == MouseButton.MIDDLE
                    || e.getButton() == MouseButton.SECONDARY) {
                panCandidate = true;
                panning = false;
                suppressClick = false;
                dragStartX = e.getX();
                dragStartY = e.getY();
                panAtDragStartX = panX;
                panAtDragStartY = panY;
            }
        });
        viewport.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!panCandidate) {
                return;
            }
            double dx = e.getX() - dragStartX;
            double dy = e.getY() - dragStartY;
            if (!panning && Math.hypot(dx, dy) >= PAN_THRESHOLD) {
                panning = true;
                suppressClick = true;
                viewport.setCursor(Cursor.CLOSED_HAND);
            }
            if (panning) {
                panX = panAtDragStartX + dx;
                panY = panAtDragStartY + dy;
                applyTransform();
                e.consume();
            }
        });
        viewport.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (panning) {
                suppressClick = true;
            }
            panCandidate = false;
            panning = false;
            viewport.setCursor(Cursor.OPEN_HAND);
        });
    }

    private void zoomAt(double factor, double pivotX, double pivotY) {
        double old = scale;
        double next = clamp(old * factor, MIN_SCALE, MAX_SCALE);
        if (Math.abs(next - old) < 1e-9) {
            return;
        }
        double worldX = (pivotX - panX) / old;
        double worldY = (pivotY - panY) / old;
        scale = next;
        panX = pivotX - worldX * scale;
        panY = pivotY - worldY * scale;
        applyTransform();
    }

    private void applyTransform() {
        world.setScaleX(scale);
        world.setScaleY(scale);
        world.setTranslateX(panX);
        world.setTranslateY(panY);
    }

    private static String key(LayoutNode node) {
        return (node.isTrunk() ? "T" : "C") + ":" + node.getRowIndex() + ":" + node.getColIndex();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
