package com.backtester;

import com.backtester.workflow.CustomProject;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

public class GsonTest {
    public static void main(String[] args) {
        CustomProject proj = new CustomProject();
        proj.setName("Test");
        
        CombinedPass cp = new CombinedPass(new Pass(), new Pass(), 1.0, 1.0, "details");
        List<CombinedPass> list = new ArrayList<>();
        list.add(cp);
        LinkedHashMap<String, List<CombinedPass>> databanks = new LinkedHashMap<>();
        databanks.put("Results", list);
        proj.setDatabanks(databanks);

        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
        String json = gson.toJson(proj);
        System.out.println("Contains databanks: " + json.contains("Results"));
        System.out.println("Contains CombinedPass: " + json.contains("backtestPass"));
        System.out.println("Total length: " + json.length());
    }
}
