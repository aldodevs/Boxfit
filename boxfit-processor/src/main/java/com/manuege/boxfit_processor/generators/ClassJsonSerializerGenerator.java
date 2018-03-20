package com.manuege.boxfit_processor.generators;

import com.manuege.boxfit.serializers.AbstractSerializer;
import com.manuege.boxfit.utils.Json;
import com.manuege.boxfit.utils.JsonArray;
import com.manuege.boxfit_processor.info.ClassInfo;
import com.manuege.boxfit_processor.info.FieldInfo;
import com.manuege.boxfit_processor.info.Utils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

import io.objectbox.Box;
import io.objectbox.BoxStore;

/**
 * Created by Manu on 1/2/18.
 */

public class ClassJsonSerializerGenerator extends AbstractFileGenerator {
    ClassInfo classInfo;

    public ClassJsonSerializerGenerator(ProcessingEnvironment environment, ClassInfo classInfo) {
        super(environment);
        this.classInfo = classInfo;
    }

    @Override
    protected TypeSpec getTypeSpec() {
        ClassName abstractSerializerClass = ClassName.get(AbstractSerializer.class);
        ParameterizedTypeName superclass = ParameterizedTypeName.get(abstractSerializerClass, getEntityTypeName(), getPrimaryKeyTypeName());

        // Class definition
        TypeSpec.Builder serializerClass = TypeSpec
                .classBuilder(getSerializerClassName())
                .superclass(superclass)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Methods
        serializerClass
                .addField(getSerializerClassName(), "instance", Modifier.STATIC, Modifier.PRIVATE)
                .addMethod(getSingleton())
                .addMethod(getConstructor())
                .addMethod(getMergeMethod())
                .addMethod(getBoxMethod())
                .addMethod(getCreateFreshObject())
                .addMethod(getIdFromJsonMethod())
                .addMethod(getIdFromJsonAndKeyMethod())
                .addMethod(getIdFromArrayMethod())
                .addMethod(getIdFromObjectMethod())
                .addMethod(getJsonObjectFromIdMethod())
                .addMethod(getExistingObjectMethod())
                .addMethod(getExistingObjectsMethod())
                .addMethod(getToJsonMethod());

        if (classInfo.getTransformer() != null) {
            serializerClass.addMethod(getTransformedJsonMethod());
        }

        return serializerClass.build();
    }

    private MethodSpec getSingleton() {
        return MethodSpec.methodBuilder("getInstance")
                .returns(getSerializerClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .beginControlFlow("if (instance == null)")
                .addStatement("instance = new $T()", getSerializerClassName())
                .endControlFlow()
                .addStatement("return instance")
                .build();
    }

    private MethodSpec getConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    private MethodSpec getMergeMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("merge")
                .addParameter(Json.class, "json")
                .addParameter(classInfo.getType(), "object")
                .addParameter(BoxStore.class, "boxStore")
                .addModifiers(Modifier.PROTECTED);

        for (FieldInfo fieldInfo: classInfo.getFields()) {
            addFieldSerializationForMergeMethod(builder, fieldInfo);
        }

        return builder.build();
    }

    private void addFieldSerializationForMergeMethod(MethodSpec.Builder builder, FieldInfo fieldInfo) {
        builder.beginControlFlow("if (json.has($S))", fieldInfo.getSerializedName());

        switch (fieldInfo.getKind()) {
            case NORMAL:
                addNormalFieldSerializer(builder, fieldInfo);
                break;
            case TRANSFORMED:
                addTransformedFieldSerializer(builder, fieldInfo);
                break;
            case TO_ONE:
                addToOneFieldSerializer(builder, fieldInfo);
                break;
            case TO_MANY:
                addToManyFieldSerializer(builder, fieldInfo);
                break;
            case JSON_SERIALIZABLE:
                addJsonSerializableFieldSerializer(builder, fieldInfo);
        }

        builder.endControlFlow();
    }

    private void addNormalFieldSerializer(MethodSpec.Builder builder, FieldInfo fieldInfo) {
        builder.addStatement("object.$N = json.$N($S)", fieldInfo.getName(), fieldInfo.getJsonGetterMethodName(), fieldInfo.getSerializedName());
    }

