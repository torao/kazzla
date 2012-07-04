/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import org.msgpack.MessagePack;
import com.kazzla.domain.async.RawBuffer;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.Unpacker;
import org.msgpack.type.*;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// MsgPackCodec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * MessagePack 形式のコーデックです。
 * インスタンスは <tt>Codec.getCode("messagepack")</tt> で参照することができます。
 * @author Takami Torao
 */
public class MsgPackCodec extends Codec {

	// ========================================================================
	// コーデックバージョン
	// ========================================================================
	/**
	 * コーデックのバージョンです。
	 */
	public static final byte VERSION = 0;

	public static final byte CONTROL = 0;
	public static final byte OPEN = 1;
	public static final byte CLOSE = 2;
	public static final byte BLOCK = 3;

	private static final byte[] EMPTY = new byte[]{0, 0, 0, 0};

	// ========================================================================
	// コーデックバージョン
	// ========================================================================
	/**
	 * コーデックのバージョンです。
	 */
	MsgPackCodec(){
		return;
	}

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * 指定された転送単位をバイナリに変換します。
	 */
	@Override
	public byte[] pack(Transferable unit) throws RemoteException, IOException{
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// 先頭に設定する長さの分だけスキップ
		out.write(EMPTY);

		// 転送ユニットをシリアライズ
		MessagePack mpack = new MessagePack();
		Packer packer = mpack.createPacker(out);
		packer.write(VERSION);
		if(unit instanceof Open){
			Open open = (Open)unit;
			packer.write(OPEN);
			packer.write(open.pipeId);
			packer.write(open.timeout);
			packer.write(open.callback);
			packer.write(open.name);
			pack(packer, open.args);
		} else if(unit instanceof Close){
			Close close = (Close)unit;
			packer.write(CLOSE);
			packer.write(close.pipeId);
			packer.write(close.code);
			packer.write(close.message);
			pack(packer, close.args);
		} else if(unit instanceof Block){
			Block block = (Block)unit;
			packer.write(BLOCK);
			packer.write(block.pipeId);
			packer.write(block.sequence);
			packer.write(block.binary);
		} else if(unit instanceof Control){
			Control control = (Control)unit;
			packer.write(CONTROL);
			packer.write(control.pipeId);
			packer.write(control.code);
			pack(packer, control.args);
		} else {
			throw new RemoteException(String.format("unsupported transfer-unit: %s", unit));
		}
		packer.flush();
		byte[] binary = out.toByteArray();

		// ビッグエンディアンで 4 バイトの長さを設定
		int len = binary.length - 4;
		for(int i=0; i<4; i++){
			binary[3 - i] = (byte)((len >> (i * 8)) & 0xFF);
		}

		return binary;
	}

	// ========================================================================
	// バッファの復元
	// ========================================================================
	/**
	 * 指定されたバッファから転送単位を復元します。バッファにオブジェクトを復元可能なデータ
	 * が揃っていない場合は None を返します。
	 */
	@Override
	public Transferable unpack(RawBuffer buffer) throws RemoteException, IOException{

		// 長さ部分をまだ受信していない
		if(buffer.length() < 4){
			return null;
		}

		// 転送単位の長さを取得
		int len =
			((buffer.raw()[buffer.offset() + 0] & 0xFF) << 24) |
				((buffer.raw()[buffer.offset() + 1] & 0xFF) << 16) |
				((buffer.raw()[buffer.offset() + 2] & 0xFF) <<  8) |
				((buffer.raw()[buffer.offset() + 3] & 0xFF) <<  0);

		// まだすべてのデータを受信していなければ何もしない
		if(buffer.length() < len + 4){
			return null;
		}

		MessagePack mpack = new MessagePack();
		Unpacker packer = mpack.createBufferUnpacker(buffer.raw(), buffer.offset() + 4, buffer.length());
		byte version = packer.readByte();
		byte type = packer.readByte();
		switch(type) {
		case OPEN: {
				long pipeId = packer.readLong();
				long timeout = packer.readLong();
				boolean callback = packer.readBoolean();
				String name = packer.readString();
				Object[] args = unpackArgs(packer);
				return new Open(pipeId, timeout, callback, name, args);
			}
		case CLOSE: {
				long pipeId = packer.readLong();
				Close.Code code = Close.Code.getCode(packer.readByte());
				String error = packer.readString();
				Object[] args = unpackArgs(packer);
				return new Close(pipeId, code, error, args);
			}
		case BLOCK: {
				long pipeId = packer.readLong();
				int seq = packer.readInt();
				byte[] bin = packer.readByteArray();
				return new Block(pipeId, seq, bin);
			}
		case CONTROL: {
				long pipeId = packer.readLong();
				byte code = packer.readByte();
				Object[] args = unpackArgs(packer);
				return new Control(pipeId, code, args);
			}
		default:
			throw new RemoteException(String.format("unsupported transfer-unit type: 0x%02X", type & 0xFF));
		}
	}

