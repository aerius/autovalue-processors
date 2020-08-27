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
package nl.overheid.aerius.processors.parser;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import nl.overheid.aerius.processors.GWTAutoValueJsonParserProcessor;
import nl.overheid.aerius.processors.util.AutoValueGeneratorUtil;
import nl.overheid.aerius.wui.future.JsonParserCallback;
import nl.overheid.aerius.wui.future.JsonRequestCallback;
import nl.overheid.aerius.wui.parser.Aware;
import nl.overheid.aerius.wui.parser.JsonListInitializer;
import nl.overheid.aerius.wui.parser.JsonListParser;
import nl.overheid.aerius.wui.parser.JsonMapInitializer;
import nl.overheid.aerius.wui.parser.JsonMapParser;
import nl.overheid.aerius.wui.parser.JsonParser;
import nl.overheid.aerius.wui.parser.convenience.ArrayListInitializer;
import nl.overheid.aerius.wui.parser.convenience.DefaultListInitializer;
import nl.overheid.aerius.wui.parser.convenience.HashMapInitializer;
import nl.overheid.aerius.wui.parser.convenience.ImmutableListInitializer;
import nl.overheid.aerius.wui.service.ForwardedAsyncCallback;
import nl.overheid.aerius.wui.service.json.JSONObjectHandle;

/**
 * Generates a simple JSON parser util capable only of handling the naive case.
 */
public class JsonParserGeneratorStep implements ProcessingStep {
  private static final String JSON_PARSER = "JsonParser";

  private final Filer filer;

  private final Map<String, TypeElement> customIndiscriminateParsers = new HashMap<>();
  private final Map<TypeElement, Map<String, TypeElement>> customSpecificParsers = new HashMap<>();

  private final Map<String, TypeElement> listCreators = new HashMap<>();
  private final Map<String, TypeElement> mapCreators = new HashMap<>();

  private TypeElement process;
  private final Types types;
  private final Elements elements;
  private final Messager messager;

  private final SourceVersion version;

  private HashMap<Element, Exception> previousRoundDeferrals;

  public JsonParserGeneratorStep(final ProcessingEnvironment processingEnv) {
    filer = processingEnv.getFiler();
    messager = processingEnv.getMessager();
    types = processingEnv.getTypeUtils();
    elements = processingEnv.getElementUtils();
    version = processingEnv.getSourceVersion();

    initializeStandardListCreators();
    initializeStandardMapCreators();
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(AutoValue.class, JsonParser.class, JsonListInitializer.class, JsonMapInitializer.class, Aware.class);
  }

  @Override
  public Set<? extends Element> process(final SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    final HashMap<Element, Exception> deferred = new HashMap<>();

    // Handle awareness first because we may only be granted one round
    elementsByAnnotation.get(Aware.class)
        .forEach(this::handleAwareness);

    elementsByAnnotation.get(AutoValue.class)
        .forEach(v -> collectDeferred(v, this::handleAutoValue, deferred::put));

    elementsByAnnotation.get(JsonParser.class)
        .forEach(v -> collectDeferred(v, this::handleJsonParser, deferred::put));

    elementsByAnnotation.get(JsonListInitializer.class)
        .forEach(v -> collectDeferred(v, this::handleJsonListInitializer, deferred::put));

    elementsByAnnotation.get(JsonMapInitializer.class)
        .forEach(v -> collectDeferred(v, this::handleJsonMapInitializer, deferred::put));

    return updateDeferrals(deferred);
  }

  private Set<Element> updateDeferrals(final HashMap<Element, Exception> deferred) {
    final HashMap<Element, Exception> finalDefer = new HashMap<>(deferred);
    if (previousRoundDeferrals != null) {
      finalDefer.keySet().removeAll(previousRoundDeferrals.keySet());
    }

    if (previousRoundDeferrals != null) {
      final HashMap<Element, Exception> intersections = new HashMap<>(previousRoundDeferrals);
      intersections.keySet().retainAll(deferred.keySet());

      intersections.entrySet().forEach(e -> messager.printMessage(Kind.ERROR, e.getValue().getMessage(), e.getKey()));
    }

    previousRoundDeferrals = finalDefer;

    return finalDefer.keySet();
  }

  private void collectDeferred(final Element element, final Consumer<Element> consumer, final BiConsumer<Element, Exception> defer) {
    try {
      consumer.accept(element);
    } catch (final Exception e) {
      // Don't print an error because this isn't an error
      messager.printMessage(Kind.NOTE, "Deferring processing of element " + element + " because " + e);
      defer.accept(element, e);
    }
  }

