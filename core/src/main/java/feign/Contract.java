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

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import feign.Request.HttpMethod;
import feign.annotation.*;

/**
 * Defines what annotations and values are valid on interfaces.
 */
public interface Contract {

	/**
	 * Called to parse the methods in the class that are linked to HTTP requests.
	 * <p>
	 * 解析出每个方法的元数据，比如返回值，参数，请求头等
	 * todo 用户可以自定义，比如SpringMvcContract，用于解析springmvc的注解
	 *
	 * @param targetType {@link feign.Target#type() type} of the Feign interface.
	 */
	List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType);

	abstract class BaseContract implements Contract {

		/**
		 * @param targetType {@link feign.Target#type() type} of the Feign interface.
		 * @see #parseAndValidateMetadata(Class)
		 */
		@Override
		public List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
			checkState(targetType.getTypeParameters().length == 0, "Parameterized types unsupported: %s",
					targetType.getSimpleName());
			checkState(targetType.getInterfaces().length <= 1, "Only single inheritance supported: %s",
					targetType.getSimpleName());
			if (targetType.getInterfaces().length == 1) {
				checkState(targetType.getInterfaces()[0].getInterfaces().length == 0,
						"Only single-level inheritance supported: %s",
						targetType.getSimpleName());
			}

			final Map<String, MethodMetadata> result = new LinkedHashMap<>();
			//遍历这个类的每个方法
			for (final Method method : targetType.getMethods()) {
				//Object的方法、静态方法、默认方法，直接跳过
				if (method.getDeclaringClass() == Object.class
						|| (method.getModifiers() & Modifier.STATIC) != 0
						|| Util.isDefault(method)) {
					continue;
				}
				//解析出方法的元数据信息
				final MethodMetadata metadata = parseAndValidateMetadata(targetType, method);
				checkState(!result.containsKey(metadata.configKey()), "Overrides unsupported: %s", metadata.configKey());
				result.put(metadata.configKey(), metadata);
			}
			return new ArrayList<>(result.values());
		}

		/**
		 * @deprecated use {@link #parseAndValidateMetadata(Class, Method)} instead.
		 */
		@Deprecated
		public MethodMetadata parseAndValidateMetadata(Method method) {
			return parseAndValidateMetadata(method.getDeclaringClass(), method);
		}

		/**
		 * Called indirectly by {@link #parseAndValidateMetadata(Class)}.
		 * <p>
		 * 解析出方法的元数据信息
		 */
		protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
			final MethodMetadata data = new MethodMetadata();
			//设置目标类型
			data.targetType(targetType);
			//设置方法
			data.method(method);
			//设置返回类型
			data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));
			//设置configKey
			data.configKey(Feign.configKey(targetType, method));

			//处理类的注解
			//todo 这里只处理了该类型直接实现的接口，而且要求实现的接口数量只能是1个，这里有问题，多继承接口怎么处理？？？
			if (targetType.getInterfaces().length == 1) {
				processAnnotationOnClass(data, targetType.getInterfaces()[0]);
			}
			processAnnotationOnClass(data, targetType);

			//处理方法的注解
			for (final Annotation methodAnnotation : method.getAnnotations()) {
				processAnnotationOnMethod(data, methodAnnotation, method);
			}
			if (data.isIgnored()) {
				return data;
			}
			checkState(data.template().method() != null,
					"Method %s not annotated with HTTP method type (ex. GET, POST)%s",
					data.configKey(), data.warnings());
			final Class<?>[] parameterTypes = method.getParameterTypes();
			final Type[] genericParameterTypes = method.getGenericParameterTypes();

			//处理方法参数的注解
			final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
			final int count = parameterAnnotations.length;
			//遍历每个参数，parameterAnnotations[i]是每个参数的注解数组
			for (int i = 0; i < count; i++) {
				boolean isHttpAnnotation = false;
				if (parameterAnnotations[i] != null) {
					//解析当前参数的注解
					isHttpAnnotation = processAnnotationsOnParameter(data, parameterAnnotations[i], i);
				}

				//如果是http的注解，则直接忽略
				if (isHttpAnnotation) {
					data.ignoreParamater(i);
				}

				//当前参数类型是URI，设置urlIndex
				if (parameterTypes[i] == URI.class) {
					data.urlIndex(i);
				} else if (!isHttpAnnotation && parameterTypes[i] != Request.Options.class) {//不是http注解，而且当前参数类型不是Request.Options
					//todo 这里有点乱
					//当前位置已经配置到了MethodMetadata中
					if (data.isAlreadyProcessed(i)) {
						//form表单数据和body数据都不为空时，抛出异常
						checkState(data.formParams().isEmpty()
								|| data.bodyIndex() == null, "Body parameters cannot be used with form parameters.%s", data.warnings());
					} else {//当前位置没有配置到MethodMetadata中
						//form表单数据不为空时，抛出异常
						checkState(data.formParams().isEmpty(), "Body parameters cannot be used with form parameters.%s", data.warnings());
						//当前位置设置了bodyIndex，抛出异常
						checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s%s", method, data.warnings());
						data.bodyIndex(i);
						data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i]));
					}
				}
			}

			if (data.headerMapIndex() != null) {
				checkMapString("HeaderMap", parameterTypes[data.headerMapIndex()],
						genericParameterTypes[data.headerMapIndex()]);
			}

			if (data.queryMapIndex() != null) {
				if (Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()])) {
					checkMapKeys("QueryMap", genericParameterTypes[data.queryMapIndex()]);
				}
			}

			return data;
		}

		private static void checkMapString(String name, Class<?> type, Type genericType) {
			checkState(Map.class.isAssignableFrom(type),
					"%s parameter must be a Map: %s", name, type);
			checkMapKeys(name, genericType);
		}

		private static void checkMapKeys(String name, Type genericType) {
			Class<?> keyClass = null;

			// assume our type parameterized
			if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
				final Type[] parameterTypes = ((ParameterizedType) genericType).getActualTypeArguments();
				keyClass = (Class<?>) parameterTypes[0];
			} else if (genericType instanceof Class<?>) {
				// raw class, type parameters cannot be inferred directly, but we can scan any extended
				// interfaces looking for any explict types
				final Type[] interfaces = ((Class) genericType).getGenericInterfaces();
				if (interfaces != null) {
					for (final Type extended : interfaces) {
						if (ParameterizedType.class.isAssignableFrom(extended.getClass())) {
							// use the first extended interface we find.
							final Type[] parameterTypes = ((ParameterizedType) extended).getActualTypeArguments();
							keyClass = (Class<?>) parameterTypes[0];
							break;
						}
					}
				}
			}

			if (keyClass != null) {
				checkState(String.class.equals(keyClass),
						"%s key must be a String: %s", name, keyClass.getSimpleName());
			}
		}

		/**
		 * Called by parseAndValidateMetadata twice, first on the declaring class, then on the target
		 * type (unless they are the same).
		 * <p>
		 * todo 处理类的注解，用户自己可以实现自己的逻辑
		 *
		 * @param data metadata collected so far relating to the current java method.
		 * @param clz  the class to process
		 */
		protected abstract void processAnnotationOnClass(MethodMetadata data, Class<?> clz);

		/**
		 * todo 处理类的注解，用户自己可以实现自己的逻辑
		 *
		 * @param data       metadata collected so far relating to the current java method.
		 * @param annotation annotations present on the current method annotation.
		 * @param method     method currently being processed.
		 */
		protected abstract void processAnnotationOnMethod(MethodMetadata data,
		                                                  Annotation annotation,
		                                                  Method method);

		/**
		 * todo 处理方法参数的注解，用户自己可以实现自己的逻辑
		 *
		 * @param data        metadata collected so far relating to the current java method.
		 * @param annotations annotations present on the current parameter annotation.
		 * @param paramIndex  if you find a name in {@code annotations}, call
		 *                    {@link #nameParam(MethodMetadata, String, int)} with this as the last parameter.
		 * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an
		 * http-relevant annotation.
		 */
		protected abstract boolean processAnnotationsOnParameter(MethodMetadata data,
		                                                         Annotation[] annotations,
		                                                         int paramIndex);

		/**
		 * links a parameter name to its index in the method signature.
		 */
		protected void nameParam(MethodMetadata data, String name, int i) {
			final Collection<String> names = data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<>();
			names.add(name);
			data.indexToName().put(i, names);
		}
	}

	class Default extends DeclarativeContract {

		static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

		public Default() {
			super.registerClassAnnotation(Headers.class, (header, data) -> {
				final String[] headersOnType = header.value();
				checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.",
						data.configKey());
				final Map<String, Collection<String>> headers = toMap(headersOnType);
				headers.putAll(data.template().headers());
				data.template().headers(null); // to clear
				data.template().headers(headers);
			});
			super.registerMethodAnnotation(RequestLine.class, (ann, data) -> {
				final String requestLine = ann.value();
				checkState(emptyToNull(requestLine) != null,
						"RequestLine annotation was empty on method %s.", data.configKey());

				final Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
				if (!requestLineMatcher.find()) {
					throw new IllegalStateException(String.format(
							"RequestLine annotation didn't start with an HTTP verb on method %s",
							data.configKey()));
				} else {
					data.template().method(HttpMethod.valueOf(requestLineMatcher.group(1)));
					data.template().uri(requestLineMatcher.group(2));
				}
				data.template().decodeSlash(ann.decodeSlash());
				data.template()
						.collectionFormat(ann.collectionFormat());
			});
			super.registerMethodAnnotation(Body.class, (ann, data) -> {
				final String body = ann.value();
				checkState(emptyToNull(body) != null, "Body annotation was empty on method %s.",
						data.configKey());
				if (body.indexOf('{') == -1) {
					data.template().body(body);
				} else {
					data.template().bodyTemplate(body);
				}
			});
			super.registerMethodAnnotation(Headers.class, (header, data) -> {
				final String[] headersOnMethod = header.value();
				checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.",
						data.configKey());
				data.template().headers(toMap(headersOnMethod));
			});
			super.registerParameterAnnotation(Param.class, (paramAnnotation, data, paramIndex) -> {
				final String annotationName = paramAnnotation.value();
				final Parameter parameter = data.method().getParameters()[paramIndex];
				final String name;
				if (emptyToNull(annotationName) == null && parameter.isNamePresent()) {
					name = parameter.getName();
				} else {
					name = annotationName;
				}
				checkState(emptyToNull(name) != null, "Param annotation was empty on param %s.",
						paramIndex);
				nameParam(data, name, paramIndex);
				final Class<? extends Param.Expander> expander = paramAnnotation.expander();
				if (expander != Param.ToStringExpander.class) {
					data.indexToExpanderClass().put(paramIndex, expander);
				}
				if (!data.template().hasRequestVariable(name)) {
					data.formParams().add(name);
				}
			});
			super.registerParameterAnnotation(QueryMap.class, (queryMap, data, paramIndex) -> {
				checkState(data.queryMapIndex() == null,
						"QueryMap annotation was present on multiple parameters.");
				data.queryMapIndex(paramIndex);
				data.queryMapEncoded(queryMap.encoded());
			});
			super.registerParameterAnnotation(HeaderMap.class, (queryMap, data, paramIndex) -> {
				checkState(data.headerMapIndex() == null,
						"HeaderMap annotation was present on multiple parameters.");
				data.headerMapIndex(paramIndex);
			});
		}

		private static Map<String, Collection<String>> toMap(String[] input) {
			final Map<String, Collection<String>> result =
					new LinkedHashMap<String, Collection<String>>(input.length);
			for (final String header : input) {
				final int colon = header.indexOf(':');
				final String name = header.substring(0, colon);
				if (!result.containsKey(name)) {
					result.put(name, new ArrayList<String>(1));
				}
				result.get(name).add(header.substring(colon + 1).trim());
			}
			return result;
		}

	}
}
