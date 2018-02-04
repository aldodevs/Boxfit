package com.manuege.boxfit_processor.info;

import com.manuege.boxfit_processor.processor.Enviroment;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Created by Manu on 3/2/18.
 */

public class Utils {
    public static ClassName getSerializer(TypeElement element) {
        return ClassName.get(Enviroment.getEnvironment().getElementUtils().getPackageOf(element).toString(),  getSerializerName(element));
    }

    private static String getSerializerName(TypeElement element) {
        String fullName = element.getQualifiedName().toString();
        String pack = Enviroment.getEnvironment().getElementUtils().getPackageOf(element).toString();
        String elementName = fullName.substring(pack.length() + 1).replace(".", "$");
        return elementName + "Serializer";
    }

    public static TypeMirror getGenericType(final TypeMirror type, int index) {
        final TypeMirror[] result = { null };

        type.accept(new SimpleTypeVisitor6<Void, Void>() {
            @Override
            public Void visitDeclared(DeclaredType declaredType, Void v) {
                List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    result[0] = typeArguments.get(0);
                }
                return null;
            }
            @Override
            public Void visitPrimitive(PrimitiveType primitiveType, Void v) {
                return null;
            }
            @Override
            public Void visitArray(ArrayType arrayType, Void v) {
                return null;
            }
            @Override
            public Void visitTypeVariable(TypeVariable typeVariable, Void v) {
                return null;
            }
            @Override
            public Void visitError(ErrorType errorType, Void v) {
                return null;
            }
            @Override
            protected Void defaultAction(TypeMirror typeMirror, Void v) {
                throw new UnsupportedOperationException();
            }
        }, null);

        return result[index];
    }

    private static HashMap<Class, String> classesAndMethods;
    public static String getJsonGetterMethodName(TypeName className) {
        if (classesAndMethods == null) {
            classesAndMethods = new HashMap<>();
            classesAndMethods.put(String.class, "getString");
            classesAndMethods.put(Integer.class, "getInt");
            classesAndMethods.put(Boolean.class, "getBoolean");
            classesAndMethods.put(Long.class, "getLong");
            classesAndMethods.put(Double.class, "getDouble");
            classesAndMethods.put(JSONObject.class, "getJSONObject");
            classesAndMethods.put(JSONArray.class, "getJSONArray");
        }

        for (Class clazz: classesAndMethods.keySet()) {
            if (TypeName.get(clazz).equals(className)) {
                return classesAndMethods.get(clazz);
            }
        }

        return "get";
    }
}
