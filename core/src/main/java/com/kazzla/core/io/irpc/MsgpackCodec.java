/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// MsgpackCodec
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import com.kazzla.core.io.async.RawBuffer;
import org.msgpack.MessagePack;
import org.msgpack.packer.BufferPacker;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Takami Torao
 */
public class MsgpackCodec extends Codec {

	public ByteBuffer encode(Pipe.Open open){
		MessagePack msgpack = new MessagePack();
		BufferPacker packer = msgpack.createBufferPacker();
		try {
			packer.write(Pipe.Open.TYPE);
			packer.write(open.id);
			encode(packer, short.class, open.method);
			encode(packer, Object[].class, open.params);
		} catch(IOException ex){
			throw new CodecException(ex);
		}
		return ByteBuffer.wrap(packer.toByteArray());
	}

	public ByteBuffer encode(Pipe.Close close){
		MessagePack msgpack = new MessagePack();
		BufferPacker packer = msgpack.createBufferPacker();
		try {
			packer.write(Pipe.Close.TYPE);
			packer.write(close.id);
			encode(packer, close.result);
			encode(packer, close.errorMessage);
		} catch(IOException ex){
			throw new CodecException(ex);
		}
		return ByteBuffer.wrap(packer.toByteArray());
	}

	public ByteBuffer encode(Pipe.Block block){
		MessagePack msgpack = new MessagePack();
		BufferPacker packer = msgpack.createBufferPacker();
		try {
			packer.write(Pipe.Block.TYPE);
			packer.write(block.id);
			encode(packer, byte[].class, block.binary);
		} catch(IOException ex){
			throw new CodecException(ex);
		}
		return ByteBuffer.wrap(packer.toByteArray());
	}

	public Object decode(RawBuffer buffer) throws CodecException{
		MessagePack msgpack = new MessagePack();
		BufferUnpacker unpacker = msgpack.createBufferUnpacker(buffer.toByteBuffer());
		try {
			Object packet;
			switch(unpacker.readByte()){
				case Pipe.Open.TYPE:
					packet = new Pipe.Open(unpacker.readInt(), ((Short)decode(unpacker)), (Object[])decode(unpacker));
					break;
				case Pipe.Close.TYPE:
					packet = new Pipe.Close<Object>(unpacker.readInt(), decode(unpacker), (String)decode(unpacker));
					break;
				case Pipe.Block.TYPE:
					packet = new Pipe.Block(unpacker.readInt(), (byte[])decode(unpacker));
					break;
				default:
					throw new CodecException();
			}
			buffer.consume(unpacker.getReadByteCount());
			return packet;
		} catch(ClassCastException ex){
			throw new CodecException("unexpected type", ex);
		} catch(IOException ex){
			return null;
		}
	}


	private static void encode(BufferPacker packer, Object value) throws IOException {
		if(value == null){
			packer.write(Type.NULL.id);
			packer.writeNil();
		} else {
			encode(packer, value.getClass(), value);
		}
	}

	private static void encode(BufferPacker packer, Class<?> clazz, Object value) throws IOException {
		Type type = Type.valueOf(clazz);
		if(type == null){
			throw new CodecException(String.format("unsupported data-type: %s", clazz));
		}
		packer.write(type.id);
		if(value == null){
			packer.writeNil();
			return;
		}
		switch(type){
			case BOOLEAN:
				packer.write((Boolean)value);
				break;
			case TINY:
				packer.write((Byte)value);
				break;
			case SHORT:
				packer.write((Short)value);
				break;
			case INT:
				packer.write((Integer)value);
				break;
			case LONG:
				packer.write((Long)value);
				break;
			case FLOAT:
				packer.write((Float)value);
				break;
			case DOUBLE:
				packer.write((Double)value);
				break;
			case STRING:
				packer.write((String)value);
				break;
			case BINARY:
				packer.write((byte[])value);
				break;
			case ARRAY:
				if(value instanceof List){
					List<?> list = (List<?>)value;
					packer.write(list.size());
					for(Object o: list){
						encode(packer, o);
					}
				} else if(clazz.isArray()){
					Object[] array = (Object[])value;
					packer.write(array.length);
					for(Object o: array){
						encode(packer, o);
					}
				}
				break;
			case MAP:
				Map<?,?> map = (Map<?,?>)value;
				packer.write(map.size());
				for(Map.Entry<?,?> e: map.entrySet()){
					encode(packer, Object.class, e.getKey());
					encode(packer, Object.class, e.getValue());
				}
				break;
		}
	}

	private static Object decode(BufferUnpacker unpacker) throws IOException {
		byte typeId = unpacker.readByte();
		Type type = Type.valueOf(typeId);
		if(type == null){
			throw new CodecException(String.format("unexpected type-id detected: 0x%02X", typeId & 0xFF));
		}
		switch(type){
			case NULL:
				unpacker.readNil();
				return null;
			case BOOLEAN:
				return unpacker.readBoolean();
			case TINY:
				return unpacker.readByte();
			case SHORT:
				return unpacker.readShort();
			case INT:
				return unpacker.readInt();
			case LONG:
				return unpacker.readLong();
			case FLOAT:
				return unpacker.readFloat();
			case DOUBLE:
				return unpacker.readDouble();
			case STRING:
				return unpacker.readString();
			case BINARY:
				return unpacker.readByteArray();
			case ARRAY:
				int length = unpacker.readInt();
				Object[] array = new Object[length];
				for(int i=0; i<length; i++){
					array[i] = decode(unpacker);
				}
				return array;
			case MAP:
				int size = unpacker.readInt();
				Map<Object,Object> map = new HashMap<>();
				for(int i=0; i<size; i++){
					Object key = decode(unpacker);
					Object value = decode(unpacker);
					map.put(key, value);
				}
				return map;
			default:
				throw new IllegalStateException(String.format("unsupported data-type specified: %s", type));
		}
	}

}
