package com.manuege.boxfitapp.model;

import com.manuege.boxfit.serializers.AbstractSerializer;
import com.manuege.boxfit.utils.Json;
import com.manuege.boxfit.utils.JsonArray;
import com.manuege.boxfitapp.transformers.AlbumJSONTransformer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;

/**
 * Created by Manu on 28/1/18.
 */

public class AlbumJsonSerializer extends AbstractSerializer<Album, Long> {

    public AlbumJsonSerializer(BoxStore boxStore) {
        super(boxStore);
    }

    @Override
    protected Box<Album> getBox() {
        return boxStore.boxFor(Album.class);
    }

    @Override
    protected void merge(Json json, Album object) {
        if (json.has("name")) {
            object.name = json.getString("name");
        }

        if (json.has("year")) {
            object.year = json.getInt("year", 0);
        }

        if (json.has("rate")) {
            object.rate = json.getInt("rate");
        }

        if (json.has("artist")) {
            ArtistJsonSerializer serializer = new ArtistJsonSerializer(boxStore);
            object.artist.setTarget(serializer.serializeRelationship(json, "artist"));
        }

        if (json.has("genre")) {
            GenreJsonSerializer serializer = new GenreJsonSerializer(boxStore);
            object.genre.setTarget(serializer.serializeRelationship(json, "genre"));
        }

        if (json.has("tracks")) {
            JSONArray jsonArray = json.getJSONArray("tracks");
            object.tracks.clear();
            if (jsonArray != null) {
                TrackJsonSerializer serializer = new TrackJsonSerializer(boxStore);
                List<Track> property = serializer.serialize(jsonArray);
                object.tracks.addAll(property);
            }
        }
    }

    @Override
    protected Album createFreshObject(Long id) {
        Album object = new Album();
        object.id = id;
        return object;
    }

    @Override
    protected Long getId(Json json) {
        return json.getLong("id");
    }

    @Override
    protected Long getId(Album object) {
        return object.id;
    }

    @Override
    protected JSONObject getJSONObject(Long id) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", id);
            return jsonObject;
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    protected Long getId(Json json, String key) {
        return json.getLong(key);
    }

    @Override
    protected Long getId(JsonArray array, int index) {
        return array.getLong(index);
    }

    @Override
    protected Album getExistingObject(Long aLong) {
        return getBox().get(aLong);
    }

    @Override
    protected List<Album> getExistingObjects(List<Long> longs) {
        return getBox().get(longs);
    }

    @Override
    protected JSONObject getTransformedJSONObject(JSONObject object) {
        AlbumJSONTransformer transformer = new AlbumJSONTransformer();
        return transformer.transform(object);
    }
}