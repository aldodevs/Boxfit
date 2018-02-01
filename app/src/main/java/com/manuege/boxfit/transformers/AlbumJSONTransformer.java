package com.manuege.boxfit.transformers;

import com.manuege.boxfit.library.transformers.JSONObjectTransformer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Manu on 1/2/18.
 */

public class AlbumJSONTransformer implements JSONObjectTransformer {
    @Override
    public JSONObject transform(JSONObject object) {
        try {
            if (object.has("_id")) {
                object.put("id", object.get("_id"));
                object.remove("_id");
            }
        } catch (JSONException ignore) {}

        return object;
    }
}