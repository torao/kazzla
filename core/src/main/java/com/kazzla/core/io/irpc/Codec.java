/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;

import com.kazzla.core.io.async.RawBuffer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Codec
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * Open, Close, Block のインスタンスをシリアライズ/デシリアライズするためのクラス。
 * @author Takami Torao
 */
public abstract class Codec {
	public static final int MAX_PACKET_SIZE = 0xFFFF;

	public abstract ByteBuffer encode(Pipe.Open open) throws CodecException;
	public abstract ByteBuffer encode(Pipe.Close close) throws CodecException;
	public abstract ByteBuffer encode(Pipe.Block block) throws CodecException;
	public abstract Object decode(RawBuffer buffer) throws CodecException;

	public enum Type {
		NULL(0),
		BOOLEAN(1, Boolean.class, boolean.class),
		TINY(2, Byte.class, byte.class),
		SHORT(3, Short.class, short.class),
		INT(4, Integer.class, int.class),
		LONG(5, Long.class, long.class),
		FLOAT(6, Float.class, float.class),
		DOUBLE(7, Double.class, double.class),
		BINARY(8, byte[].class),
		STRING(9, String.class),
		ARRAY(10, List.class, Object[].class),
		MAP(11, Map.class),
		;
		public final byte id;
		public final Class<?>[] types;
		private Type(int id, Class<?>... types){
			this.id = (byte)id;
			this.types = types;
		}

		public static Type valueOf(byte id){
			for(Type t: values()){
				if(t.id == id){
					return t;
				}
			}
			return null;
		}

		public static Type valueOf(Class<?> clazz){
			// TODO clazz のスーパークラスまでさかのぼって互換性のある値を返す
			for(Type t: values()){
				for(Class<?> c: t.types){
					if(c.equals(clazz)){
						return t;
					}
				}
			}
			return null;
		}

	}

}
