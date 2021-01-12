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
package nl.aerius.wui.parser.convenience;

import java.util.LinkedHashMap;
import java.util.Map;

import nl.aerius.wui.parser.JsonMapInitializer;
import nl.aerius.wui.parser.JsonMapParser;

@JsonMapInitializer
public class LinkedHashMapInitializer implements JsonMapParser<LinkedHashMap<?, ?>> {
  @Override
  public LinkedHashMap<?, ?> create(final Map<?, ?> lst) {
    return (LinkedHashMap<?, ?>) lst;
  }
}
