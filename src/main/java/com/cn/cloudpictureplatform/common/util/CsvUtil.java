package com.cn.cloudpictureplatform.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CsvUtil {

    public static String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        boolean needsEscaping = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (needsEscaping) {
            text = text.replace("\"", "\"\"");
            return "\"" + text + "\"";
        }
        return text;
    }
}
