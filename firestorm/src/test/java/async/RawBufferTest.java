/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package async;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.Assert.*;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBufferTest
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
@RunWith(JUnit4.class)
public class RawBufferTest {

	@Test
	public void testコンストラクタ(){
		RawBuffer buffer = new RawBuffer();
		assertEquals(0, buffer.length());

		buffer = new RawBuffer(1024);
		assertEquals(0, buffer.length());

		buffer = new RawBuffer(0);
		assertEquals(0, buffer.length());

		try {
			new RawBuffer(-1);
			Assert.fail();
		} catch(RuntimeException ex){ }
	}


	private static byte[] sequentialBinary(int length){
		byte[] b = new byte[length];
		for(int i=0; i<b.length; i++){
			b[i] = (byte)i;
		}
		return b;
	}

	private static byte[] randomBinary(int length){
		Random random = new Random();
		byte[] b = new byte[length];
		random.nextBytes(b);
		return b;
	}

}
