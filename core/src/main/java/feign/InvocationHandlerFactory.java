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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Controls reflective method dispatch.
 * <p>
 * todo 创建InvocationHand的工厂，这个用户可以自己实现
 */
public interface InvocationHandlerFactory {

	/**
	 * 创建InvocationHandler，创建代理对象来执行target中每个方法的调用
	 * <p>
	 * todo target和dispatch可以创建InvocationHandler对象，针对每个方法的调用获取到具体每个方法对应的MethodHandler进行处理
	 *
	 * @param target   目标类，feign客户端
	 * @param dispatch fegin客户端中每个方法与方法执行器之间的映射
	 * @return InvocationHandler
	 */
	InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch);

	/**
	 * Like {@link InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])}, except for a
	 * single method.
	 * <p>
	 * 方法执行器，fegin客户端中具体的执行方法
	 */
	interface MethodHandler {

		Object invoke(Object[] argv) throws Throwable;
	}

	/**
	 * InvocationHandlerFactory的默认实现
	 */
	static final class Default implements InvocationHandlerFactory {

		@Override
		public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
			//默认使用ReflectiveFeign.FeignInvocationHandler
			return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
		}
	}
}