    private void addTransformedFieldSerializer(MethodSpec.Builder builder, FieldInfo fieldInfo) {
        builder.addStatement("$T originalValue = json.$N($S)", fieldInfo.getJsonFieldTypeName(), fieldInfo.getJsonGetterMethodName(), fieldInfo.getSerializedName());
        builder.addStatement("$T transformer = new $T()", fieldInfo.getTransformerName(), fieldInfo.getTransformerName());
        builder.addStatement("object.$N = transformer.transform(originalValue)", fieldInfo.getName());
    }

    private void addToOneFieldSerializer(MethodSpec.Builder builder, FieldInfo fieldInfo) {
        TypeName serializer = fieldInfo.getRelationshipSerializerName();
        builder.addStatement("$T serializer = $T.getInstance()", serializer, serializer);
        builder.addStatement("object.$N.setTarget(serializer.serializeRelationship(json, $S, boxStore))", fieldInfo.getName(), fieldInfo.getSerializedName());
    }

    private void addToManyFieldSerializer(MethodSpec.Builder builder, FieldInfo fieldInfo) {
        builder.addStatement("$T jsonArray = json.getJSONArray($S)", JSONArray.class, fieldInfo.getSerializedName());
        builder.addStatement("object.$N.clear()", fieldInfo.getName());
        builder.beginControlFlow("if (jsonArray != null)");
        TypeName serializer = fieldInfo.getRelationshipSerializerName();
        builder.addStatement("$T serializer = $T.getInstance()", serializer, serializer);
        builder.addStatement("$T<$T> property = serializer.fromJson(jsonArray, boxStore)", List.class, fieldInfo.getRelationshipName());
        builder.addStatement("object.$N.addAll(property)", fieldInfo.getName());
        builder.endControlFlow();
    }

    private void addJsonSerializableFieldSerializer(MethodSpec.Builder builder, FieldInfo fieldInfo) {
        TypeName serializer = fieldInfo.getRelationshipSerializerName();
        builder.addStatement("$T serializer = $T.getInstance()", serializer, serializer);
        builder.addStatement("object.$N = serializer.serializeRelationship(json, $S, boxStore)", fieldInfo.getName(), fieldInfo.getSerializedName());
    }

    private MethodSpec getBoxMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getBox")
                .addParameter(BoxStore.class, "boxStore")
                .addModifiers(Modifier.PROTECTED)
                .returns(ParameterizedTypeName.get(ClassName.get(Box.class), getEntityTypeName()));

        if (classInfo.isEntity()) {
            builder.addStatement("return boxStore.boxFor($T.class)", classInfo.getType());
        } else {
            builder.addStatement("return null");
        }

