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
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
            for (File f : data.listFiles()) {
                f.delete();
            }
        }
    }

    private static Stream<Entry> processEntries() {
        return Arrays.stream(new File("data").listFiles())
                .map(Utils::toEntry)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private static void recommendTag(boolean autoMode) throws IOException {
        log.info("recommending");

        List<Tag> tags = Arrays.stream(new Feedly(token).getTags())
                .filter(t -> t.label != null)
                .collect(Collectors.toList());

        for (Tag tag : tags) {
            processTag(tag, autoMode, tags);
        }
    }

    private static boolean containsTag(Tag[] tags, String tag) {
        if(tags == null) return false;
        for (Tag t : tags) {
            if (tag.equals(t.label)) {
                return true;
            }
        }
        return false;
    }

    private static boolean untagged(Tag[] tags) {
        if(tags == null) return true;
        for (Tag tag : tags) {
            if (tag.label != null) {
                return false;
            }
        }
        return true;
    }

    private static void processTag(final Tag tagObj, boolean autoMode, List<Tag> feedlyTags) {
        final long a = System.currentTimeMillis();
        final String tag = tagObj.label;

        log.info("start tag: " + tag);
        Bayes bayes = new Bayes(tag);

        log.info("train");
        processEntries()
                .forEach(e -> {
                    List<String> content = Utils.getAllContent(e);
                    if (containsTag(e.tags, tag)) {
                        bayes.add(tag, content);
                    } else if (!e.unread) {
                        bayes.add("", content);
                    }
                });

        log.info("recommend");
        processEntries()
                .filter(e -> e.unread)
                .filter(e -> untagged(e.tags))
                .forEach(entry -> {
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

    private static void getData() throws Exception {
        log.info("getting data");
        Feedly urls = new Feedly(token);
        Gson gson = new Gson();
        for (Category cat : urls.getCategories()) {
            log.info("getting entries for category: " + cat.label);
            for (Entry entry : urls.getStreamContents(cat.id).items) {
                File f = new File("data", Utils.hash(entry.id) + ".txt");
                if (!f.exists()) {
                    log.info("new Entry: " + f.getAbsolutePath());
                    String json = gson.toJson(entry);
                    Files.write(f.toPath(), json.getBytes(), StandardOpenOption.CREATE);
                }
            }
        }
        System.gc();
    }

    private static boolean tagEntry(final Entry entry, String tagName, List<Tag> feedlyTags) throws IOException {
        final Feedly urls = new Feedly(token);
        for (Tag ft : feedlyTags) {
            if (tagName.equals(ft.label)) {
                return urls.tagEntry(entry, ft);
            }
        }
        return false;
    }
}
