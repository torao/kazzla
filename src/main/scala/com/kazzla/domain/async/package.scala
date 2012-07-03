/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// package
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 非同期入出力のためのパイプライン機能を含むパッケージです。
 * </p>
 * <div>
 *   <img src="doc-files/Pipeline.png" alt="Pipeline"/>
 * </div>
 * <dt>
 *   <dt>Sink</dt>
 *   <dd>パイプランが非同期で読み込んだデータを渡す関数。</dd>
 * </dt>
 * @author Takami Torao
 */
package object async {
	val logger = Logger.getLogger(this.getClass)
}
