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

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
    "com.google.auto.value.AutoValue"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AutoValueJsonSerializerProcessor extends AbstractProcessor {
  @Override
  public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    final JsonSerializerGenerator generator = new JsonSerializerGenerator(processingEnv);

    final Types typeUtils = processingEnv.getTypeUtils();

    final TypeMirror serializable = processingEnv.getElementUtils().getTypeElement(Serializable.class.getCanonicalName()).asType();

    final Set<TypeElement> autoValueTypes = ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(AutoValue.class))
        .stream()
        .filter(v -> typeUtils.isAssignable(v.asType(), serializable))
        .collect(Collectors.toSet());

    autoValueTypes.stream()
        .forEach(generator::generate);

    // Don't claim, we're only generating utils.
    return false;
  }
}
