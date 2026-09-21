package hearsay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One village's story as a page you can open, send, or keep.
 *
 * <p>Self-contained on purpose. A page that fetched its own CSVs would not open from a
 * file:// URL at all, because browsers refuse those requests, so the numbers are written
 * into the page and the charts are drawn as SVG with no script and nothing loaded from
 * anywhere. It works offline, it works in five years, and it survives being emailed.
 *
 * <p>Here rather than in the cli module for the reason everything else is: it is plain Java
 * with decisions in it — what counts as a headline, how a series is scaled — and that is
 * worth testing.
 */
public final class DashboardPage {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 240;
    private static final int PAD = 40;

    /** Four ticks to the day, as everywhere else. */
    private static final int TICKS_PER_DAY = 4;

    private DashboardPage() {
    }

    /**
     * @param withLie    the village as it happened
     * @param withoutLie the same village, same seed, same everything, with the lie removed
     */
    /**
     * How long after a lie a bubble may still be laid at its door. A month, as
     * {@code CalibrationTest} counts it, so the page and the tests make the same claim.
     */
    public static final int WITHIN_DAYS = 30;

    public static String render(long seed, int ticks, Params params, Claim claim,
                                MarketStats withLie, MarketStats withoutLie, long lieToldAt) {
        return render(seed, ticks, params, claim, withLie, withoutLie, null, lieToldAt);
    }

    /**
     * @param footprint the four-way split of who moved the price, or null when nobody
     *                  traded and there is nothing to split
     */
    /**
     * @param lieToldAt the tick the first lie was planted, or 0 if none was. Bubbles are
     *                  only credited to it within {@link #WITHIN_DAYS} of it: E38 measured
     *                  that 22 points of what an unwindowed count attributed to a lie
     *                  happened more than a month later, by which time a village has had
     *                  time to talk itself into anything
     */
    public static String render(long seed, int ticks, Params params, Claim claim,
                                MarketStats withLie, MarketStats withoutLie,
                                Footprint footprint, long lieToldAt) {
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
           .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
           .append("<title>Hearsay — seed ").append(seed).append("</title>")
           .append(style())
           .append("</head><body><main>");

        out.append("<h1>Hearsay</h1><p class=\"sub\">Seed ").append(seed).append(" · ")
           .append(ticks).append(" ticks (").append(ticks / 4).append(" days) · ")
           .append(params.villagers()).append(" villagers · ")
           .append(claim.item()).append(' ').append(claim.type().name().toLowerCase(Locale.ROOT))
           .append("</p>");

        out.append("<section class=\"facts\">");
        // The change leads and the index follows: 138 says nothing until you have worked
        // out that it means 38% dear, and the whole index is built so 100 is normal.
        priceFact(out, "Peak, with the lie", withLie.peakPrice(), params.basePrice());
        priceFact(out, "Peak, without it", withoutLie.peakPrice(), params.basePrice());
        fact(out, "Bubbled within " + WITHIN_DAYS + " days of the lie",
                said(withLie.bubbleWithin(lieToldAt, WITHIN_DAYS).isPresent())
                        + " / " + said(withoutLie.bubbleWithin(lieToldAt, WITHIN_DAYS).isPresent()));
        fact(out, "Bubbles at any point in the run",
                withLie.bubbles().size() + " / " + withoutLie.bubbles().size());
        fact(out, "Busts", withLie.busts().size() + " / " + withoutLie.busts().size());
        fact(out, "Held the rumour", peakHolders(withLie) + " of " + params.villagers());
        fact(out, "Evidence from gossip", withLie.shareFromGossip().isPresent()
                ? Math.round(withLie.shareFromGossip().getAsDouble() * 100) + "%" : "—");
        out.append("</section>");

        out.append("<p class=\"verdict\">").append(verdict(withLie, withoutLie, lieToldAt))
           .append("</p>");

        out.append("<h2>The price</h2>");
        out.append("<p class=\"sub\">Gold is what happened. Grey is the same village with the "
                + "lie never told — same seed, same everything else.</p>");
        out.append(priceChart(withLie, withoutLie, params.basePrice()));

        out.append("<h2>Who believed it</h2>");
        out.append("<p class=\"sub\">Villagers holding the claim at the end of each day.</p>");
        out.append(believerChart(withLie, params.villagers()));

        if (footprint != null && footprint.peakFromTheTrading() != 0) {
            out.append("<h2>Who moved the price</h2>");
            out.append("<p class=\"sub\">Two things moved it, and one counterfactual cannot "
                    + "say which. Each is measured against the timeline where only the other "
                    + "happened.</p>");
            out.append("<section class=\"facts\">");
            fact(out, "The lie, with your trades held fixed",
                    signed(footprint.peakFromTheLie()));
            fact(out, "Your selling, with the lie held fixed",
                    signed(footprint.peakFromTheTrading()));
            fact(out, "Neither: the village left alone",
                    String.valueOf(footprint.peakLeftAlone()));
            fact(out, "The two together, beyond their sum",
                    signed(footprint.interaction()));
            out.append("</section>");
            out.append("<p class=\"sub\">These are the same trades priced against each "
                    + "timeline. They are not what you would have made, because a player "
                    + "seeing different prices would have traded differently, and nothing "
                    + "here can guess how.</p>");
        }

        out.append("<h2>Does it settle?</h2>");
        out.append("<p class=\"sub\">A bubble is one shape a price can make. Whether each "
                + "swing is smaller than the last is a different question, and the one that "
                + "says whether the village is calming down or going round for ever.</p>");
        out.append(oscillation(withLie, params.basePrice()));

        out.append("<h2>Day by day</h2>").append(table(withLie, withoutLie));
        out.append("</main></body></html>\n");
        return out.toString();
    }

