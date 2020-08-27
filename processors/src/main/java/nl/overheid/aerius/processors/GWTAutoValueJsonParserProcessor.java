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
package nl.overheid.aerius.processors;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;

import nl.overheid.aerius.processors.parser.JsonParserGeneratorStep;

@AutoService(Processor.class)
@SupportedAnnotationTypes(value = {
    "com.google.auto.value.AutoValue",
    "io.yogh.wui.parser.JsonParser",
    "io.yogh.wui.parser.JsonListInitializer",
    "io.yogh.wui.parser.Aware"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GWTAutoValueJsonParserProcessor extends BasicAnnotationProcessor {
  private JsonParserGeneratorStep jsonParserGeneratorStep;

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    if (jsonParserGeneratorStep == null) {
      jsonParserGeneratorStep = new JsonParserGeneratorStep(processingEnv);
    }

    return ImmutableSet.of(jsonParserGeneratorStep);
  }
}
