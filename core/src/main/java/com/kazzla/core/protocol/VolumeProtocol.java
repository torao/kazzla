/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

import com.kazzla.core.protocol.volume.Block;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// VolumeProtocol
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class VolumeProtocol extends Protocol {

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 * このプロトコル層の下層となるエンドポイントを指定して構築を行います。
	 * @param endpoint エンドポイント
	 */
	protected VolumeProtocol(Endpoint endpoint){
		super(endpoint);
		return;
	}

	// ==============================================================================================
	// ファクトリの登録
	// ==============================================================================================
	/**
	 * サブクラスのプロトコルが定義するデータタイプを登録するためにオーバーライドする必要があります。
	 */
	@Override
	protected void registerTransferable(){
		register(Block.Allocate.TYPE, Block.Allocate.class);
		return;
	}

}
