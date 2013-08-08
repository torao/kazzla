/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core

import java.lang.reflect.Method

package object debug {

	implicit def richMethod(method:Method) = new {
		def getSimpleName:String = {
			method.getDeclaringClass.getSimpleName + "." + method.getName + "(" + method.getParameterTypes.map { p =>
				p.getSimpleName
			}.mkString(",") + "):" + method.getReturnType.getSimpleName
		}
	}
}
