/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

import com.kazzla.domain.async.RawBuffer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Codec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロシジャーコールの転送単位をバイナリ化するトレイトです。
 * インスタンスはスレッドセーフです。
 * @author Takami Torao
 */
public abstract class Codec {

	// ========================================================================
	// ストリームヘッダの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリームのヘッダを参照します。
	 */
	protected static final byte[] EMPTY_BUFFER = new byte[0];

	// ========================================================================
	// ストリームヘッダの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリームのヘッダを参照します。
	 */
	public byte[] header(){
		return EMPTY_BUFFER;
	}

	// ========================================================================
	// ストリームヘッダの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリームのヘッダを参照します。
	 */
	public byte[] footer(){
		return EMPTY_BUFFER;
	}

	// ========================================================================
	// ユニットセパレータの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリーム転送の各転送ユニットごとの区切りを参照します。
	 */
	public byte[] separator(){
		return EMPTY_BUFFER;
	}

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * 指定された転送単位をバイナリに変換します。
	 */
	public abstract byte[] pack(Transferable unit) throws RemoteException, IOException;

	// ========================================================================
	// バッファの復元
	// ========================================================================
	/**
	 * 指定されたバッファから転送単位を復元します。バッファにオブジェクトを復元可能なデータ
	 * が揃っていない場合は null を返します。
	 */
	public abstract Transferable unpack(RawBuffer buffer) throws RemoteException, IOException;

	// ========================================================================
	// ファクトリ
	// ========================================================================
	/**
	 * 名前にマッピングされたコーデックのインスタンスです。
	 */
	private static final Map<String,Codec> codecs
		= Collections.synchronizedMap(new HashMap<String, Codec>());

	// ========================================================================
	// コーデックの登録
	// ========================================================================
	/**
	 * 指定された名前に対するコーデックを登録します。
	 * @param names コーデック名
	 * @param codec コーデック
	 */
	public static void register(Codec codec, String... names){
		for(String name: names){
			codecs.put(name.toLowerCase(), codec);
		}
	}

	// ========================================================================
	// コーデックの参照
	// ========================================================================
	/**
	 * 指定された名前に対するコーデックを参照します。コーデック名は大文字と小文字を区別し
	 * ません。名前に該当するコーデックが定義されていない場合は None を返します。
	 * @param name コーデック名
	 * @return コーデック
	 */
	public static Codec getCodec(String name){
		return codecs.get(name.toLowerCase());
	}

	static {
		Codec.register(new MsgPackCodec(), "msgpack", "application/x-msgpack");
	}

}
