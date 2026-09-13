// Dumps the Java query API's answers for one calendar to a JSON fixture, so the
// Python package can be tested against the reference implementation without a
// JVM in the loop. Driven by generate_parity_fixture.sh; see that script for the
// environment variables it expects.
//
// Everything here reads the *published* artifacts through the same code path as
// `tools query --as-of blessed`, so the fixture is exactly what the CLI answers.

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.model.Event;
import com.bdc.stream.DateStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

String CAL = System.getenv("BDC_CALENDAR");
Path ROOT = Path.of(System.getenv("BDC_REPO_ROOT"));
Path OUT = Path.of(System.getenv("BDC_OUT"));
int SAMPLES = Integer.parseInt(System.getenv().getOrDefault("BDC_SAMPLES", "1000"));

String esc(String s) {
    if (s == null) return "null";
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
        switch (c) {
            case '"' -> b.append("\\\"");
            case '\\' -> b.append("\\\\");
            case '\n' -> b.append("\\n");
            case '\r' -> b.append("\\r");
            case '\t' -> b.append("\\t");
            default -> {
                if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                else b.append(c);
            }
        }
    }
    return b.append('"').toString();
}

String opt(Object o) { return o == null ? "null" : esc(o.toString()); }

String eventJson(Event e) {
    return "[" + esc(e.date().toString())
        + "," + esc(e.type().toString())
        + "," + esc(e.description())
        + "," + opt(e.key())
        + "," + opt(e.sourceModule())
        + "," + opt(e.observedFrom())
        + "," + opt(e.closeTime())
        + "," + esc(e.status().toString()) + "]";
}

String nav(DateStream s, String op, LocalDate d, int n) {
    try {
        LocalDate r = switch (op) {
            case "next" -> s.nextBusinessDay(d);
            case "prev" -> s.prevBusinessDay(d);
            default -> s.nthBusinessDay(d, n);
        };
        return esc(r.toString());
    } catch (RuntimeException ex) {
        return "null";
    }
}

var store = new ReleaseHistoryStore(ROOT.resolve("release-history"), ROOT.resolve("blessed"));
var snapshot = store.resolve(CAL, "blessed").orElseThrow();
DateStream stream = store.stream(snapshot);
LocalDate start = stream.range().start();
LocalDate end = stream.range().end();

Files.createDirectories(OUT.getParent());
PrintWriter out = new PrintWriter(Files.newBufferedWriter(OUT, StandardCharsets.UTF_8));
out.print("{\"calendar_id\":" + esc(stream.calendarId()));
out.print(",\"version\":" + esc(snapshot.version()));
out.print(",\"range\":[" + esc(start.toString()) + "," + esc(end.toString()) + "]");
out.print(",\"verified_through\":" + opt(stream.verifiedThrough().orElse(null)));

// Every non-weekend event in the published artifact.
out.print(",\"events\":[");
boolean first = true;
for (Event e : stream.eventsInRange(start, end)) {
    if (e.type().toString().equals("WEEKEND")) continue;
    if (!first) out.print(",");
    first = false;
    out.print(eventJson(e));
}
out.print("]");

// Deterministic, evenly spaced sample of dates across the covered range.
out.print(",\"queries\":[");
long span = end.toEpochDay() - start.toEpochDay();
long step = Math.max(1, span / SAMPLES);
first = true;
for (long i = 0; i <= span; i += step) {
    LocalDate d = start.plusDays(i);
    if (!first) out.print(",");
    first = false;
    out.print("{\"d\":" + esc(d.toString()));
    out.print(",\"b\":" + stream.isBusinessDay(d));
    out.print(",\"n\":" + nav(stream, "next", d, 0));
    out.print(",\"p\":" + nav(stream, "prev", d, 0));
    out.print(",\"f5\":" + nav(stream, "nth", d, 5));
    out.print(",\"b5\":" + nav(stream, "nth", d, -5));
    out.print(",\"s\":" + esc(stream.status(d).toString()));
    out.print(",\"c\":" + opt(stream.closeTime(d).orElse(null)));
    out.print(",\"ec\":" + stream.isEarlyClose(d));
    LocalDate windowEnd = d.plusDays(30).isAfter(end) ? end : d.plusDays(30);
    out.print(",\"cnt\":" + stream.businessDaysInRange(d, windowEnd));
    out.print(",\"we\":" + esc(windowEnd.toString()));
    out.print(",\"ev\":[");
    boolean firstEvent = true;
    for (Event e : stream.eventsOn(d)) {
        if (!firstEvent) out.print(",");
        firstEvent = false;
        out.print(eventJson(e));
    }
    out.print("]}");
}
out.print("]}");
out.close();

System.out.println("Wrote " + OUT + " (" + Files.size(OUT) + " bytes)");

/exit