	// ========================================================================
	// 可変データのパック
	// ========================================================================
	/**
	 * 指定された可変長データをパックします。
	 */
	private void pack(Packer packer, Object[] args) throws RemoteException, IOException{
		packer.writeArrayBegin(args.length);
		for(Object arg: args){
			packer.write(toValue(arg));
		}
		packer.writeArrayEnd();
	}

	// ========================================================================
	// 可変データのパック
	// ========================================================================
	/**
	 * 指定された可変長データをパックします。
	 */
	private Object[] unpackArgs(Unpacker packer) throws RemoteException, IOException{
		int length = packer.readArrayBegin();
		Object[] args = new Object[length];
		for(int i=0; i<length; i++){
			args[i] = fromValue(packer.readValue());
		}
		packer.readArrayEnd();
		return args;
	}

	// ========================================================================
	// 値の変換
	// ========================================================================
	/**
	 * 指定された値を MessagePack 用の値に変換します。
	 */
	private Value toValue(Object value) throws RemoteException{
		if(value == null){
			return ValueFactory.createNilValue();
		}
		if(value instanceof Boolean){
			return ValueFactory.createBooleanValue(((Boolean)value).booleanValue());
		}
		if(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long){
			return ValueFactory.createIntegerValue(((Number)value).longValue());
		}
		if(value instanceof Float || value instanceof Double){
			return ValueFactory.createFloatValue(((Number)value).doubleValue());
		}
		if(value instanceof Character || value instanceof String){
			return ValueFactory.createRawValue(value.toString());
		}
		if(value instanceof byte[]){
			return ValueFactory.createRawValue((byte[])value);
		}
		if(value instanceof Map){
			Set<Map.Entry<Object,Object>> entries = ((Map<Object,Object>)value).entrySet();
			Value[] values = new Value[entries.size()];
			int i = 0;
			for(Map.Entry<Object,Object> e: entries){
				values[i] = toValue(e.getKey());
				values[i+1] = toValue(e.getValue());
				i += 2;
			}
			return ValueFactory.createMapValue(values, true);
		}
		if(value instanceof Iterable){
			List<Value> values = new ArrayList<Value>();
			for(Object e: (Iterable)value){
				values.add(toValue(e));
			}
			return ValueFactory.createArrayValue(values.toArray(new Value[values.size()]), true);
		}
		throw new RemoteException("unsupported value type: " + value);
	}

	// ========================================================================
	// 値の変換
	// ========================================================================
	/**
	 * MessagePack 用の値から Java のオブジェクトへ変換します。
	 */
	private Object fromValue(Value value) throws RemoteException{
		if(value.isNilValue()){
			return null;
		}
		if(value.isBooleanValue()){
			return value.asBooleanValue().getBoolean();
		}
		if(value.isIntegerValue()){
			return value.asIntegerValue().getLong();
		}
		if(value.isFloatValue()){
			return value.asFloatValue().getDouble();
		}
		if(value.isRawValue()){
			return value.asRawValue().getByteArray();
		}
		if(value.isArrayValue()){
			ArrayValue array = value.asArrayValue();
			List<Object> list = new ArrayList<Object>(array.size());
			for(int i=0; i<array.size(); i++){
				list.add(fromValue(array.get(i)));
			}
			return list;
		}
		if(value.isMapValue()){
			MapValue map = value.asMapValue();
			Map<Object,Object> m = new HashMap<Object,Object>();
			Value[] kv = map.getKeyValueArray();
			for(int i=0; i<kv.length; i+=2){
				m.put(fromValue(kv[i]), fromValue(kv[i+1]));
			}
			return m;
		}
		throw new RemoteException("unsupported value type: " + value);
	}

}
