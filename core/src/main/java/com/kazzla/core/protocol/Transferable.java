/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

import com.kazzla.core.io.IO;

import java.io.*;
import java.security.PrivateKey;
import java.security.Signature;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Transferable
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public abstract class Transferable {

	// ==============================================================================================
	// データタイプ
	// ==============================================================================================
	/**
	 * データタイプを表す数値です。
	 */
	public final short code;

	// ==============================================================================================
	// 電子署名
	// ==============================================================================================
	/**
	 * このデータの電子署名です。
	 */
	private byte[] sign = new byte[0];

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 * @param type データタイプ
	 */
	public Transferable(short type){
		this.code = type;
		return;
	}

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 */
	public Transferable(short code, DataInput in) throws IOException {
		this(code);
		this.sign = IO.readUShortBinary(in);
		return;
	}

	// ==============================================================================================
	// 電子署名の実行
	// ==============================================================================================
	/**
	 * このデータに電子署名を付与します。
	 */
	public void sign(PrivateKey key) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(key);
		signature.update(getBinary());
		this.sign = signature.sign();
	}

	// ==============================================================================================
	// 電子署名の検証
	// ==============================================================================================
	/**
	 * このデータの持つ電子署名が指定された証明書によって行われたものかを判定します。
	 */
	public boolean verify(java.security.cert.Certificate cert) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initVerify(cert);
		return signature.verify(getBinary());
	}

	// ==============================================================================================
	// バイナリの参照
	// ==============================================================================================
	/**
	 * 電子署名に使用するためのバイナリを参照します。
	 */
	private byte[] getBinary(){
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(baos);
			writeTo(out);
			out.flush();
			return baos.toByteArray();
		} catch(IOException ex){
			throw new IllegalStateException(ex);
		}
	}

	// ==============================================================================================
	// データの出力
	// ==============================================================================================
	/**
	 * 指定された出力ストリームにデータを出力します。
	 * @param out 出力先のストリーム
	 */
	public void write(DataOutput out) throws IOException{
		IO.writeUShortBinary(out, sign);
		writeTo(out);
		return;
	}

	// ==============================================================================================
	// データの出力
	// ==============================================================================================
	/**
	 * 指定された出力ストリームにデータを出力します。
	 * @param out 出力先のストリーム
	 */
	protected abstract void writeTo(DataOutput out) throws IOException;

	// ==============================================================================================
	// インスタンスの文字列化
	// ==============================================================================================
	/**
	 * このインスタンスを文字列化します。
	 */
	@Override
	public String toString(){
		return getClass().getSimpleName() + String.format("[0x%02X]", code & 0xFF) + "(" + ")";
	}

}
