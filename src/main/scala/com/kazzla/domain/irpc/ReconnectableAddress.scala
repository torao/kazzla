package com.kazzla.domain.irpc

/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
trait ReconnectableAddress {

	// ========================================================================
	//
	// ========================================================================
	/**
	 * このセッション上で確保されている不必要なリソースを開放します。
	 */
	def connect(): (SelectableChannel with ReadableByteChannel, SelectableChannel with WritableByteChannel)

}
