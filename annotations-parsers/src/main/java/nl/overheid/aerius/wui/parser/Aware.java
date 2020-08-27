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
package nl.overheid.aerius.wui.parser;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation exists purely to guide the eclipse annotation environment to a class the processing environment needs to be aware of.
 * 
 * Processors may register to the Aware annotation and patch whatever class(es) are in it into its processing.
 * 
 * Some IDEs (cough Eclipse) include only a (very) limited set of elements in its RoundEnvironment while processing annotations. It is possible an
 * annotation processor depends on processing in a wider set of elements and will consequently not produce correct results. This solution alleviates
 * this problem by at least making the processor aware of it and allowing it to correct for it / adapt to it.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Aware {
  Class<?>[] value();
}
