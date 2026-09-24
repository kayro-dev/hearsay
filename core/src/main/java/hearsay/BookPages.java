package hearsay;

import java.util.ArrayList;
import java.util.List;

/**
 * The chronicle laid out as the pages of a written book.
 *
 * <p>A Minecraft book page shows about fourteen lines of about nineteen characters, and text
 * past the bottom is not scrolled but cut off. So the chronicle is paginated here, by
 * estimated lines rather than characters, a little under the page's real size so the game's
 * own wrapping, which breaks at words, never pushes a line out of sight. A day's heading is
 * never left alone at the foot of a page.
 *
 * <p>Plain Java in core so it can be tested; the plugin only turns the strings into a book.
 */
public final class BookPages {

    /** Characters on a line, a little under the page's width so word wrapping has room. */
    static final int LINE_WIDTH = 18;
    /** Lines on a page, one under the page's fourteen. */
    static final int LINES_PER_PAGE = 13;
    /** A written book holds a hundred pages; the last is kept for saying the rest is elsewhere. */
    static final int MOST_PAGES = 100;

    static final String THE_REST = "The rest does not fit in one book. The whole chronicle "
            + "and the report are in the saved session.";

    private BookPages() {
    }

    public static List<String> of(List<String> chronicle) {
        List<String> entries = new ArrayList<>();
        for (String line : chronicle) {
            if (line.isBlank()) {
                continue;
            }
            // "  morning  You told Lark ..." reads as "morning: You told Lark ..." in a book.
            String trimmed = line.strip();
            int gap = trimmed.indexOf("  ");
            entries.add(line.startsWith("  ") && gap > 0
                    ? trimmed.substring(0, gap) + ": " + trimmed.substring(gap).strip()
                    : trimmed);
        }

        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int used = 0;
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            boolean heading = entry.startsWith("Day ");
            int needed = linesFor(entry)
                    // A heading only goes on a page with room for the entry under it too.
                    + (heading && i + 1 < entries.size() ? linesFor(entries.get(i + 1)) : 0);
            if (used > 0 && used + needed > LINES_PER_PAGE) {
                pages.add(page.toString());
                page = new StringBuilder();
                used = 0;
            }
            for (String piece : pieces(entry)) {
                if (used > 0 && used + linesFor(piece) > LINES_PER_PAGE) {
                    pages.add(page.toString());
                    page = new StringBuilder();
                    used = 0;
                }
                if (!page.isEmpty()) {
                    page.append('\n');
                }
                page.append(piece);
                used += linesFor(piece);
            }
        }
        if (!page.isEmpty()) {
            pages.add(page.toString());
        }
        if (pages.size() > MOST_PAGES) {
            List<String> kept = new ArrayList<>(pages.subList(0, MOST_PAGES - 1));
            kept.add(THE_REST);
            return kept;
        }
        return pages;
    }

    /** How many lines an entry takes once the page wraps it. */
    static int linesFor(String text) {
        return Math.max(1, (text.length() + LINE_WIDTH - 1) / LINE_WIDTH);
    }

    /** An entry too long for one page, cut at words into pieces that each fit. */
    private static List<String> pieces(String entry) {
        int most = LINE_WIDTH * LINES_PER_PAGE;
        List<String> pieces = new ArrayList<>();
        String rest = entry;
        while (rest.length() > most) {
            int cut = rest.lastIndexOf(' ', most);
            if (cut <= 0) {
                cut = most;
            }
            pieces.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        pieces.add(rest);
        return pieces;
    }
}
