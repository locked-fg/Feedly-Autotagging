package de.locked.feedly;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import de.locked.feedly.feedly.Category;
import de.locked.feedly.feedly.Entry;
import de.locked.feedly.feedly.Tag;
import de.locked.feedly.feedly.TypeHref;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;

public class Utils {

    private static final Logger log = Logger.getLogger(Utils.class.getName());

    static String hash(String s) throws NoSuchAlgorithmException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        return Hex.encodeHexString(md5.digest(s.getBytes()));
    }

    static List<String> userTagsAsStrings(Tag[] tags) {
        if (tags == null || tags.length == 0) {
            return Arrays.asList("untagged");
        } else {
            List<String> list = Arrays.stream(tags)
                    .map(t -> t.label)
                    .filter(l -> l != null)
                    .filter(l -> !l.isEmpty())
                    .filter(l -> !l.startsWith("global."))
                    .collect(Collectors.toList());
            if (list.isEmpty()) {
                list.add("untagged");
            }
            return list;
        }
    }

    static String tagToString(Tag t) {
        String s = (t.label != null) ? t.label.trim() : "untagged";
        if (s.equals("global.read")) {
            s = "";
        }
        return s;
    }

    static List<String> contentFromFile(File f) {
        try {
            return getAllContent(fromFile(f));
        } catch (IOException ex) {
            log.log(Level.WARN, null, ex);
            return Collections.EMPTY_LIST;
        }
    }

    static Optional<Entry> toEntry(File f) {
        try {
            return Optional.of(fromFile(f));
        } catch (IOException ex) {
            log.log(Level.WARN, null, ex);
            return Optional.empty();
        }
    }

    static Entry fromFile(File f) throws UnsupportedEncodingException, IOException {
        // String json = new String(Files.readAllBytes(f.toPath()), "UTF-8");
        String json = new String(Files.readAllBytes(f.toPath()));
        return new Gson().fromJson(json, Entry.class);
    }

    // strip html: JSoup; http://stackoverflow.com/questions/832620/stripping-html-tags-in-java
    static LinkedList<String> splitContent(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return new LinkedList();
        }
        s = new HtmlToPlainText().getPlainText(Jsoup.parse(s));
        return new LinkedList<>(Arrays.asList(s.toLowerCase().split("[^a-zäöüß]+")));
    }

    static List<String> splitContent(Entry entry) {
        return getAllContent(entry);
    }

    static List<File> savedTagDirs() {
        return Arrays.asList(new File("data/").listFiles());
    }

    static List<String> savedTags() {
        return savedTagDirs().stream()
                .filter(f -> f.isDirectory())
                .map(f -> f.getName())
                .collect(Collectors.toList());
    }

    static List<String> getAllContent(Optional<Entry> e) {
        return e.isPresent() ? getAllContent(e.get()) : Collections.EMPTY_LIST;
    }

    static List<String> getAllContent(Entry e) {
        ArrayList<String> list = new ArrayList<>();
        list.addAll(splitContent(e.summary.content));
        list.addAll(splitContent(e.content.content));
        list.addAll(splitContent(e.title));
        list.addAll(splitContent(e.originId));
        list.addAll(splitContent(e.origin.title));
        list.addAll(toList(e.keywords));
        for (TypeHref th : toList(e.alternate)) {
            list.addAll(splitContent(th.href));
        }
        for (Category category : toList(e.categories)) {
            list.add(category.label);
        }

        List<String> filtered = list.stream()
                .filter(s -> !s.isEmpty())
                .map(String::intern)
                .collect(Collectors.toList());
        return filtered;
    }

    static <T> List<T> toList(T[] tt) {
        if (tt == null) {
            return Collections.EMPTY_LIST;
        } else {
            return Arrays.asList(tt);
        }
    }

    static String substr(String s, int length){
        return s.length() < length ? s : s.substring(0, length);
    }
}
