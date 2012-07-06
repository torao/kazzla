/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger
import java.nio.ByteBuffer
import com.kazzla.KazzlaException

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Interactive Remote Procedure Call
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 双方向 RPC のためのパッケージです。
 * @author Takami Torao
 */
package object irpc {
	val logger = Logger.getLogger(this.getClass)
}
