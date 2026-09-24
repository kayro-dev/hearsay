package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookPagesTest {

    private static List<String> chronicle(int days) {
        List<String> lines = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            lines.add("Day " + day);
            lines.add("  morning  You told Lark, the Town Crier, that wheat is getting scarce.");
            lines.add("  evening  Wheat peaked at ▲ +38%. (see the report for what caused this)");
        }
        return lines;
    }

    @Test
    void noPageRunsPastTheBottom() {
        for (String page : BookPages.of(chronicle(40))) {
            int lines = 0;
            for (String entry : page.split("\n")) {
                lines += BookPages.linesFor(entry);
            }
            assertTrue(lines <= BookPages.LINES_PER_PAGE, lines + " lines on:\n" + page);
        }
    }

    @Test
    void everythingIsKeptInOrder() {
        String book = String.join("\n", BookPages.of(chronicle(10)));
        int last = -1;
        for (int day = 1; day <= 10; day++) {
            int at = book.indexOf("Day " + day + "\n");
            assertTrue(at > last, "day " + day + " is missing or out of order");
            last = at;
        }
        assertTrue(book.contains("morning: You told Lark"), "entries read as time: text");
    }

    @Test
    void aDayIsNeverLeftAtTheFootOfAPage() {
        for (String page : BookPages.of(chronicle(40))) {
            String[] entries = page.split("\n");
            assertFalse(entries[entries.length - 1].startsWith("Day "),
                    "a heading with nothing under it:\n" + page);
        }
    }

    @Test
    void anEntryLongerThanAPageIsCutAtWords() {
        String longest = "  night    " + "word ".repeat(200).strip();
        List<String> pages = BookPages.of(List.of("Day 1", longest));
        assertTrue(pages.size() > 1);
        assertEquals(200, String.join(" ", pages).split("word", -1).length - 1,
                "every word should survive the cutting");
    }

    @Test
    void aSessionTooLongForOneBookSaysWhereTheRestIs() {
        List<String> pages = BookPages.of(chronicle(400));
        assertEquals(BookPages.MOST_PAGES, pages.size());
        assertEquals(BookPages.THE_REST, pages.get(pages.size() - 1));
    }
}
