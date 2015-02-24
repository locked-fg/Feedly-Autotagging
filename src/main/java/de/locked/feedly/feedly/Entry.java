package de.locked.feedly.feedly;

public class Entry {

    public String id;
    public String originId;
    public String fingerprint;
    public Category[] categories;
    public String author;
    public Tag[] tags;
    public int engagement;
    public long published;
    public String[] keywords;
    public String title;
    public Origin origin;
    public double engagementRate;
    public long crawled;
    public boolean unread;
    public long updated;
    public TypeHref[] alternate;
    public Content summary = new Content();
    public Content content = new Content();
    public long actionTimestamp;


    @Override
    public String toString() {
        return "Entry{title=" + title + ", categories=" + categories + ", author=" + author + ", tags=" + tags + ", engagement=" + engagement + ", published=" + published + ", id=" + id + ", keywords=" + keywords + ", origin=" + origin + ", engagementRate=" + engagementRate + ", crawled=" + crawled + ", unread=" + unread + ", updated=" + updated + ", alternate=" + alternate + '}';
    }
}
