package com.kazzla.domain.irpc

/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
trait ServerAddress {

	// ========================================================================
	//
	// ========================================================================
	/**
	 */
	def create(): ServerSocketChannel

}