    private static String said(boolean yes) {
        return yes ? "yes" : "no";
    }

    /**
     * What the two runs together actually say, in a sentence.
     *
     * <p>Only a bubble inside the window counts as the lie's doing. A page that said "the
     * lie caused a bubble" about something that happened five months later would be making
     * a claim the numbers do not support.
     */
    private static String verdict(MarketStats withLie, MarketStats withoutLie, long lieToldAt) {
        int gap = withLie.peakPrice() - withoutLie.peakPrice();
        // The counterfactual is the evidence: this village bubbled and the same village
        // without the lie did not. The window's job is to catch a claim that cannot be
        // pinned on the lie, not to overrule one that can — a village that reached its
        // peak on day 102 with nineteen believers, against a twin with none, was caused by
        // the lie however long it took.
        boolean bubbled = !withLie.bubbles().isEmpty();
        boolean bubbledAnyway = !withoutLie.bubbles().isEmpty();
        boolean soonAfter = withLie.bubbleWithin(lieToldAt, WITHIN_DAYS).isPresent();
        if (bubbled && !bubbledAnyway) {
            return soonAfter
                    ? "The lie caused a bubble, within " + WITHIN_DAYS + " days of being "
                            + "told. The same village, left alone, never had one."
                    : "The lie caused a bubble. It took longer than " + WITHIN_DAYS
                            + " days to arrive, but the same village left alone never had "
                            + "one at all.";
        }
        if (bubbled) {
            return "Both villages bubbled, so this run does not show the lie causing it.";
        }
        if (gap > 0) {
            return "The lie raised the peak price by " + gap
                    + " points, but not far enough to count as a bubble.";
        }
        return "The lie changed nothing the market noticed.";
    }

    private static int peakHolders(MarketStats stats) {
        int peak = 0;
        for (MarketStats.DayOfTrading day : stats.daily()) {
            peak = Math.max(peak, day.heard());
        }
        return peak;
    }

    private static String signed(int points) {
        return (points > 0 ? "+" : "") + points;
    }

    private static void priceFact(StringBuilder out, String label, int price, int basePrice) {
        PriceMood mood = PriceMood.of(price, basePrice);
        out.append("<div class=\"fact\"><span class=\"n\" style=\"color:").append(mood.hex())
           .append("\">").append(PriceMood.describe(price, basePrice))
           .append("</span><span class=\"l\">").append(label).append(" · index ").append(price)
           .append("</span></div>");
    }

    private static void fact(StringBuilder out, String label, String value) {
        out.append("<div class=\"fact\"><span class=\"n\">").append(value)
           .append("</span><span class=\"l\">").append(label).append("</span></div>");
    }

    private static String priceChart(MarketStats withLie, MarketStats withoutLie, int basePrice) {
        List<int[]> lied = highs(withLie);
        List<int[]> quiet = highs(withoutLie);
        int highest = Math.max(Math.max(max(lied), max(quiet)), Bubble.PEAK_ABOVE + 10);
        int lowest = Math.min(Math.min(min(lied), min(quiet)), Bubble.TROUGH_BELOW - 10);
        int days = Math.max(Math.max(lied.size(), quiet.size()), 2);

        StringBuilder svg = new StringBuilder(open());
        svg.append(guide(basePrice, lowest, highest, "normal"));
        svg.append(guide(Bubble.PEAK_ABOVE, lowest, highest,
                "bubble " + PriceMood.describe(Bubble.PEAK_ABOVE, basePrice)));
        svg.append(guide(Bubble.TROUGH_BELOW, lowest, highest,
                "bust " + PriceMood.describe(Bubble.TROUGH_BELOW, basePrice)));
        svg.append(path(quiet, days, lowest, highest, "quiet"));
        svg.append(path(lied, days, lowest, highest, "lied"));
        svg.append(axis(days));
        return svg.append("</svg>").toString();
    }

