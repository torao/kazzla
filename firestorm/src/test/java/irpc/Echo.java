/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package irpc;
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Echo
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Takami Torao
 */
public interface Echo {

	@RemoteProcedure(10)
	public String echo(String text);

	@RemoteProcedure(11)
	public String reverse(String text);

	@RemoteProcedure(12)
	public void stream();

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
		public void stream(){
			logger.debug("stream()");
			Pipe pipe = Pipe.currentPipe();
			InputStream in = pipe.getInputStream();
			OutputStream out = pipe.getOutputStream();
			byte[] buffer = new byte[1024];
			try{
				while(true){
					int len = in.read(buffer);
					if(len < 0){
						break;
					}
					out.write(buffer, 0, len);
				}
			} catch(IOException ex){
				ex.printStackTrace();
			}
		}
	}

}
