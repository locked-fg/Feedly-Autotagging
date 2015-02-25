package de.locked.feedly;

import com.google.gson.Gson;
import de.locked.feedly.feedly.Category;
import de.locked.feedly.feedly.Entry;
import de.locked.feedly.feedly.Tag;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class App {

    private static final Logger log = LogManager.getLogger(App.class);

    private static final Optional<Boolean> NONE = Optional.empty();
    private static final Optional<Boolean> TRUE = Optional.of(true);
    private static final Optional<Boolean> FALSE = Optional.of(false);
    private static String token;

    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure(App.class.getClassLoader().getResourceAsStream("logging.properties"));
        // get the API token from https://feedly.com/v3/auth/dev
        token = args[0];

        delData();
        getData();

        boolean auto = args.length > 1 && args[1].equals("auto");
        recommendTag(auto);
    }

    private static void delData() {
        log.info("deleting data");
        File data = new File("data");
        if (data.exists()) {
            for (File tagDir : data.listFiles()) {
                for (File f : tagDir.listFiles()) {
                    f.delete();
                }
                tagDir.delete();
            }
        }
    }

    private static void processEntries(String tag, Optional<Boolean> unread, Consumer<Entry> action) {
        Arrays.stream(new File("data/" + tag).listFiles())
                .map(Utils::toEntry)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(o -> !unread.isPresent() || unread.get() == o.unread)
                .forEach(action);
    }

    private static void recommendTag(boolean autoMode) throws IOException {
        log.info("recommending");

        final Tag[] feedlyTags = new Feedly(token).getTags();
        final List<String> tags = Utils.savedTags();
        tags.remove("untagged");

        for (String tag : tags) {
            final long a = System.currentTimeMillis();
            log.info("start tag: " + tag);
            Bayes bayes = new Bayes(tag);

            log.info("train with tagged");
            processEntries(tag, NONE, entry -> bayes.add(tag, Utils.getAllContent(entry)));

            log.info("train with untagged");
            processEntries("untagged", FALSE, entry -> bayes.add("untagged", Utils.getAllContent(entry)));

            log.info("recommend");
            processEntries("untagged", TRUE, entry -> {
                try {
                    double p = bayes.docIsA(Utils.getAllContent(entry));
                    if (autoMode && p >= 0.95) {
                        log.info(String.format("auto: %.2f: %s: %s %s tagged: %b, markRead: %b",
                                p, bayes.getName(),
                                Utils.substr(entry.title, 40),
                                Utils.userTagsAsStrings(entry.tags),
                                tagEntry(entry, bayes.getName(), feedlyTags),
                                new Feedly(token).markAsread(entry))
                        );

                    }
                    if (!autoMode && p >= 0.6) {
                        System.out.println(String.format("%.2f: %s: %s [%s]",
                                p, bayes.getName(), entry.title, Utils.userTagsAsStrings(entry.tags)));
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                        if (br.readLine().trim().equals("j")) {
                            tagEntry(entry, bayes.getName(), feedlyTags);
                        }
                    }
                } catch (IOException ex) {
                    log.fatal("something went brutally wrong", ex);
                }
            });
            bayes.close();

            final long b = System.currentTimeMillis();
            log.info(String.format("recommendation run took: %.2fs", (b - a) / 1000d));
        }
    }

    private static void getData() throws Exception {
        log.info("getting data");
        Feedly urls = new Feedly(token);

        for (Category cat : urls.getCategories()) {
            log.info("getting entries for category: " + cat.label);

            for (Entry entry : urls.getStreamContents(cat.id).items) {
                for (String tag : Utils.userTagsAsStrings(entry.tags)) {
                    File dir = new File("data", tag);
                    dir.mkdirs();
                    File f = new File(dir, Utils.hash(entry.id) + ".txt");
                    if (!f.exists()) {
                        log.info("new Entry: " + f.getAbsolutePath());
                        String json = new Gson().toJson(entry);
                        Files.write(f.toPath(), json.getBytes(), StandardOpenOption.CREATE);
                    }
                }
            }
        }
        System.gc();
    }

    private static boolean tagEntry(final Entry entry, String tagName, Tag[] feedlyTags) throws IOException {
        final Feedly urls = new Feedly(token);
        for (Tag ft : feedlyTags) {
            if (tagName.equals(ft.label)) {
                return urls.tagEntry(entry, ft);
            }
        }
        return false;
    }
}