    private static String believerChart(MarketStats stats, int villagers) {
        List<int[]> held = new ArrayList<>();
        for (MarketStats.DayOfTrading day : stats.daily()) {
            held.add(new int[] {day.day(), day.heard()});
        }
        int days = Math.max(held.size(), 2);
        StringBuilder svg = new StringBuilder(open());
        svg.append(path(held, days, 0, Math.max(villagers, 1), "lied"));
        svg.append(axis(days));
        return svg.append("</svg>").toString();
    }

    private static List<int[]> highs(MarketStats stats) {
        List<int[]> points = new ArrayList<>();
        for (MarketStats.DayOfTrading day : stats.daily()) {
            points.add(new int[] {day.day(), day.highPrice()});
        }
        return points;
    }

    private static int max(List<int[]> points) {
        int most = Integer.MIN_VALUE;
        for (int[] point : points) {
            most = Math.max(most, point[1]);
        }
        return most == Integer.MIN_VALUE ? 0 : most;
    }

    private static int min(List<int[]> points) {
        int least = Integer.MAX_VALUE;
        for (int[] point : points) {
            // A day the market never opened reports zero, which is not a price.
            if (point[1] > 0) {
                least = Math.min(least, point[1]);
            }
        }
        return least == Integer.MAX_VALUE ? 0 : least;
    }

    private static String open() {
        return "<svg viewBox=\"0 0 " + WIDTH + " " + HEIGHT + "\" role=\"img\">";
    }

    private static String path(List<int[]> points, int days, int low, int high, String cssClass) {
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder d = new StringBuilder();
        boolean started = false;
        for (int[] point : points) {
            if (point[1] <= 0) {
                continue; // the market did not open; no line rather than a line to zero
            }
            d.append(started ? " L " : "M ")
             .append(String.format(Locale.ROOT, "%.1f %.1f", x(point[0], days), y(point[1], low, high)));
            started = true;
        }
        return started ? "<path class=\"" + cssClass + "\" d=\"" + d + "\"/>" : "";
    }

    private static String guide(int price, int low, int high, String label) {
        if (price < low || price > high) {
            return "";
        }
        double at = y(price, low, high);
        return String.format(Locale.ROOT,
                "<line class=\"guide\" x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\"/>"
                        + "<text class=\"tick\" x=\"%d\" y=\"%.1f\">%s</text>",
                PAD, at, WIDTH - 4, at, 2, at - 3, label);
    }

    private static String axis(int days) {
        return String.format(Locale.ROOT,
                "<line class=\"axis\" x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\"/>"
                        + "<text class=\"tick\" x=\"%d\" y=\"%d\">day 1</text>"
                        + "<text class=\"tick\" text-anchor=\"end\" x=\"%d\" y=\"%d\">day %d</text>",
                PAD, HEIGHT - PAD + 6, WIDTH - 4, HEIGHT - PAD + 6,
                PAD, HEIGHT - 8, WIDTH - 4, HEIGHT - 8, days);
    }

    private static double x(int day, int days) {
        return PAD + (day - 1) * (WIDTH - PAD - 8) / (double) Math.max(days - 1, 1);
    }

    private static double y(int value, int low, int high) {
        double span = Math.max(high - low, 1);
        return HEIGHT - PAD - (value - low) * (HEIGHT - 2.0 * PAD) / span;
    }

    /** How the swings behaved: their sizes, whether they shrank, and whether it ended. */
    private static String oscillation(MarketStats stats, int basePrice) {
        int noiseFloor = Math.max(1, basePrice / 10);
        List<Swing> legs = stats.swings(noiseFloor);
        StringBuilder out = new StringBuilder("<section class=\"facts\">");

        fact(out, "Swings", String.valueOf(legs.size()));
        fact(out, "Biggest swing", legs.isEmpty() ? "—"
                : Math.round(biggest(legs) * 100.0 / basePrice) + "%");
        String decay = stats.swingDecay(noiseFloor).isPresent()
                ? String.format(Locale.ROOT, "%.2f", stats.swingDecay(noiseFloor).getAsDouble())
                : "—";
        fact(out, "Each swing vs the last", decay);
        fact(out, "Settled within 10%", stats.settledAt(basePrice, 0.10).isPresent()
                ? "day " + stats.settledAt(basePrice, 0.10).getAsLong() / (TICKS_PER_DAY)
                : "never");
        out.append("</section>");

        if (!legs.isEmpty()) {
            out.append("<p class=\"sub\">");
            out.append(stats.swingDecay(noiseFloor).isPresent()
                    && stats.swingDecay(noiseFloor).getAsDouble() < 1
                    ? "Each swing is smaller than the one before it, so the village is settling."
                    : "The swings are not shrinking. Nothing in the model damps them: every "
                            + "piece of evidence a villager has is either gossip or the price "
                            + "itself, and neither can contradict the other.");
            out.append("</p>");
            out.append("<table><thead><tr><th>Swing</th><th>From</th><th>To</th>"
                    + "<th>Size</th><th>Days</th></tr></thead><tbody>");
            int number = 0;
            for (Swing leg : legs) {
                out.append("<tr><td>").append(++number)
                   .append("</td><td>").append(PriceMood.describe(leg.fromPrice(), basePrice))
                   .append("</td><td>").append(PriceMood.describe(leg.toPrice(), basePrice))
                   .append("</td><td>").append(Math.round(leg.amplitude() * 100.0 / basePrice))
                   .append("%</td><td>").append(leg.ticks() / TICKS_PER_DAY)
                   .append("</td></tr>");
            }
            out.append("</tbody></table>");
        }
        return out.toString();
    }