        return builder.build();
    }

    private MethodSpec getCreateFreshObject() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("createFreshObject")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(getPrimaryKeyTypeName(), "id")
                .returns(getEntityTypeName())
                .addStatement("$T object = new $T()", getEntityTypeName(), getEntityTypeName());

        if (classInfo.hasPrimaryKey()) {
            builder.addStatement("object.$N = id", classInfo.getPrimaryKey().getName());
        }

        builder.addStatement("return object");

        return builder.build();
    }

    private MethodSpec getIdFromJsonMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getId")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(Json.class, "json")
                .returns(getPrimaryKeyTypeName());

        if (classInfo.hasPrimaryKey()) {
            FieldInfo primaryKey = classInfo.getPrimaryKey();
            builder.addStatement("return json.$N($S)", primaryKey.getJsonGetterMethodName(), primaryKey.getSerializedName());
        } else {
            builder.addStatement("return null");
        }

        return builder.build();
    }

    private MethodSpec getIdFromJsonAndKeyMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getId")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(Json.class, "json")
                .addParameter(String.class, "key")
                .returns(getPrimaryKeyTypeName());

        if (classInfo.hasPrimaryKey()) {
            FieldInfo primaryKey = classInfo.getPrimaryKey();
            builder.addStatement("return json.$N(key)", primaryKey.getJsonGetterMethodName());
        } else {
            builder.addStatement("return null");
        }

        return builder.build();
    }

    private MethodSpec getIdFromArrayMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getId")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(JsonArray.class, "array")
                .addParameter(TypeName.INT, "index")
                .returns(getPrimaryKeyTypeName());

        if (classInfo.hasPrimaryKey()) {
            builder.addStatement("return array.$N(index)", classInfo.getPrimaryKey().getJsonGetterMethodName());
        } else {
            builder.addStatement("return null");
        }

        return builder.build();
    }

    private MethodSpec getIdFromObjectMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getId")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(getEntityTypeName(), "object")
                .returns(getPrimaryKeyTypeName());

        if (classInfo.hasPrimaryKey()) {
            builder.addStatement("return object.$N", classInfo.getPrimaryKey().getName());
        } else {
            builder.addStatement("return null");
        }

        return builder.build();
    }

    private MethodSpec getJsonObjectFromIdMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getJSONObject")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(getPrimaryKeyTypeName(), "id")
                .returns(JSONObject.class)
                .addStatement("$T jsonObject = new $T()", JSONObject.class, JSONObject.class);

        if (classInfo.hasPrimaryKey()) {
            builder.beginControlFlow("try")
                    .addStatement("jsonObject.put($S, id)", classInfo.getPrimaryKey().getSerializedName())
                    .endControlFlow()
                    .beginControlFlow("catch($T ignored)", JSONException.class).endControlFlow();
        }

        builder.addStatement("return jsonObject");

        return builder.build();
    }

    private MethodSpec getExistingObjectMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getExistingObject")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(getPrimaryKeyTypeName(), "id")
                .addParameter(BoxStore.class, "boxStore")
                .returns(getEntityTypeName());

        if (classInfo.hasPrimaryKey()) {
            builder.addStatement("return getBox(boxStore).get(id)");
        } else {
            builder.addStatement("return null");
        }

        return builder.build();
    }

    private MethodSpec getExistingObjectsMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getExistingObjects")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(ParameterizedTypeName.get(ClassName.get(List.class), getPrimaryKeyTypeName()), "ids")
                .addParameter(BoxStore.class, "boxStore")
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), getEntityTypeName()));

        if (classInfo.hasPrimaryKey()) {
            builder.addStatement("return getBox(boxStore).get(ids)");
        } else {
            builder.addStatement("return new $T<>()", ArrayList.class);
        }

        return builder.build();
    }

    private MethodSpec getTransformedJsonMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("getTransformedJSONObject")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(JSONObject.class, "object")
                .returns(JSONObject.class)
                .addStatement("return new $T().transform(object)", classInfo.getTransformer());

        return builder.build();
    }

    private MethodSpec getToJsonMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("toJson")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(getEntityTypeName(), "object")
                .returns(JSONObject.class);

        builder.addStatement("$T json = new $T()", JSONObject.class, JSONObject.class);
        builder.beginControlFlow("try");

        for (FieldInfo fieldInfo: classInfo.getFields()) {
            if (fieldInfo.isToJsonIgnore()) {
                continue;
            }

            TypeName serializer = fieldInfo.getRelationshipSerializerName();
            String serializerName = fieldInfo.getName() + "Serializer";

            if (fieldInfo.getKind() == FieldInfo.Kind.NORMAL) {
                if (fieldInfo.isPrimitive()) {
                    builder.addStatement("json.put($S, object.$N)", fieldInfo.getSerializedName(), fieldInfo.getName());
                } else {
                    builder.beginControlFlow("if (object.$N != null)", fieldInfo.getName());
                    builder.addStatement("json.put($S, object.$N)", fieldInfo.getSerializedName(), fieldInfo.getName());

                    if (fieldInfo.isToJsonIncludeNull()) {
                        builder.nextControlFlow("else");
                        builder.addStatement("json.put($S, JSONObject.NULL)", fieldInfo.getSerializedName());
                        builder.endControlFlow();
                    } else {
                        builder.endControlFlow();
                    }
                }

            } else if (fieldInfo.getKind() == FieldInfo.Kind.TRANSFORMED) {
                String transformerName = fieldInfo.getName() + "Transformer";
                String transformedValueName = fieldInfo.getName() + "TransformedValue";
                builder.addStatement("$T $N = new $T()", fieldInfo.getTransformerName(), transformerName, fieldInfo.getTransformerName());
                builder.addStatement("$T $N = $N.inverseTransform(object.$N)", fieldInfo.getJsonFieldTypeName(), transformedValueName, transformerName, fieldInfo.getName());

                builder.beginControlFlow("if ($N != null)", transformedValueName);
                builder.addStatement("json.put($S, $N)", fieldInfo.getSerializedName(), transformedValueName);

                if (fieldInfo.isToJsonIncludeNull()) {
                    builder.nextControlFlow("else");
                    builder.addStatement("json.put($S, JSONObject.NULL)", fieldInfo.getSerializedName());
                    builder.endControlFlow();
                } else {
                    builder.endControlFlow();
                }

            } else if (fieldInfo.getKind() == FieldInfo.Kind.JSON_SERIALIZABLE) {
                builder.beginControlFlow("if (object.$N != null)", fieldInfo.getName());
                builder.addStatement("$T $N = $T.getInstance()", serializer, serializerName, serializer);
                builder.addStatement("json.put($S, $N.toJson(object.$N))", fieldInfo.getSerializedName(), serializerName, fieldInfo.getName());

                if (fieldInfo.isToJsonIncludeNull()) {
                    builder.nextControlFlow("else");
                    builder.addStatement("json.put($S, JSONObject.NULL)", fieldInfo.getSerializedName());
                    builder.endControlFlow();
                } else {
                    builder.endControlFlow();
                }

            } else if (fieldInfo.getKind() == FieldInfo.Kind.TO_ONE) {
                builder.beginControlFlow("if (object.$N.getTarget() != null)", fieldInfo.getName());
                builder.addStatement("$T $N = $T.getInstance()", serializer, serializerName, serializer);
                builder.addStatement("json.put($S, $N.toJson(object.$N.getTarget()))", fieldInfo.getSerializedName(), serializerName, fieldInfo.getName());

                if (fieldInfo.isToJsonIncludeNull()) {
                    builder.nextControlFlow("else");
                    builder.addStatement("json.put($S, JSONObject.NULL)", fieldInfo.getSerializedName());
                    builder.endControlFlow();
                } else {
                    builder.endControlFlow();
                }

            } else if (fieldInfo.getKind() == FieldInfo.Kind.TO_MANY) {
                builder.beginControlFlow("if (object.$N != null)", fieldInfo.getName());
                builder.addStatement("$T $N = $T.getInstance()", serializer, serializerName, serializer);
                builder.addStatement("json.put($S, $N.toJson(object.$N))", fieldInfo.getSerializedName(), serializerName, fieldInfo.getName());

                if (fieldInfo.isToJsonIncludeNull()) {
                    builder.nextControlFlow("else");
                    builder.addStatement("json.put($S, JSONObject.NULL)", fieldInfo.getSerializedName());
                    builder.endControlFlow();
                } else {
                    builder.endControlFlow();
                }
            }

            builder.addCode("\n");
        }

        if (classInfo.getTransformer() != null) {
            builder.addStatement("$T transformer = new $T()", classInfo.getTransformer(), classInfo.getTransformer());
            builder.addStatement("json = transformer.inverseTransform(json)");
        }

        builder.addStatement("return json")
                .endControlFlow()
                .beginControlFlow("catch($T ignored)", JSONException.class)
                .addStatement("return null")
                .endControlFlow();

        return builder.build();
    }

    // Helpers
    private ClassName getSerializerClassName() {
        return Utils.getSerializer(classInfo.getTypeElement());
    }

    private TypeName getEntityTypeName() {
        return classInfo.getType();
    }

    private TypeName getPrimaryKeyTypeName() {
        if (classInfo.hasPrimaryKey()) {
            return classInfo.getPrimaryKey().getTypeName();
        } else {
            return TypeName.get(Void.class);
        }
    }

    @Override
    protected String getPackageName() {
        return classInfo.getPackage();
    }
}
