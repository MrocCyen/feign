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

import feign.Logger.NoOpLogger;
import feign.ReflectiveFeign.ParseHandlersByName;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.querymap.FieldQueryMapEncoder;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static feign.ExceptionPropagationPolicy.NONE;

/**
 * Feign's purpose is to ease development against http apis that feign restfulness. <br>
 * In implementation, Feign is a {@link Feign#newInstance factory} for generating {@link Target
 * targeted} http apis.
 */
public abstract class Feign {

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Configuration keys are formatted as unresolved <a href=
	 * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html" >see
	 * tags</a>. This method exposes that format, in case you need to create the same value as
	 * {@link MethodMetadata#configKey()} for correlation purposes.
	 *
	 * <p>
	 * Here are some sample encodings:
	 *
	 * <pre>
	 * <ul>
	 *   <li>{@code Route53}: would match a class {@code route53.Route53}</li>
	 *   <li>{@code Route53#list()}: would match a method {@code route53.Route53#list()}</li>
	 *   <li>{@code Route53#listAt(Marker)}: would match a method {@code route53.Route53#listAt(Marker)}</li>
	 *   <li>{@code Route53#listByNameAndType(String, String)}: would match a method {@code route53.Route53#listAt(String, String)}</li>
	 * </ul>
	 * </pre>
	 * <p>
	 * Note that there is no whitespace expected in a key!
	 *
	 * @param targetType {@link feign.Target#type() type} of the Feign interface.
	 * @param method     invoked method, present on {@code type} or its super.
	 * @see MethodMetadata#configKey()
	 * <p>
	 * 创建feign客户端每个方法的唯一表示key
	 */
	public static String configKey(Class targetType, Method method) {
		StringBuilder builder = new StringBuilder();
		builder.append(targetType.getSimpleName());
		builder.append('#').append(method.getName()).append('(');
		for (Type param : method.getGenericParameterTypes()) {
			param = Types.resolve(targetType, targetType, param);
			builder.append(Types.getRawType(param).getSimpleName()).append(',');
		}
		if (method.getParameterTypes().length > 0) {
			builder.deleteCharAt(builder.length() - 1);
		}
		return builder.append(')').toString();
	}

	/**
	 * @deprecated use {@link #configKey(Class, Method)} instead.
	 */
	@Deprecated
	public static String configKey(Method method) {
		return configKey(method.getDeclaringClass(), method);
	}

	/**
	 * Returns a new instance of an HTTP API, defined by annotations in the {@link Feign Contract},
	 * for the specified {@code target}. You should cache this result.
	 * <p>
	 * todo 这个是产生出feign客户端实例的方法，实例是一个jdk代理对象
	 */
	public abstract <T> T newInstance(Target<T> target);

	/**
	 * todo feign客户端对象构建器，这里是使用的建造器模式
	 */
	public static class Builder {
		/**
		 * 请求拦截器，请求在请求时执行
		 */
		private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
		/**
		 * feign日志等级
		 */
		private Logger.Level logLevel = Logger.Level.NONE;
		/**
		 * 用于获取feign客户端每个方法的元数据，默认获取feign定义的注解做标记的方法和类
		 */
		private Contract contract = new Contract.Default();
		/**
		 * 执行具体的http请求
		 */
		private Client client = new Client.Default(null, null);
		/**
		 * 重试
		 */
		private Retryer retryer = new Retryer.Default();
		/**
		 * 日志
		 */
		private Logger logger = new NoOpLogger();
		/**
		 * 编码器
		 */
		private Encoder encoder = new Encoder.Default();
		/**
		 * 解码器
		 */
		private Decoder decoder = new Decoder.Default();
		/**
		 * 查询参数编码器
		 */
		private QueryMapEncoder queryMapEncoder = new FieldQueryMapEncoder();
		/**
		 * 错误异常解码器
		 */
		private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
		/**
		 * 所有请求的共有参数设置
		 */
		private Options options = new Options();
		/**
		 * InvocationHandler工厂
		 */
		private InvocationHandlerFactory invocationHandlerFactory = new InvocationHandlerFactory.Default();
		/**
		 * 是否解码出现异常时显示404
		 */
		private boolean decode404;
		/**
		 * 解码后关闭
		 */
		private boolean closeAfterDecode = true;
		/**
		 * 异常传播策略
		 */
		private ExceptionPropagationPolicy propagationPolicy = NONE;
		/**
		 * 强制进行解码，默认是false
		 */
		private boolean forceDecoding = false;
		/**
		 * feign配置信息可以在构建的时候进行自定义处理
		 */
		private List<Capability> capabilities = new ArrayList<>();

		public Builder logLevel(Logger.Level logLevel) {
			this.logLevel = logLevel;
			return this;
		}

		public Builder contract(Contract contract) {
			this.contract = contract;
			return this;
		}

		public Builder client(Client client) {
			this.client = client;
			return this;
		}

		public Builder retryer(Retryer retryer) {
			this.retryer = retryer;
			return this;
		}

		public Builder logger(Logger logger) {
			this.logger = logger;
			return this;
		}

		public Builder encoder(Encoder encoder) {
			this.encoder = encoder;
			return this;
		}

		public Builder decoder(Decoder decoder) {
			this.decoder = decoder;
			return this;
		}

		public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
			this.queryMapEncoder = queryMapEncoder;
			return this;
		}

		/**
		 * Allows to map the response before passing it to the decoder.
		 */
		public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
			this.decoder = new ResponseMappingDecoder(mapper, decoder);
			return this;
		}

		/**
		 * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
		 * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
		 * <p>
		 * <p/>
		 * All first-party (ex gson) decoders return well-known empty values defined by
		 * {@link Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder)
		 * decoder} or make your own.
		 * <p>
		 * <p/>
		 * This flag only works with 404, as opposed to all or arbitrary status codes. This was an
		 * explicit decision: 404 -> empty is safe, common and doesn't complicate redirection, retry or
		 * fallback policy. If your server returns a different status for not-found, correct via a
		 * custom {@link #client(Client) client}.
		 *
		 * @since 8.12
		 */
		public Builder decode404() {
			this.decode404 = true;
			return this;
		}

		public Builder errorDecoder(ErrorDecoder errorDecoder) {
			this.errorDecoder = errorDecoder;
			return this;
		}

		public Builder options(Options options) {
			this.options = options;
			return this;
		}

		/**
		 * Adds a single request interceptor to the builder.
		 */
		public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
			this.requestInterceptors.add(requestInterceptor);
			return this;
		}

		/**
		 * Sets the full set of request interceptors for the builder, overwriting any previous
		 * interceptors.
		 */
		public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
			this.requestInterceptors.clear();
			for (RequestInterceptor requestInterceptor : requestInterceptors) {
				this.requestInterceptors.add(requestInterceptor);
			}
			return this;
		}

		/**
		 * Allows you to override how reflective dispatch works inside of Feign.
		 */
		public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
			this.invocationHandlerFactory = invocationHandlerFactory;
			return this;
		}

		/**
		 * This flag indicates that the response should not be automatically closed upon completion of
		 * decoding the message. This should be set if you plan on processing the response into a
		 * lazy-evaluated construct, such as a {@link java.util.Iterator}.
		 *
		 * </p>
		 * Feign standard decoders do not have built in support for this flag. If you are using this
		 * flag, you MUST also use a custom Decoder, and be sure to close all resources appropriately
		 * somewhere in the Decoder (you can use {@link Util#ensureClosed} for convenience).
		 *
		 * @since 9.6
		 */
		public Builder doNotCloseAfterDecode() {
			this.closeAfterDecode = false;
			return this;
		}

		public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
			this.propagationPolicy = propagationPolicy;
			return this;
		}

