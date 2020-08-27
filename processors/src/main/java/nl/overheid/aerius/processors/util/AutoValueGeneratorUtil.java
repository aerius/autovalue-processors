/*
 * Copyright the State of the Netherlands
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package nl.overheid.aerius.processors.util;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.processing.Filer;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import com.google.auto.value.AutoValue;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec.Builder;

import nl.overheid.aerius.wui.parser.Aware;
import nl.overheid.aerius.wui.parser.JsonParser;

public final class AutoValueGeneratorUtil {
  private AutoValueGeneratorUtil() {}

  public static void toJavaFile(final Filer filer, final Builder classBuilder, final ClassName className, final TypeElement originatingElement) {
    try {
      final JavaFile javaFile = JavaFile.builder(className.packageName(), classBuilder.build()).build();
      final JavaFileObject javaFileObject = filer.createSourceFile(className.reflectionName(), originatingElement);

      // System.out.println("Outing file: " + javaFile);

      try (final Writer writer = javaFileObject.openWriter()) {
        javaFile.writeTo(writer);
      }
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Get the simple type, a number, boolean, string, or integer for the given TypeMirror.
   */
  public static String getSimpleParserType(final TypeMirror typeMirror) {
    final TypeName typeName = TypeName.get(typeMirror);

    if (typeName.equals(TypeName.INT)
        || typeName.equals(TypeName.INT.box())
        || typeName.equals(TypeName.SHORT)
        || typeName.equals(TypeName.SHORT.box())) {
      return "Integer";
    } else if (typeName.equals(TypeName.LONG)
        || typeName.equals(TypeName.LONG.box())) {
      return "Long";
    } else if (typeName.equals(TypeName.BYTE)
        || typeName.equals(TypeName.BYTE.box())
        || typeName.equals(TypeName.FLOAT)
        || typeName.equals(TypeName.FLOAT.box())
        || typeName.equals(TypeName.DOUBLE)
        || typeName.equals(TypeName.DOUBLE.box())) {
      return "Number";
    } else if (typeName.equals(TypeName.BOOLEAN)) {
      return "Boolean";
    } else if (typeName.equals(TypeName.get(String.class)) || typeName.equals(TypeName.CHAR)) {
      return "String";
    } else {
      // Either complex object or collection
      return "";
    }
  }

  /**
   * Get the simple type, a number, boolean, or string, for the given TypeMirror.
   */
  public static String getSimpleSerializerType(final TypeMirror typeMirror) {
    final TypeName typeName = TypeName.get(typeMirror);

    if (typeName.equals(TypeName.INT)
        || typeName.equals(TypeName.INT.box())
        || typeName.equals(TypeName.SHORT)
        || typeName.equals(TypeName.SHORT.box())
        || typeName.equals(TypeName.BYTE.box())
        || typeName.equals(TypeName.LONG)
        || typeName.equals(TypeName.LONG.box())
        || typeName.equals(TypeName.FLOAT)
        || typeName.equals(TypeName.FLOAT.box())
        || typeName.equals(TypeName.DOUBLE)
        || typeName.equals(TypeName.DOUBLE.box())) {
      return "Number";
    } else if (typeName.equals(TypeName.BOOLEAN)) {
      return "Boolean";
    } else if (typeName.equals(TypeName.get(String.class)) || typeName.equals(TypeName.CHAR)) {
      return "String";
    } else {
      // Either complex object or collection
      return "";
    }
  }

  public static List<? extends TypeMirror> getJsonParsers(final Aware annotation) {
    try {
      annotation.value(); // this should throw
    } catch (final MirroredTypesException mte) {
      return mte.getTypeMirrors();
    }

    throw new RuntimeException("Cannot access TypeMirror");
  }

  public static TypeMirror getTypeMirror(final JsonParser annotation) {
    try {
      annotation.value(); // this should throw
    } catch (final MirroredTypeException mte) {
      return mte.getTypeMirror();
    }

    throw new RuntimeException("Cannot access TypeMirror");
  }

  public static boolean isAutoValue(final Types types, final TypeMirror type) {
    return types.asElement(type).getAnnotationsByType(AutoValue.class).length > 0;
  }

  public static boolean isEnum(final Types typeUtils, final TypeMirror type) {
    return typeUtils.asElement(type).getKind() == ElementKind.ENUM;
  }

  public static boolean isList(final Types types, final Elements elements, final TypeMirror typeMirror) {
    final TypeMirror listType = elements.getTypeElement(List.class.getCanonicalName()).asType();
    final TypeMirror erasedListType = types.erasure(listType);

    return types.isAssignable(typeMirror, erasedListType);
  }

  public static boolean isMap(final Types types, final Elements elements, final TypeMirror typeMirror) {
    final TypeMirror mapType = elements.getTypeElement(Map.class.getCanonicalName()).asType();
    final TypeMirror erasedMapType = types.erasure(mapType);

    return types.isAssignable(typeMirror, erasedMapType);
  }

  public static void addGeneratorIfExists(final Elements elements, final SourceVersion sourceVersion, final Builder bldr,
      final String canonicalName) {
    generatedAnnotation(elements, sourceVersion).ifPresent(chosen -> bldr.addAnnotation(AnnotationSpec
        .builder(ClassName.get(chosen))
        .addMember("value", "$S", canonicalName)
        .build()));
  }

  public static Optional<TypeElement> generatedAnnotation(final Elements elements, final SourceVersion sourceVersion) {
    return Optional.ofNullable(elements.getTypeElement(sourceVersion.compareTo(SourceVersion.RELEASE_8) > 0
        ? "javax.annotation.processing.Generated"
        : "javax.annotation.Generated"));
  }
}
