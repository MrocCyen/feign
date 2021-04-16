/**
 * Copyright 2012-2020 The Feign Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.template;

/**
 * URI Template Literal.
 * <p>
 * todo 文字规则
 * literals      =  %x21 / %x23-24 / %x26 / %x28-3B / %x3D / %x3F-5B
 * /  %x5D / %x5F / %x61-7A / %x7E / ucschar / iprivate
 * /  pct-encoded any Unicode character except: CTL, SP, DQUOTE, "'", "%" (aside from pct-encoded),"<", ">", "\", "^", "`", "{", "|", "}"
 */
public class Literal implements TemplateChunk {

	private final String value;

	/**
	 * Create a new Literal.
	 *
	 * @param value of the literal.
	 * @return the new Literal.
	 */
	public static Literal create(String value) {
		return new Literal(value);
	}

	/**
	 * Create a new Literal.
	 *
	 * @param value of the literal.
	 */
	Literal(String value) {
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException("a value is required.");
		}
		this.value = value;
	}

	@Override
	public String getValue() {
		return this.value;
	}
}
