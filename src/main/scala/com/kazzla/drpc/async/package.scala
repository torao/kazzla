/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import org.apache.log4j.Logger

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// package
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 非同期入出力のパイプラインを構成するためのパッケージです。
 * </p>
 * <div>
 *   <img src="doc-files/Pipeline.png" alt="Pipeline"/>
 * </div>
 * @author Takami Torao
 */
package object async {
	val logger = Logger.getLogger(this.getClass)
}
