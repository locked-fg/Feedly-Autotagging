package de.locked.feedly;

import java.io.File;
import java.io.Serializable;
import static java.lang.Math.min;
import static java.lang.Math.max;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.mapdb.*;

// http://www.paulgraham.com/spam.html
// http://www.paulgraham.com/naivebayes.html
public class Bayes {

    private static final int TOP_X_WORDS = 100;

    private final HashMap<String, Container> map = new HashMap<>();
//    private final HTreeMap<String, Container> map;
    private final String name;
    private double na = 0;
    private double nb = 0;
//    private final DB db;

    Bayes(String tag) {
        this.name = tag;
        File dbLoc = new File("databases/");
        dbLoc.mkdir();
        
//        this.db = DBMaker.newFileDB(new File(dbLoc, tag + ".db"))
//                .closeOnJvmShutdown()
//                .deleteFilesAfterClose()
//                .transactionDisable()
//                .make();
//        map = this.db.getHashMap(name);
    }

    public String getName() {
        return name;
    }

    void close(){
//        db.close();
    }
    
    private double isA(String word) {
        Container c = map.get(word);
        if (c == null || c.a + c.b < 5) {
            return .5;
        }

        double pa = min(1, (double)c.a / na); // c.a() x 2 due to PaulGraham
        double pb = min(1, (double)c.b / nb);
        double p = pa / (pa + pb);
        return max(.01, min(0.99, p)); // bind into [.01, .99]
    }

    private Container put(String word) {
        if (!map.containsKey(word)) {
            map.put(word.intern(), new Container());
        }
        return map.get(word.intern());
    }

    public void add(String className, List<String> words) {
        if (this.name.equals(className)) {
            addA(words);
        } else {
            addB(words);
        }
    }

    private void addA(List<String> words) {
        for (String word : words) {
            put(word).a++;
        }
        na++;
    }

    private synchronized void addB(List<String> words) {
        for (String word : words) {
            put(word).b++;
        }
        nb++;
    }

    private int from05(double a, double b) {
        a = Math.abs(a - 0.5);
        b = Math.abs(b - 0.5);
        return Double.compare(a, b);
    }

    double docIsA(List<String> strings) {
        List<Double> pset = strings.stream()
                .map(str -> isA(str)) // get the probabilities
                .sorted((p1, p2) -> -from05(p1, p2)) // sort by descending distance from 0.5
                .limit(TOP_X_WORDS)
                .collect(Collectors.toList());

        double p = 1, np = 1;
        for (Double ap : pset) {
            p *= ap;
            np *= 1 - ap;
        }
        return p / (p + np);
    }

    @Override
    public String toString() {
        return "Bayes{" + "name=" + name + ", na=" + na + ", nb=" + nb + '}';
    }
}

class Container implements Serializable {

    int a = 0;
    int b = 0;
}