    private static int biggest(List<Swing> legs) {
        int most = 0;
        for (Swing leg : legs) {
            most = Math.max(most, leg.amplitude());
        }
        return most;
    }

    private static String table(MarketStats withLie, MarketStats withoutLie) {
        StringBuilder out = new StringBuilder("<table><thead><tr><th>Day</th>"
                + "<th>High</th><th>Low</th><th>Held it</th><th>Believed it</th>"
                + "<th>High, no lie</th></tr></thead><tbody>");
        List<MarketStats.DayOfTrading> quiet = withoutLie.daily();
        for (MarketStats.DayOfTrading day : withLie.daily()) {
            String counterfactual = day.day() <= quiet.size()
                    ? String.valueOf(quiet.get(day.day() - 1).highPrice()) : "—";
            out.append("<tr><td>").append(day.day())
               .append("</td><td>").append(day.highPrice())
               .append("</td><td>").append(day.lowPrice())
               .append("</td><td>").append(day.heard())
               .append("</td><td>").append(day.believers())
               .append("</td><td class=\"quiet\">").append(counterfactual)
               .append("</td></tr>");
        }
        return out.append("</tbody></table>").toString();
    }

    private static String style() {
        return """
            <style>
            :root { --ink:#1b1a17; --dim:#6b675f; --paper:#faf8f4; --rule:#e3ded4;
                    --lied:#c07a1e; --quiet:#9a958b; }
            @media (prefers-color-scheme: dark) {
              :root { --ink:#ece8e1; --dim:#9a958b; --paper:#171614; --rule:#332f2a;
                      --lied:#e0a04a; --quiet:#6b675f; }
            }
            * { box-sizing: border-box; }
            body { margin:0; background:var(--paper); color:var(--ink);
                   font:15px/1.6 ui-sans-serif, system-ui, -apple-system, Segoe UI, sans-serif; }
            main { max-width:820px; margin:0 auto; padding:32px 16px 64px; }
            h1 { font-size:1.6rem; margin:0; letter-spacing:-0.01em; }
            h2 { font-size:1.05rem; margin:40px 0 4px; }
            .sub { color:var(--dim); margin:4px 0 0; font-size:0.9rem; }
            .verdict { margin:24px 0 0; padding:12px 16px; border-left:3px solid var(--lied);
                       background:color-mix(in srgb, var(--lied) 8%, transparent); }
            .facts { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr));
                     gap:1px; background:var(--rule); border:1px solid var(--rule);
                     margin-top:28px; }
            .fact { background:var(--paper); padding:12px 14px; }
            .fact .n { display:block; font-size:1.35rem; font-variant-numeric:tabular-nums; }
            .fact .l { display:block; color:var(--dim); font-size:0.78rem; }
            svg { width:100%; height:auto; margin-top:12px; overflow:visible; }
            path { fill:none; stroke-width:2; stroke-linejoin:round; stroke-linecap:round; }
            path.lied { stroke:var(--lied); }
            path.quiet { stroke:var(--quiet); stroke-width:1.5; stroke-dasharray:4 4; }
            .guide { stroke:var(--rule); stroke-width:1; }
            .axis { stroke:var(--rule); stroke-width:1; }
            .tick { fill:var(--dim); font-size:10px; }
            table { border-collapse:collapse; width:100%; margin-top:12px;
                    font-variant-numeric:tabular-nums; font-size:0.86rem; }
            th, td { text-align:right; padding:4px 8px; border-bottom:1px solid var(--rule); }
            th:first-child, td:first-child { text-align:left; }
            th { color:var(--dim); font-weight:600; font-size:0.78rem; }
            td.quiet { color:var(--dim); }
            </style>
            """;
    }
}
