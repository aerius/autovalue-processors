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
package nl.overheid.aerius.wui.parser.convenience;

import java.util.HashMap;
import java.util.Map;

import nl.overheid.aerius.wui.parser.JsonMapInitializer;
import nl.overheid.aerius.wui.parser.JsonMapParser;

@JsonMapInitializer
public class HashMapInitializer implements JsonMapParser<HashMap<?, ?>> {
  @Override
  public HashMap<?, ?> create(final Map<?, ?> lst) {
    return (HashMap<?, ?>) lst;
  }
}
