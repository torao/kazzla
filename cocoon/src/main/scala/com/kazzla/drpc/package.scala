/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla

import org.apache.log4j.Logger

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Duplex Remote Procedure Call
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 双方向 RPC のためのパッケージです。
 * @author Takami Torao
 */
package object drpc {
	val logger = Logger.getLogger(this.getClass)
}
