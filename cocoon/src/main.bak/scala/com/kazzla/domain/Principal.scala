/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import java.security.{Signature, PublicKey, PrivateKey}


// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Principal
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 * @param id
 * @param name
 * @param publicKey
 * @param privateKey
 */
abstract class Principal(id:Long, name:String, publicKey:PublicKey, privateKey:PrivateKey) {

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 *
	 * @param id
	 * @param name
	 * @param publicKey
	 */
	def this(id:Long, name:String, publicKey:PublicKey) = {
		this(id, name, publicKey, null)
	}

	// ========================================================================
	// 電子署名の検証
	// ========================================================================
	/**
	 * 指定された電子署名がこの主体によって行われたものかを検証します。
	 * @param binary 検証するバイナリ
	 * @return この主体による署名の場合 true
	 */
	def verify(binary:Array[Byte], sign:Array[Byte]):Boolean = {
		val signature = Signature.getInstance(Principal.SIGNATURE_ALGORITHM)
		signature.initVerify(publicKey)
		signature.update(binary)
		signature.verify(sign)
	}

	// ========================================================================
	// 電子署名の発行
	// ========================================================================
	/**
	 * 指定されたプライベート鍵を用いて指定されたバイナリに対する署名を作成します。
	 * @param binary 署名するバイナリ
	 * @param privateKey 署名に使用するプライベート鍵
	 * @return バイナリに対する電子署名
	 */
	def sign(privateKey:PrivateKey, binary:Array[Byte]):Array[Byte] = {
		val signature = Signature.getInstance(Principal.SIGNATURE_ALGORITHM)
		signature.initSign(privateKey)
		signature.update(binary)
		signature.sign()
	}

}

object Principal {

	// ========================================================================
	// 電子署名アルゴリズム
	// ========================================================================
	/**
	 * 電子署名のためのアルゴリズムです。
	 */
	val SIGNATURE_ALGORITHM = "SHA256withRSA"

}