  private void handleAwareness(final Element elem) {
    final Aware aware = elem.getAnnotation(Aware.class);

    AutoValueGeneratorUtil.getJsonParsers(aware).forEach(v -> {
      final Element jsonParser = types.asElement(v);

      handleJsonParser(jsonParser);
    });
  }

  private void handleJsonParser(final Element parser) {
    messager.printMessage(Kind.NOTE, "Generating JsonParser: " + parser);

    final JsonParser ann = parser.getAnnotation(JsonParser.class);
    if (ann == null) {
      messager.printMessage(Kind.WARNING, "Made aware of a JsonParser while it is not a JsonParser: " + parser);
      return;
    }

    final String parsedType = ((TypeElement) types.asElement(AutoValueGeneratorUtil.getTypeMirror(ann))).getQualifiedName().toString();
    try {
      // Doing this leads to the catch.... (oh lordy)
      ann.target();
    } catch (final MirroredTypesException e) {
      if (e.getTypeMirrors().isEmpty()) {
        customIndiscriminateParsers.put(parsedType, (TypeElement) parser);
      } else {
        e.getTypeMirrors().forEach(target -> {
          final TypeElement element = (TypeElement) types.asElement(target);
          customSpecificParsers.merge(element, new HashMap<>(), (a, b) -> {
            b.put(parsedType, (TypeElement) parser);
            return b;
          });
        });
      }

      // Squelch
    }
  }

  private void initializeStandardListCreators() {
    listCreators.put(ArrayList.class.getCanonicalName(),
        elements.getTypeElement(ArrayListInitializer.class.getCanonicalName()));
    listCreators.put(List.class.getCanonicalName(),
        elements.getTypeElement(DefaultListInitializer.class.getCanonicalName()));
    listCreators.put(ImmutableList.class.getCanonicalName(),
        elements.getTypeElement(ImmutableListInitializer.class.getCanonicalName()));
  }

  private void initializeStandardMapCreators() {
    mapCreators.put(HashMap.class.getCanonicalName(),
        elements.getTypeElement(HashMapInitializer.class.getCanonicalName()));
  }

  private void handleJsonMapInitializer(final Element creator) {
    messager.printMessage(Kind.NOTE, "Generating MapInitializer: " + creator);

    final TypeMirror jsonMapCreatorType = elements.getTypeElement(JsonMapParser.class.getCanonicalName()).asType();
    final TypeMirror erasedMapCreatorType = types.erasure(jsonMapCreatorType);

    final Optional<DeclaredType> creatorTarget = types.directSupertypes(creator.asType())
        .stream()
        .filter(v -> types.erasure(v).equals(erasedMapCreatorType))
        .map(v -> (DeclaredType) v)
        .findAny();

    creatorTarget.ifPresent(initializer -> {
      final TypeElement type = (TypeElement) types.asElement(types.erasure(initializer.getTypeArguments().iterator().next()));
      mapCreators.put(type.getQualifiedName().toString(), (TypeElement) creator);
    });
  }

  private void handleJsonListInitializer(final Element creator) {
    messager.printMessage(Kind.NOTE, "Generating ListInitializer: " + creator);

    final TypeMirror jsonListCreatorType = elements.getTypeElement(JsonListParser.class.getCanonicalName()).asType();
    final TypeMirror erasedListCreatorType = types.erasure(jsonListCreatorType);

    final Optional<DeclaredType> creatorTarget = types.directSupertypes(creator.asType())
        .stream()
        .filter(v -> types.erasure(v).equals(erasedListCreatorType))
        .map(v -> (DeclaredType) v)
        .findAny();

    creatorTarget.ifPresent(initializer -> {
      final TypeElement type = (TypeElement) types.asElement(types.erasure(initializer.getTypeArguments().iterator().next()));
      listCreators.put(type.getQualifiedName().toString(), (TypeElement) creator);
    });
  }

