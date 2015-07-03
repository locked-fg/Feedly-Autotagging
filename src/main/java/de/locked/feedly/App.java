package de.locked.feedly;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import de.locked.feedly.feedly.Category;
import de.locked.feedly.feedly.Entry;
import de.locked.feedly.feedly.StreamContent;
import de.locked.feedly.feedly.Tag;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import static java.nio.file.StandardOpenOption.*;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class App {

    private static final Logger log = LogManager.getLogger(App.class);
    private static final boolean useDB = false;
    private static String token;
    private static final double autoTagThreshold = 0.9;

    // params evaluated through test()
    private static final BayesCfg BAYES_CFG = new BayesCfg(1, 0.94, 0.03);

    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure(App.class.getClassLoader().getResourceAsStream("logging.properties"));
        // get the API token from https://feedly.com/v3/auth/dev
        token = args[0];

        delData();
        getData();
        boolean auto = args.length > 1 && args[1].equals("auto");
        recommendTag(auto);
//        test();
    }

    private static void delData() {
        log.info("deleting data");
        File data = new File("data");
        if (data.exists()) {
            for (File f : data.listFiles()) {
                f.delete();
            }
        }
        File data2 = new File("databases");
        if (data2.exists()) {
            for (File f : data2.listFiles()) {
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

    private static void processTag(final Tag tagObj, boolean autoMode, List<Tag> feedlyTags) {
        final long a = System.currentTimeMillis();
        final String tag = tagObj.label;

        log.info("start tag: " + tag);
        Bayes bayes = new Bayes(tag, useDB);

        log.info("train");
        processEntries()
                .forEach(e -> {
                    List<String> content = Utils.getAllContent(e);
                    if (Utils.containsTag(e.tags, tag)) {
                        bayes.add(tag, content);
                    } else if (!e.unread) {
                        bayes.add("", content);
                    }
                });
        bayes.reduce(BAYES_CFG);

        log.info("recommend");
        processEntries()
                .filter(e -> e.unread)
                .filter(e -> Utils.untagged(e.tags))
                .forEach(entry -> {
                    double p = bayes.docIsA(Utils.getAllContent(entry));
                    log.debug(String.format("%s : %.02f : %s", tag, p, entry.title));
                    try {
                        if (autoMode && p >= autoTagThreshold) {
                            log.info(String.format("auto: %.2f: %s: %s tagged: %b, markRead: %b",
                                            p, bayes.getName(),
                                            Utils.substr(entry.title, 40),
                                            tagEntry(entry, bayes.getName(), feedlyTags),
                                            new Feedly(token).markAsread(entry))
                            );
                        }
                        if (!autoMode && p >= 0.6) {
                            System.out.println(String.format("%.2f: %s: %s",
                                            p, bayes.getName(), entry.title));
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
        for (Category cat : urls.getCategories()) {
            log.info("getting entries for category: " + cat.label);
            StreamContent stream = urls.getStreamContents(cat.id);
            saveEntries(stream.items);
            while (stream.continuation != null) {
                stream = urls.getStreamContents(cat.id, stream.continuation);
                saveEntries(stream.items);
            }

        }
    }

    private static void saveEntries(Entry[] entries) throws NoSuchAlgorithmException, IOException {
        Gson gson = new Gson();
        File data = new File("data");
        data.mkdir();
        for (Entry entry : entries) {
            File f = new File(data, Utils.hash(entry.id) + ".txt");
            Files.write(f.toPath(), gson.toJson(entry).getBytes(), CREATE, TRUNCATE_EXISTING);
            // f.deleteOnExit();
        }
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

    private static void test() throws Exception {
        log.info("testing");
        Tag[] tags = new Feedly(token).getTags();

        int folds = 15;
        // params in BayesCfg
        int[] minDocs = {1,2};
        double[] maxDocPs = {0.98, 0.97, 0.96, 0.95, 0.94};
        double[] minPs = {0.00, 0.01, 0.015, 0.02, 0.03, 0.04, 0.05};

        List<BayesCfg> configs = new ArrayList<>();
        for (int minDoc : minDocs) {
            for (double maxDocP : maxDocPs) {
                for (double minP : minPs) {
                    configs.add(new BayesCfg(minDoc, maxDocP, minP));
                }
            }
        }

        SimpleEntry<BayesCfg, Double> x = configs.stream().parallel()
                .map(config -> {
                    double f = IntStream.rangeClosed(1, folds).asDoubleStream()
                    .map(i -> test(tags, config)) // compute f measure
                    .filter(d -> !Double.isNaN(d))
                    .summaryStatistics().getAverage();
                    return new SimpleEntry<>(config, f);
                })
                .sorted((o1, o2) -> -1 * Double.compare(o1.getValue(), o2.getValue())) // sort reversed!
                .findFirst()
                .get();
        log.info("best cfg: " + x);
    }

    private static Double test(Tag[] tags, BayesCfg cfg) {
        double testRatio = 0.1;
        double pThreshold = autoTagThreshold;

        double tp = 0;
        double tn = 0;
        double fp = 0;
        double fn = 0;

        Map<String, Bayes> classifiers = Arrays.stream(tags)
                .filter(t -> t.label != null)
                .map(tag -> new Bayes(tag.label, false))
                .collect(Collectors.toMap(b -> b.getName(), b -> b));

//            log.info("split train / test");
        Multimap<String, Entry> train = ArrayListMultimap.create();
        Multimap<String, Entry> test = ArrayListMultimap.create();
        processEntries()
                .forEach(e -> {
                    String label = Utils.untagged(e.tags) ? "untagged" : e.tags[0].label;
                    Multimap<String, Entry> target = Math.random() < testRatio ? test : train;
                    target.put(label, e);
                });

//            log.info("train");
        for (String k : train.keySet()) {
            for (Entry e : train.get(k)) {
                List<String> content = Utils.getAllContent(e);
                for (Bayes b : classifiers.values()) {
                    b.add(k, content);
                }
            }
        }
        for (Bayes b : classifiers.values()) {
            b.reduce(cfg);
        }

//            log.info("test");
        for (String k : test.keySet()) {
            for (Entry e : test.get(k)) {
                List<String> content = Utils.getAllContent(e);
                for (Bayes b : classifiers.values()) {
                    boolean match = b.docIsA(content) > pThreshold;
                    boolean shouldMatch = k.equals(b.getName());
                    if (match && shouldMatch) {
                        tp++;
                    } else if (match && !shouldMatch) {
                        fp++;
                    } else if (!match && shouldMatch) {
                        fn++;
                    } else if (!match && !shouldMatch) {
                        tn++;
                    }
                }
            }
        }
        double precision = tp / (tp + fp);
        double recall = tp / (tp + fn);
        double beta2 = Math.pow(0.75, 2); // f(0.5) - put weight on precision
        double f = (1 + beta2) * (precision * recall) / (beta2 * precision + recall);
        log.info(String.format("F-Measure: %.02f with %s \t Precision: %.02f, Recall: %.02f",
                f, cfg, precision, recall));
        return f;
    }
}

class BayesCfg {

    int minDoc;
    double maxDocP;
    double minP;

    public BayesCfg(int minDoc, double maxDocP, double minP) {
        this.minDoc = minDoc;
        this.maxDocP = maxDocP;
        this.minP = minP;
    }

    @Override
    public String toString() {
        return "BayesCfg{" + "minDoc=" + minDoc + ", maxDocP=" + maxDocP + ", minP=" + minP + '}';
    }
}
