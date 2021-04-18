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
package feign;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import feign.annotation.Param.Expander;
import feign.annotation.Experimental;

/**
 * 方法元数据
 */
public final class MethodMetadata implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 设置configKey
	 */
	private String configKey;
	/**
	 * 设置返回类型
	 */
	private transient Type returnType;
	/**
	 * 设置类型是URI的参数的位置编号
	 */
	private Integer urlIndex;
	/**
	 * 设置请求体参数的位置编号
	 */
	private Integer bodyIndex;
	private Integer headerMapIndex;
	private Integer queryMapIndex;
	private boolean queryMapEncoded;
	/**
	 * 请求体参数的类型
	 */
	private transient Type bodyType;
	/**
	 * 请求模板，用户可以自己设置
	 */
	private final RequestTemplate template = new RequestTemplate();
	/**
	 * form表单参数
	 */
	private final List<String> formParams = new ArrayList<>();
	/**
	 * 参数位置和参数每个注解的映射
	 * key是参数的位置
	 * value是每个直接的名称
	 */
	private final Map<Integer, Collection<String>> indexToName = new LinkedHashMap<>();
	private final Map<Integer, Class<? extends Expander>> indexToExpanderClass = new LinkedHashMap<>();
	private final Map<Integer, Boolean> indexToEncoded = new LinkedHashMap<>();
	private transient Map<Integer, Expander> indexToExpander;
	private BitSet parameterToIgnore = new BitSet();
	/**
	 * 是否忽略，可以中断当前方法元数据的解析过程
	 */
	private boolean ignored;
	/**
	 * 设置目标类型
	 */
	private transient Class<?> targetType;
	/**
	 * 设置方法
	 */
	private transient Method method;
	/**
	 * 警告信息
	 */
	private transient final List<String> warnings = new ArrayList<>();

	MethodMetadata() {
		template.methodMetadata(this);
	}

	/**
	 * Used as a reference to this method. For example, {@link Logger#log(String, String, Object...)
	 * logging} or {@link ReflectiveFeign reflective dispatch}.
	 *
	 * @see Feign#configKey(Class, java.lang.reflect.Method)
	 */
	public String configKey() {
		return configKey;
	}

	public MethodMetadata configKey(String configKey) {
		this.configKey = configKey;
		return this;
	}

	public Type returnType() {
		return returnType;
	}

	public MethodMetadata returnType(Type returnType) {
		this.returnType = returnType;
		return this;
	}

	public Integer urlIndex() {
		return urlIndex;
	}

	public MethodMetadata urlIndex(Integer urlIndex) {
		this.urlIndex = urlIndex;
		return this;
	}

	public Integer bodyIndex() {
		return bodyIndex;
	}

	public MethodMetadata bodyIndex(Integer bodyIndex) {
		this.bodyIndex = bodyIndex;
		return this;
	}

	public Integer headerMapIndex() {
		return headerMapIndex;
	}

	public MethodMetadata headerMapIndex(Integer headerMapIndex) {
		this.headerMapIndex = headerMapIndex;
		return this;
	}

	public Integer queryMapIndex() {
		return queryMapIndex;
	}

	public MethodMetadata queryMapIndex(Integer queryMapIndex) {
		this.queryMapIndex = queryMapIndex;
		return this;
	}

	public boolean queryMapEncoded() {
		return queryMapEncoded;
	}

	public MethodMetadata queryMapEncoded(boolean queryMapEncoded) {
		this.queryMapEncoded = queryMapEncoded;
		return this;
	}

	/**
	 * Type corresponding to {@link #bodyIndex()}.
	 */
	public Type bodyType() {
		return bodyType;
	}

	public MethodMetadata bodyType(Type bodyType) {
		this.bodyType = bodyType;
		return this;
	}

	public RequestTemplate template() {
		return template;
	}

	public List<String> formParams() {
		return formParams;
	}

	public Map<Integer, Collection<String>> indexToName() {
		return indexToName;
	}

	public Map<Integer, Boolean> indexToEncoded() {
		return indexToEncoded;
	}

	/**
	 * If {@link #indexToExpander} is null, classes here will be instantiated by newInstance.
	 */
	public Map<Integer, Class<? extends Expander>> indexToExpanderClass() {
		return indexToExpanderClass;
	}

	/**
	 * After {@link #indexToExpanderClass} is populated, this is set by contracts that support runtime
	 * injection.
	 */
	public MethodMetadata indexToExpander(Map<Integer, Expander> indexToExpander) {
		this.indexToExpander = indexToExpander;
		return this;
	}

	/**
	 * When not null, this value will be used instead of {@link #indexToExpander()}.
	 */
	public Map<Integer, Expander> indexToExpander() {
		return indexToExpander;
	}

	/**
	 * @param i individual parameter that should be ignored
	 * @return this instance
	 */
	public MethodMetadata ignoreParamater(int i) {
		this.parameterToIgnore.set(i);
		return this;
	}

	public BitSet parameterToIgnore() {
		return parameterToIgnore;
	}

	public MethodMetadata parameterToIgnore(BitSet parameterToIgnore) {
		this.parameterToIgnore = parameterToIgnore;
		return this;
	}

	/**
	 * @param i individual parameter to check if should be ignored
	 * @return true when field should not be processed by feign
	 */
	public boolean shouldIgnoreParamater(int i) {
		return parameterToIgnore.get(i);
	}

	/**
	 * @param index
	 * @return true if the parameter {@code index} was already consumed by a any
	 * {@link MethodMetadata} holder
	 */
	public boolean isAlreadyProcessed(Integer index) {
		return index.equals(urlIndex)
				|| index.equals(bodyIndex)
				|| index.equals(headerMapIndex)
				|| index.equals(queryMapIndex)
				|| indexToName.containsKey(index)
				|| indexToExpanderClass.containsKey(index)
				|| indexToEncoded.containsKey(index)
				|| (indexToExpander != null && indexToExpander.containsKey(index))
				|| parameterToIgnore.get(index);
	}

	public void ignoreMethod() {
		this.ignored = true;
	}

	public boolean isIgnored() {
		return ignored;
	}

	@Experimental
	public MethodMetadata targetType(Class<?> targetType) {
		this.targetType = targetType;
		return this;
	}

	@Experimental
	public Class<?> targetType() {
		return targetType;
	}

	@Experimental
	public MethodMetadata method(Method method) {
		this.method = method;
		return this;
	}

	@Experimental
	public Method method() {
		return method;
	}

	public void addWarning(String warning) {
		warnings.add(warning);
	}

	public String warnings() {
		return warnings.stream()
				.collect(Collectors.joining("\n- ", "\nWarnings:\n- ", ""));
	}

}
