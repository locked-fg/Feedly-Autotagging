package de.locked.feedly.feedly;

import java.util.Arrays;

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
        return "Entry{title=" + title + ", categories=" + Arrays.toString(categories) + ", author=" + author 
                + ", tags=" + Arrays.toString(tags) + ", engagement=" + engagement + ", published=" + published 
                + ", id=" + id + ", keywords=" + Arrays.toString(keywords) + ", origin=" + origin 
                + ", engagementRate=" + engagementRate + ", crawled=" + crawled + ", unread=" + unread 
                + ", updated=" + updated + ", alternate=" + Arrays.toString(alternate) + '}';
    }
}
