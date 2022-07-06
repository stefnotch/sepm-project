package at.ac.tuwien.sepm.groupphase.backend.codegenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * A little helper class to work with strings.
 */
public class StringUtils {

    /**
     * Java's built-in String.split() method has a few undesirable edge cases, which this method fixes.
     */
    public static String[] split(String input, String delimiter) {
        if (input.isEmpty()) {
            return new String[0];
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        // invariant: we have at least one more part to add to the list
        while (true) {
            int end = input.indexOf(delimiter, start);
            if (end == -1) {
                parts.add(input.substring(start));
                return parts.toArray(new String[0]);
            } else {
                parts.add(input.substring(start, end));
                start = end + delimiter.length();
            }
        }
    }
}
