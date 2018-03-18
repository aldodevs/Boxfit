package com.manuege.boxfit.converters;

import com.manuege.boxfit.annotations.JsonSerializable;
import com.manuege.boxfit.serializers.AbstractMainSerializer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * Created by Manu on 11/3/18.
 */

public class JsonSerializableConverterFactory extends Converter.Factory {

    AbstractMainSerializer jsonSerializer;

    public JsonSerializableConverterFactory(AbstractMainSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        if (typeIsJsonSerializable(type)) {
            return new ResponseSerializableConverter((Class<?>) type);
        } else if (type instanceof ParameterizedType &&
                ((ParameterizedType) type).getRawType() instanceof Class &&
                List.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType()) &&
                typeIsJsonSerializable(((ParameterizedType) type).getActualTypeArguments()[0])) {
            return new ResponseSerializableManyConverter((Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0]);
        }
        return null;
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        return super.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
    }

    private boolean typeIsJsonSerializable(Type type) {
        if (!(type instanceof Class)) {
            return false;
        }

        Class clazz = (Class) type;
        return clazz.getAnnotation(JsonSerializable.class) != null;

    }

    private class ResponseSerializableConverter<T> implements Converter<ResponseBody, T> {
        Class<? extends T> clazz;

        private ResponseSerializableConverter(Class<? extends T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T convert(ResponseBody value) throws IOException {
            String jsonString = value.string();
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                return jsonSerializer.serialize(clazz, jsonObject);
            } catch (JSONException e) {
                return null;
            }
        }
    }

    private class ResponseSerializableManyConverter<T> implements Converter<ResponseBody, List<T>> {
        Class<? extends T> clazz;

        private ResponseSerializableManyConverter(Class<? extends T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public List<T> convert(ResponseBody value) throws IOException {
            String jsonString = value.string();
            try {
                JSONArray jsonArray = new JSONArray(jsonString);
                return jsonSerializer.serialize(clazz, jsonArray);
            } catch (JSONException e) {
                return null;
            }
        }
    }
}