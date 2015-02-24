package de.locked.feedly;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.locked.feedly.feedly.Category;
import de.locked.feedly.feedly.Entry;
import de.locked.feedly.feedly.StreamContent;
import de.locked.feedly.feedly.Tag;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;

public class Feedly {

    private final String base = "http://cloud.feedly.com/v3/";
    private final BasicHeader auth;

    public Feedly(String accessToken) {
        auth = new BasicHeader("Authorization", accessToken);
    }

    public Category[] getCategories() throws IOException {
        String json = Request.Get(base + "categories")
                .setHeader(auth).execute().returnContent().asString();
        return new Gson().fromJson(json, Category[].class);
    }

    public Tag[] getTags() throws IOException {
        String json = Request.Get(base + "tags")
                .setHeader(auth).execute().returnContent().asString();
        return new Gson().fromJson(json, Tag[].class);
    }

    // https://developer.feedly.com/v3/streams/
    // continuation
    // count 20-10.000
    public StreamContent getStreamContents(String id) throws IOException {
        return new Gson().fromJson(getStreamContentsAsString(id), StreamContent.class);
    }

    public String getStreamContentsAsString(String id) throws IOException {
        return Request.Get(base + "streams/contents?streamId=" + enc(id) + "&count=10000")
                .setHeader(auth).execute().returnContent().asString();
    }

    boolean tagEntry(Entry entry, Tag tag) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("entryId", entry.id);
        return exec(Request.Put(base + "tags/" + enc(tag.id)),o);
    }

    boolean untagEntry(Entry entry, Tag tag) throws IOException {
        return exec(Request.Delete(base + "tags/" + enc(tag.id) + "/" + entry.id));
    }

    boolean deleteTag(Tag tag) throws IOException {
        return exec(Request.Delete(base + "tags/" + enc(tag.id)));
    }

    boolean markAsread(Entry entry) throws IOException {
        return exec(Request.Post(base + "/markers"), new Marker(entry.id));
    }

    private boolean exec(Request request, Object o) throws IOException {
        String json = new Gson().toJson(o);
        return exec(request.bodyString(json, ContentType.APPLICATION_JSON));
    }

    private boolean exec(Request request) throws IOException {
        int status = request.setHeader(auth).execute()
                .returnResponse().getStatusLine().getStatusCode();
        return 200 == status;
    }

    private String enc(String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, Charsets.UTF_8.name());
    }
}

// https://developer.feedly.com/v3/markers/#mark-one-or-multiple-articles-as-read
class Marker {

    String type = "entries";
    String action = "markAsRead";
    String[] entryIds;

    public Marker(String... ids) {
        entryIds = ids;
    }
}
