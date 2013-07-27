/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Echo
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Takami Torao
 */
public interface Echo {

	@RemoteProcedure(10)
	public String echo(String text);

	@RemoteProcedure(11)
	public String reverse(String text);

	public class Service implements Echo {
		private static final Logger logger = LoggerFactory.getLogger(Service.class);
		public String echo(String text){
			logger.debug("echo(" + text + ")");
			return text;
		}
		public String reverse(String text){
			logger.debug("reverse(" + text + ")");
			return new StringBuilder(text).reverse().toString();
		}
	}

}
