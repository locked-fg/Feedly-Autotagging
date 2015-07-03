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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.IIOException;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class Feedly {

    private static final Logger log = LogManager.getLogger(Feedly.class);
    private final String base = "http://cloud.feedly.com/v3/";
    private final BasicHeader auth;
    private static int requests = 0;

    public Feedly(String accessToken) {
        auth = new BasicHeader("Authorization", accessToken);
    }

    public Category[] getCategories() throws IOException {
        String json = getAsString(base + "categories");
        return new Gson().fromJson(json, Category[].class);
    }

    public Tag[] getTags() throws IOException {
        String json = getAsString(base + "tags");
        return new Gson().fromJson(json, Tag[].class);
    }

    // https://developer.feedly.com/v3/streams/
    // continuation
    // count 20-10.000
    public List<Entry> getAllStreamContents(String id) throws IOException, URISyntaxException {
        StreamContent stream = getStreamContents(id);
        List<Entry> entries = new ArrayList<>(Arrays.asList(stream.items));

        while (stream.continuation != null) {
            stream = getStreamContents(id, stream.continuation);
            entries.addAll(Arrays.asList(stream.items));
        }
        return entries;
    }

    public StreamContent getStreamContents(String id) throws IOException, URISyntaxException {
        URI uri = new URI(base + "streams/contents?streamId=" + enc(id) + "&count=10000");
        return getStreamContents(uri);
    }

    public StreamContent getStreamContents(String id, String continuation) throws IOException, URISyntaxException {
        URI uri = new URI(base + "streams/contents?streamId=" + enc(id) + "&count=10000&continuation=" + enc(continuation));
        return getStreamContents(uri);
    }

    public StreamContent getStreamContents(URI uri) throws IOException {
        String data = getAsString(uri);
        return new Gson().fromJson(data, StreamContent.class);
    }

    boolean tagEntry(Entry entry, Tag tag) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("entryId", entry.id);
        return exec(Request.Put(base + "tags/" + enc(tag.id)), o);
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
        requests++;
        log.info("Request #" + requests + ", " + request.toString());
        int status = request.setHeader(auth).execute()
                .returnResponse().getStatusLine().getStatusCode();
        return 200 == status;
    }

    private String getAsString(String urlPart) throws IOException {
        try {
            return getAsString(new URI(urlPart));
        } catch (URISyntaxException ex) {
            log.warn("Invalid URI", ex);
            throw new IIOException("invalid URI for "+urlPart, ex);
        }
    }
    
    private String getAsString(URI urlPart) throws IOException {
        requests++;
        log.info("Request #" + requests + ", " + urlPart);
        return Request.Get(urlPart).setHeader(auth).execute().returnContent().asString();
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
