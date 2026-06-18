package com.sellm.anonymizer;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexAnonymizer implements Anonymizer {

    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    @Override
    public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
        List<String> mustNotContain = new ArrayList<>();
        mustNotContain.addAll(names);
        mustNotContain.addAll(schools);
        return anonymize(text, names, schools, mustNotContain);
    }

    public AnonymizationResult anonymize(String text, List<String> names,
                                         List<String> schools, List<String> mustNotContain) {
        Map<String, String> restoreMap = new LinkedHashMap<>();
        String result = text;

        int idx = 1;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String placeholder = "[儿童" + idx++ + "]";
            result = result.replace(name, placeholder);
            restoreMap.put(placeholder, name);
        }
        idx = 1;
        for (String school : schools) {
            if (school == null || school.isBlank()) continue;
            String placeholder = "[学校" + idx++ + "]";
            result = result.replace(school, placeholder);
            restoreMap.put(placeholder, school);
        }
        idx = 1;
        Matcher m = ID_CARD.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String placeholder = "[身份证" + idx++ + "]";
            restoreMap.put(placeholder, m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        }
        m.appendTail(sb);
        result = sb.toString();

        // 校验:出网前若仍残留任何 mustNotContain 项,硬阻断
        List<String> leaked = new ArrayList<>();
        for (String s : mustNotContain) {
            if (s != null && !s.isBlank() && result.contains(s)) {
                leaked.add(s);
            }
        }
        if (!leaked.isEmpty()) {
            throw new AnonymizationException("脱敏校验未通过,残留身份信息: " + leaked);
        }
        return new AnonymizationResult(result, restoreMap);
    }

    @Override
    public String restore(String text, Map<String, String> restoreMap) {
        String result = text;
        for (Map.Entry<String, String> e : restoreMap.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }
}