  public void handleAutoValue(final Element element) {
    final TypeMirror serializable = elements.getTypeElement(Serializable.class.getCanonicalName()).asType();
    if (!types.isAssignable(element.asType(), serializable)) {
      return;
    }

    final TypeElement type = (TypeElement) element;
    messager.printMessage(Kind.NOTE, "Generating parser: " + type);

    this.process = type;
    final ClassName target = ClassName.bestGuess(type.getQualifiedName().toString() + JSON_PARSER);

    // Create the type
    final TypeSpec.Builder bldr = TypeSpec.classBuilder(target)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    AutoValueGeneratorUtil.addGeneratorIfExists(elements, version, bldr, GWTAutoValueJsonParserProcessor.class.getCanonicalName());

    // Add Legacy Callback inner class
    bldr.addType(TypeSpec
        .classBuilder("LegacyCallback")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .superclass(ParameterizedTypeName.get(ClassName.get(JsonParserCallback.class), TypeName.get(type.asType())))
        .addMethod(MethodSpec
            .constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(ClassName.get(AsyncCallback.class), TypeName.get(type.asType())), "callback", Modifier.FINAL)
            .addStatement("super(callback)")
            .build())
        .addMethod(MethodSpec
            .methodBuilder("parse")
            .addAnnotation(Override.class)
            .returns(TypeName.get(type.asType()))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JSONValue.class, "result", Modifier.FINAL)
            .addStatement("return $T.parse(result)", target)
            .build())
        .build());

    // Add Elemental Callback inner class
    bldr.addType(TypeSpec
        .classBuilder("ElementalCallback")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .superclass(ParameterizedTypeName.get(ClassName.get(ForwardedAsyncCallback.class), TypeName.get(type.asType()), TypeName.get(String.class)))
        .addMethod(MethodSpec
            .constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(ClassName.get(AsyncCallback.class), TypeName.get(type.asType())), "callback", Modifier.FINAL)
            .addStatement("super(callback)")
            .build())
        .addMethod(MethodSpec
            .methodBuilder("convert")
            .addAnnotation(Override.class)
            .returns(TypeName.get(type.asType()))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(String.class, "content", Modifier.FINAL)
            .addStatement("return $T.parse(content)", target)
            .build())
        .build());

    // Add a private constructor
    bldr.addMethod(MethodSpec
        .constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .build());

    bldr.addMethod(MethodSpec
        .methodBuilder("parse")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .returns(TypeName.get(type.asType()))
        .addParameter(ParameterSpec
            .builder(String.class, "text", Modifier.FINAL)
            .build())
        .addStatement("return parse($1T.fromText(text))", JSONObjectHandle.class)
        .build());

    bldr.addMethod(MethodSpec
        .methodBuilder("parse")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .returns(TypeName.get(type.asType()))
        .addParameter(ParameterSpec
            .builder(JSONValue.class, "json", Modifier.FINAL)
            .build())
        .addStatement("return parse($1T.fromJson(json))", JSONObjectHandle.class)
        .build());

    bldr.addMethod(MethodSpec
        .methodBuilder("parse")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .returns(TypeName.get(type.asType()))
        .addParameter(ParameterSpec
            .builder(JSONObjectHandle.class, "result", Modifier.FINAL)
            .build())
        .addCode(generatePerFieldParser(type))
        .build());

    bldr.addMethod(MethodSpec
        .methodBuilder("wrap")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .returns(ParameterizedTypeName.get(AsyncCallback.class, String.class))
        .addParameter(ParameterSpec
            .builder(ParameterizedTypeName.get(ClassName.get(AsyncCallback.class), TypeName.get(type.asType())), "callback", Modifier.FINAL)
            .build())
        .addStatement("return new ElementalCallback(callback)", ParameterizedTypeName.get(AsyncCallback.class, String.class))
        .build());

    bldr.addMethod(MethodSpec
        .methodBuilder("wrapLegacy")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .returns(RequestCallback.class)
        .addParameter(ParameterSpec
            .builder(ParameterizedTypeName.get(ClassName.get(AsyncCallback.class), TypeName.get(type.asType())), "callback", Modifier.FINAL)
            .build())
        .addStatement("return $1T.create(new LegacyCallback(callback))", JsonRequestCallback.class)
        .build());

    // And generate our Java Class
    AutoValueGeneratorUtil.toJavaFile(filer, bldr, target, type);
  }

  private CodeBlock generatePerFieldParser(final TypeElement type) {
    final CodeBlock.Builder bldr = CodeBlock.builder()
        .addStatement("$1T.Builder bldr = $1T.builder()", type);

    ElementFilter.methodsIn(type.getEnclosedElements()).stream()
        .forEach(field -> addField(bldr, field));

    return bldr.addStatement("return bldr.build()")
        .build();
  }

  private void addField(final CodeBlock.Builder bldr, final ExecutableElement method) {
    if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
      return;
    }

    if (method.getAnnotationsByType(Nullable.class).length > 0) {
      handleNullable(bldr, method);
    } else {
      handleMethod(bldr, method);
    }
  }

  private void handleMethod(final CodeBlock.Builder bldr, final ExecutableElement method) {
    final String type = AutoValueGeneratorUtil.getSimpleParserType(method.getReturnType());

    switch (type) {
    case "Boolean":
    case "Integer":
    case "Long":
    case "Number":
    case "String":
      bldr.addStatement("bldr.$L(result.get" + type + "($S))", method.getSimpleName(), method.getSimpleName());
      break;
    default:
      handleComplexType(bldr, method);
      break;
    }
  }

  private void handleComplexType(final CodeBlock.Builder bldr, final ExecutableElement method) {
    if (AutoValueGeneratorUtil.isList(types, elements, method.getReturnType())) {
      handleListType(bldr, method);
    } else if (AutoValueGeneratorUtil.isEnum(types, method.getReturnType())) {
      handleEnum(bldr, method);
    } else if (AutoValueGeneratorUtil.isAutoValue(types, method.getReturnType())) {
      handleAutoValueType(bldr, method);
    } else if (AutoValueGeneratorUtil.isMap(types, elements, method.getReturnType())) {
      handleMapType(bldr, method);
    } else {
      final TypeElement complexType = (TypeElement) types.asElement(method.getReturnType());
      final String name = complexType.getQualifiedName().toString();

      if (customSpecificParsers.containsKey(complexType)) {
        bldr.addStatement("bldr.$L($T.parse(result.getObject($S)))", method.getSimpleName(), customSpecificParsers.get(complexType).get(name),
            method.getSimpleName());
      } else if (customIndiscriminateParsers.containsKey(name)) {
        bldr.addStatement("bldr.$L($T.parse(result.getObject($S)))", method.getSimpleName(), customIndiscriminateParsers.get(name),
            method.getSimpleName());
      } else {
        bldr.add("// JsonParserGenerator could not find a parser for this object: $T\n", method.getReturnType());
        throw new IllegalStateException(
            "Could not find JsonParser for type: " + method.getReturnType() + ". Please implement a @JsonParser parsing this type.");
      }
    }
  }

  private void handleMapType(final CodeBlock.Builder bldr, final ExecutableElement method) {
    final String mapTypeName = ((TypeElement) types.asElement(types.erasure(method.getReturnType()))).getQualifiedName().toString();

    final DeclaredType findMapType = findMapType(method.getReturnType());

    final TypeElement mapCreator = mapCreators.get(mapTypeName);
    if (mapCreator == null) {
      throw new IllegalStateException("Only HashMap is a supported map type for the moment.");
//      throw new IllegalStateException(
//          "Encountered map type without an initializer: " + method.getReturnType() + ". Please implement a @JsonMapInitializer parsing this type.");
    }

    final Iterator<? extends TypeMirror> it = findMapType.getTypeArguments().iterator();
    final TypeMirror key = it.next();
    final TypeMirror value = it.next();

    final String keyType = AutoValueGeneratorUtil.getSimpleParserType(key);
    final String valueType = AutoValueGeneratorUtil.getSimpleParserType(value);

    final String name = method.getSimpleName() + "Map";
    bldr.addStatement("final $T<$T, $T> $L = new $T<>()", HashMap.class, key, value, name, HashMap.class);
    bldr.addStatement("$T $LObj = result.getObject($S)", JSONObjectHandle.class, method.getSimpleName(), method.getSimpleName());
    bldr.add("$LObj.keySet().stream()\n", method.getSimpleName());
    bldr.indent();

    switch (keyType) {
    case "Boolean":
      bldr.add(".map(Boolean::parseBoolean)\n");
      break;
    case "Integer":
      bldr.add(".map(Integer::parseInt)\n");
      break;
    case "Long":
      bldr.add(".map(Long::parseLong)\n");
      break;
    case "Number":
      bldr.add(".map(Double::parseDouble)\n");
      break;
    case "String":
    default:
      // Do nothing
      break;
    }

    bldr.addStatement(".forEach(v -> $LMap.put(v, $LObj.get$L(String.valueOf(v))))", method.getSimpleName(), method.getSimpleName(), valueType);
    bldr.unindent();

    bldr.addStatement("bldr.$L($L)", method.getSimpleName(), name);
  }

  private void handleEnum(final CodeBlock.Builder bldr, final ExecutableElement method) {
    bldr.addStatement("bldr.$N($T.valueOf(result.getString($S).toUpperCase()))", method.getSimpleName(), method.getReturnType(),
        method.getSimpleName());
  }

  private void handleAutoValueType(final CodeBlock.Builder bldr, final ExecutableElement method) {
    // TODO
    final ClassName expectedParser = ClassName.bestGuess(method.getReturnType().toString() + JSON_PARSER);

    bldr.addStatement("bldr.$L($T.parse(result.getObject($S)))", method.getSimpleName(), expectedParser, method.getSimpleName());
  }

  private void handleListType(final CodeBlock.Builder bldr, final ExecutableElement method) {
    final String listTypeName = ((TypeElement) types.asElement(types.erasure(method.getReturnType()))).getQualifiedName().toString();
    final TypeElement listCreator = listCreators.get(listTypeName);
    if (listCreator == null) {
      throw new IllegalStateException(
          "Encountered list type without an initializer: " + method.getReturnType() + ". Please implement a @JsonListInitializer parsing this type.");
    }

    final DeclaredType listType = findListType(method.getReturnType());
    if (listType == null) {
      throw new RuntimeException("The impossible happened; list type of: " + method.getReturnType() + " could not be found.");
    }

    final TypeMirror inner = listType.getTypeArguments().iterator().next();

    final String name = method.getSimpleName() + "List";
    bldr.addStatement("final $T<$T> $L = new $T<>()", List.class, inner, name, ArrayList.class);

    final String simpleType = AutoValueGeneratorUtil.getSimpleParserType(inner);
    switch (simpleType) {
    case "Boolean":
    case "Integer":
    case "Long":
    case "Number":
    case "String":
      bldr.addStatement("result.get$NArray($S).forEach($L::add)", simpleType, method.getSimpleName(), name);
      break;
    default:
      bldr.addStatement("result.getArray($S).forEach(json -> $L.add($L))", method.getSimpleName(), name, generateComplexTypeParser(inner));
    }

    bldr.addStatement("bldr.$N(($T) new $T().create($L))", method.getSimpleName(), method.getReturnType(), listCreator, name);
  }

  private CodeBlock generateComplexTypeParser(final TypeMirror type) {
    final CodeBlock.Builder bldr = CodeBlock.builder();

    final String name = ((TypeElement) types.asElement(type)).getQualifiedName().toString();

    if (AutoValueGeneratorUtil.isAutoValue(types, type)) {
      final ClassName parser = ClassName.bestGuess(name + JSON_PARSER);
      bldr.add("$T.parse(json)", parser);
    } else if (AutoValueGeneratorUtil.isEnum(types, type)) {
      bldr.add("$T.valueOf(json.asString().toUpperCase())");
    } else {
      if (customSpecificParsers.containsKey(process)) {
        bldr.add("$T.parse(json)", customSpecificParsers.get(process).get(name));
      } else if (customIndiscriminateParsers.containsKey(name)) {
        bldr.add("$T.parse(json)", customIndiscriminateParsers.get(name));
      } else {
        throw new IllegalStateException(
            "Could not find JsonParser for type: " + type + ". Please implement a @JsonParser parsing this type.");
      }
    }

    return bldr.build();
  }

  private DeclaredType findMapType(final TypeMirror type) {
    final TypeMirror mapType = elements.getTypeElement(Map.class.getCanonicalName()).asType();
    final TypeMirror erasedMapType = types.erasure(mapType);

    return findDeclaredType(type, erasedMapType);
  }

  private DeclaredType findListType(final TypeMirror type) {
    final TypeMirror listType = elements.getTypeElement(List.class.getCanonicalName()).asType();
    final TypeMirror erasedListType = types.erasure(listType);

    System.out.println("List type: " + listType);
    System.out.println("Erased list type: " + erasedListType);

    return findDeclaredType(type, erasedListType);
  }

  private void handleNullable(final CodeBlock.Builder bldr, final ExecutableElement method) {
    final String type = AutoValueGeneratorUtil.getSimpleParserType(method.getReturnType());
    if (!type.isEmpty()) {
      bldr.addStatement("result.get" + type + "Optional($S).ifPresent(bldr::$L)", method.getSimpleName(), method.getSimpleName());
    } else {

      bldr.beginControlFlow("if (result.has($S) && !result.isNull($S))", method.getSimpleName(), method.getSimpleName());
      handleComplexType(bldr, method);
      bldr.endControlFlow();
    }
  }

  private DeclaredType findDeclaredType(final TypeMirror type, final TypeMirror erasedListType) {
    if (types.erasure(type).equals(erasedListType)) {
      return (DeclaredType) type;
    }
    
    final List<? extends TypeMirror> directSuperTypes = types.directSupertypes(type);
    if (directSuperTypes.isEmpty()) {
      return null;
    }

    final Optional<DeclaredType> found = directSuperTypes.stream()
        .filter(v -> types.erasure(v).equals(erasedListType))
        .filter(v -> v.getKind() == TypeKind.DECLARED)
        .map(v -> (DeclaredType) v)
        .findAny();

    if (found.isPresent()) {
      return found.get();
    } else {
      for (int i = 0; i < directSuperTypes.size(); i++) {
        final DeclaredType solution = findDeclaredType(directSuperTypes.get(i), erasedListType);
        if (solution != null) {
          return solution;
        }
      }

      return null;
    }
  }
}
