package com.backtester.ui.javafx;

import javafx.scene.control.Button;

/**
 * Facade for documentation UI helpers. Implementations live in topic classes;
 * call sites keep using DocHelper.* unchanged.
 */
public class DocHelper {
    public static javafx.scene.Node createHeaderWithTooltip(String title, String tooltipText) {
        return DocHelperButtons.createHeaderWithTooltip(title, tooltipText);
    }

    public static javafx.scene.Node createHeaderWithTooltip(String title, String tooltipText, Runnable clickAction) {
        return DocHelperButtons.createHeaderWithTooltip(title, tooltipText, clickAction);
    }

    public static Button createInfoButton(String tabName, String overview, String details) {
        return DocHelperButtons.createInfoButton(tabName, overview, details);
    }

    public static Button createSmallInfoButton(String tabName, String overview, String details) {
        return DocHelperButtons.createSmallInfoButton(tabName, overview, details);
    }

    public static void showDoc(String tabName, String overview, String details) {
        DocHelperButtons.showDoc(tabName, overview, details);
    }

    public static Button createSmallInfoButton(Runnable action) {
        return DocHelperButtons.createSmallInfoButton(action);
    }

    public static Button createSmallInfoButton(String tooltip, Runnable action) {
        return DocHelperButtons.createSmallInfoButton(tooltip, action);
    }

    public static void showScoreDocDialog(javafx.stage.Window owner) {
        ScoreConsistencyDocs.showScoreDocDialog(owner);
    }

    public static void showConsistencyDocDialog(javafx.stage.Window owner) {
        ScoreConsistencyDocs.showConsistencyDocDialog(owner);
    }

    public static String getScoreDocHtml() {
        return ScoreConsistencyDocs.getScoreDocHtml();
    }

    public static String getConsistencyDocHtml() {
        return ScoreConsistencyDocs.getConsistencyDocHtml();
    }

    public static Button createThickCircularInfoButton(String tooltip, Runnable action) {
        return DocHelperButtons.createThickCircularInfoButton(tooltip, action);
    }

    public static Button createThickCircularCyanInfoButton(String tooltip, Runnable action) {
        return DocHelperButtons.createThickCircularCyanInfoButton(tooltip, action);
    }

    public static void showAllIndicesDocDialog(javafx.stage.Window owner) {
        IndicesDocs.showAllIndicesDocDialog(owner);
    }

    public static String getAllIndicesDocHtml() {
        return IndicesDocs.getAllIndicesDocHtml();
    }

    public static void showCustomProjectDiversityDocDialog(javafx.stage.Window owner) {
        DiversityDocs.showCustomProjectDiversityDocDialog(owner);
    }

    public static String getCustomProjectDiversityDocHtml() {
        return DiversityDocs.getCustomProjectDiversityDocHtml();
    }

    public static void showDiversityDocDialog(javafx.stage.Window owner) {
        DiversityDocs.showDiversityDocDialog(owner);
    }

    public static String getDiversityDocHtml() {
        return DiversityDocs.getDiversityDocHtml();
    }

    public static Button createControllingInfoButton(javafx.scene.Node ownerNode) {
        return ControllingDocs.createControllingInfoButton(ownerNode);
    }

    public static void showControllingDocDialog(javafx.stage.Window owner) {
        ControllingDocs.showControllingDocDialog(owner);
    }

    public static String getControllingDocHtml() {
        return ControllingDocs.getControllingDocHtml();
    }
}
