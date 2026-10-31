package vn.edu.taskmanager;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the limited Markdown commonly returned by Gemini into readable Android text. */
final class AiMarkdown {
    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|(?<!\\*)\\*([^*]+?)\\*(?!\\*)|`([^`]+)`");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+");

    private AiMarkdown() { }

    static CharSequence format(String markdown) {
        SpannableStringBuilder output = new SpannableStringBuilder();
        if (markdown == null || markdown.isEmpty()) return output;
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher heading = HEADING.matcher(line);
            boolean isHeading = heading.find();
            if (isHeading) line = line.substring(heading.end());
            Matcher bullet = BULLET.matcher(line);
            if (bullet.find()) line = "•  " + line.substring(bullet.end()).trim();
            int lineStart = output.length();
            appendInline(output, line);
            int lineEnd = output.length();
            if (isHeading && lineEnd > lineStart) {
                output.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                output.setSpan(new RelativeSizeSpan(1.08f), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < lines.length - 1) output.append('\n');
        }
        while (output.length() > 0 && output.charAt(output.length() - 1) == '\n')
            output.delete(output.length() - 1, output.length());
        return output;
    }

    private static void appendInline(SpannableStringBuilder output, String text) {
        Matcher matcher = INLINE.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            output.append(text, cursor, matcher.start());
            String value;
            Object span;
            if (matcher.group(1) != null) {
                value = matcher.group(1);
                span = new StyleSpan(Typeface.BOLD);
            } else if (matcher.group(2) != null) {
                value = matcher.group(2);
                span = new StyleSpan(Typeface.ITALIC);
            } else {
                value = matcher.group(3);
                span = new TypefaceSpan("monospace");
            }
            int start = output.length();
            output.append(value);
            output.setSpan(span, start, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cursor = matcher.end();
        }
        output.append(text, cursor, text.length());
    }
}
