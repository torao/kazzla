/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import java.security.{Signature, PrivateKey}
import java.util.Properties

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Configuration
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Configuration(config:Properties) {

	// ========================================================================
	// 電子署名アルゴリズム
	// ========================================================================
	/**
	 * このドメインで使用している電子署名のアルゴリズムです。
	 */
	lazy val signatureAlgorithm:String = config.getProperty("security.signature.algorithm", "SHA256withRSA")

}
