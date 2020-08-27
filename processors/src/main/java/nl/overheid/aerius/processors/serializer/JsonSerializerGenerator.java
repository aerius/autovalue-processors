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
package nl.overheid.aerius.processors.serializer;

import java.io.IOException;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

import nl.overheid.aerius.processors.util.AutoValueGeneratorUtil;

/**
 * Generates a simple JSON parser util capable only of handling the naive case.
 */
public class JsonSerializerGenerator {
  private static final String JSON_SERIALIZER = "JsonSerializer";

  private final Filer filer;
  private final Types types;
  private final Elements elements;
  private final Messager messager;

  private final SourceVersion version;

  public JsonSerializerGenerator(final ProcessingEnvironment processingEnv) {
    filer = processingEnv.getFiler();
    messager = processingEnv.getMessager();
    types = processingEnv.getTypeUtils();
    elements = processingEnv.getElementUtils();
    version = processingEnv.getSourceVersion();
  }

  public void generate(final TypeElement type) {
    messager.printMessage(Kind.NOTE, "Generating serializer: " + type);

    final ClassName target = ClassName.bestGuess(type.getQualifiedName().toString() + JSON_SERIALIZER);

    // Create the type
    final Builder bldr = TypeSpec.classBuilder(target)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addAnnotation(JsonComponent.class);

    AutoValueGeneratorUtil.addGeneratorIfExists(elements, version, bldr, AutoValueJsonSerializerProcessor.class.getCanonicalName());

    bldr.superclass(ParameterizedTypeName.get(ClassName.get(StdSerializer.class), TypeName.get(type.asType())));

    bldr.addMethod(MethodSpec
        .constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addStatement("super($T.class)", type)
        .build());

    bldr.addMethod(MethodSpec
        .methodBuilder("serialize")
        .addAnnotation(Override.class)
        .addException(IOException.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addParameter(ParameterSpec
            .builder(TypeName.get(type.asType()), "value", Modifier.FINAL)
            .build())
        .addParameter(ParameterSpec
            .builder(JsonGenerator.class, "jgen", Modifier.FINAL)
            .build())
        .addParameter(ParameterSpec
            .builder(SerializerProvider.class, "provider", Modifier.FINAL)
            .build())
        .addCode(generatePerFieldSerializer(type))
        .build());

    // And generate our Java Class
    AutoValueGeneratorUtil.toJavaFile(filer, bldr, target, type);
  }

  private CodeBlock generatePerFieldSerializer(final TypeElement type) {
    final CodeBlock.Builder bldr = CodeBlock.builder();

    bldr.addStatement("jgen.writeStartObject()");

    ElementFilter.methodsIn(type.getEnclosedElements()).stream()
        .forEach(field -> addFieldSerializer(bldr, field));

    bldr.addStatement("jgen.writeEndObject()");

    return bldr.build();
  }

  private void addFieldSerializer(final CodeBlock.Builder bldr, final ExecutableElement method) {
    if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
      return;
    }

    final TypeMirror type = method.getReturnType();

    final String simpleType = AutoValueGeneratorUtil.getSimpleSerializerType(type);
    if (!simpleType.isEmpty()) {
      bldr.addStatement("jgen.write$NField($S, value.$N())", simpleType, method.getSimpleName(), method.getSimpleName());
    } else {
      addComplexField(bldr, type, method);
    }
  }

  private void addComplexField(final CodeBlock.Builder bldr, final TypeMirror type, final ExecutableElement field) {
    if (types.asElement(type).getKind() == ElementKind.ENUM) {
      bldr.addStatement("jgen.writeObjectField($S, value.$N().name().toLowerCase())", field.getSimpleName(), field.getSimpleName());
    } else {
      bldr.addStatement("jgen.writeObjectField($S, value.$N())", field.getSimpleName(), field.getSimpleName());
    }
  }
}
