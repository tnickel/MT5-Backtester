package com.backtester.ui.javafx;

import com.backtester.engine.WorkflowEngine;
import com.backtester.workflow.WorkflowTask;
import javafx.stage.Window;

import java.util.List;

/**
 * Facade for workflow config dialogs. Thin forwarders to keep call sites stable.
 */
public class WorkflowConfigDialogs {

    private WorkflowConfigDialogs() {}

    static double parseFiniteDecimal(String text, String fieldName, double minimum, double maximum) {
        return WorkflowConfigDialogSupport.parseFiniteDecimal(text, fieldName, minimum, maximum);
    }

    static int parsePositiveInteger(String text, String fieldName) {
        return WorkflowConfigDialogSupport.parsePositiveInteger(text, fieldName);
    }

    static void applyDiversityTaskSettings(WorkflowTask task,
                                            String moduleName,
                                            String sourceDatabank,
                                            String targetDatabank,
                                            String parameterDifferencePercent,
                                            String tradeDifferencePercent,
                                            String minimumDifferentParameters,
                                            String maximumStrategies) {
        WorkflowConfigDialogSupport.applyDiversityTaskSettings(
                task, moduleName, sourceDatabank, targetDatabank,
                parameterDifferencePercent, tradeDifferencePercent,
                minimumDifferentParameters, maximumStrategies);
    }

    static void applyDiversityTaskSettings(WorkflowTask task,
                                            String moduleName,
                                            String sourceDatabank,
                                            String targetDatabank,
                                            String parameterDifferencePercent,
                                            String tradeDifferencePercent,
                                            String minimumDifferentParameters,
                                            String maximumStrategies,
                                            boolean rankByScore) {
        WorkflowConfigDialogSupport.applyDiversityTaskSettings(
                task, moduleName, sourceDatabank, targetDatabank,
                parameterDifferencePercent, tradeDifferencePercent,
                minimumDifferentParameters, maximumStrategies, rankByScore);
    }

    public static void showDiversityClusteringDialog(WorkflowTask task,
                                                      List<String> databankNames,
                                                      Window owner,
                                                      Runnable onSave) {
        WorkflowDiversityConfigDialog.showDiversityClusteringDialog(task, databankNames, owner, onSave);
    }

    public static void showStep1Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep1ConfigDialog.showStep1Dialog(engine, owner);
    }

    public static void showStep1Dialog(WorkflowEngine engine, Window owner, Runnable onSave) {
        WorkflowStep1ConfigDialog.showStep1Dialog(engine, owner, onSave);
    }

    public static void showStep2Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep2ConfigDialog.showStep2Dialog(engine, owner);
    }

    public static void showStep2Dialog(WorkflowEngine engine, Window owner, Runnable onSave) {
        WorkflowStep2ConfigDialog.showStep2Dialog(engine, owner, onSave);
    }

    public static void showStep3Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep3ConfigDialog.showStep3Dialog(engine, owner);
    }

    public static void showScoreWeightsDialog(Window owner) {
        WorkflowScoreWeightsConfigDialog.showScoreWeightsDialog(owner);
    }

    public static void showStep4Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep4ConfigDialog.showStep4Dialog(engine, owner);
    }

    public static void showStep5Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep5ConfigDialog.showStep5Dialog(engine, owner);
    }

    public static void showStep7Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep7ConfigDialog.showStep7Dialog(engine, owner);
    }

    public static void showStep6Dialog(WorkflowEngine engine, Window owner) {
        WorkflowStep6ConfigDialog.showStep6Dialog(engine, owner);
    }

    public static void showCvExplanationDialog(Window owner, boolean isForward) {
        WorkflowCvExplanationDialog.showCvExplanationDialog(owner, isForward);
    }
}