		public Builder addCapability(Capability capability) {
			this.capabilities.add(capability);
			return this;
		}

		/**
		 * Internal - used to indicate that the decoder should be immediately called
		 */
		Builder forceDecoding() {
			this.forceDecoding = true;
			return this;
		}

		public <T> T target(Class<T> apiType, String url) {
			return target(new HardCodedTarget<T>(apiType, url));
		}

		public <T> T target(Target<T> target) {
			return build().newInstance(target);
		}

		/**
		 * 根据设置的参数，创建feign对象，实例返回的是ReflectiveFeign
		 */
		public Feign build() {
			Client client = Capability.enrich(this.client, capabilities);
			Retryer retryer = Capability.enrich(this.retryer, capabilities);
			List<RequestInterceptor> requestInterceptors = this.requestInterceptors
					.stream()
					.map(ri -> Capability.enrich(ri, capabilities))
					.collect(Collectors.toList());
			Logger logger = Capability.enrich(this.logger, capabilities);
			Contract contract = Capability.enrich(this.contract, capabilities);
			Options options = Capability.enrich(this.options, capabilities);
			Encoder encoder = Capability.enrich(this.encoder, capabilities);
			Decoder decoder = Capability.enrich(this.decoder, capabilities);
			InvocationHandlerFactory invocationHandlerFactory = Capability.enrich(this.invocationHandlerFactory, capabilities);
			QueryMapEncoder queryMapEncoder = Capability.enrich(this.queryMapEncoder, capabilities);

			SynchronousMethodHandler.Factory synchronousMethodHandlerFactory = new SynchronousMethodHandler.Factory(client,
					retryer, requestInterceptors, logger, logLevel, decode404, closeAfterDecode, propagationPolicy, forceDecoding);
			ParseHandlersByName handlersByName = new ParseHandlersByName(contract, options, encoder, decoder, queryMapEncoder,
					errorDecoder, synchronousMethodHandlerFactory);

			//这里主要关注一下invocationHandlerFactory，这个是产生代理对象的工厂，默认是InvocationHandlerFactory.Default()
			//InvocationHandler默认使用ReflectiveFeign.FeignInvocationHandler
			//todo 用户可以自己实现，用于扩展自己的InvocationHandlerFactory
			//todo 用户还需要实现一个InvocationHandler的对象，用于实现代理逻辑
			return new ReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
		}
	}

	public static class ResponseMappingDecoder implements Decoder {

		private final ResponseMapper mapper;
		private final Decoder delegate;

		public ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
			this.mapper = mapper;
			this.delegate = decoder;
		}

		@Override
		public Object decode(Response response, Type type) throws IOException {
			return delegate.decode(mapper.map(response, type), type);
		}
	}
}
