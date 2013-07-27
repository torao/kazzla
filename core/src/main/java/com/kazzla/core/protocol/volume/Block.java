/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol.volume;

import com.kazzla.core.io.IO;
import com.kazzla.core.protocol.Transferable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Block
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public interface Block {

	public class Allocate extends Transferable {
		public static final short TYPE = 0x0100;
		public final UUID id;
		public final int length;
		public Allocate(UUID id, int length){
			super(TYPE);
			this.id = id;
			this.length = length;
		}
		public Allocate(DataInput in) throws IOException {
			super(TYPE, in);
			this.id = IO.readUUID(in);
			this.length = in.readInt();
		}
		protected void writeTo(DataOutput out) throws IOException{
			IO.write(out, id);
			out.writeInt(length);
		}
	}

	public class Delete extends Transferable {
		public static final short TYPE = 0x0101;
		public final UUID id;
		public Delete(UUID id, int length){
			super(TYPE);
			this.id = id;
		}
		public Delete(DataInput in) throws IOException {
			super(TYPE, in);
			this.id = IO.readUUID(in);
		}
		protected void writeTo(DataOutput out) throws IOException{
			IO.write(out, id);
		}
	}

	public class Checksum extends Transferable {
		public static final short TYPE = 0x0102;
		public final UUID id;
		public Checksum(UUID id, int length){
			super(TYPE);
			this.id = id;
		}
		public Checksum(DataInput in) throws IOException {
			super(TYPE, in);
			this.id = IO.readUUID(in);
		}
		protected void writeTo(DataOutput out) throws IOException{
			IO.write(out, id);
		}
	}

	public class Read extends Transferable {
		public static final short TYPE = 0x0102;
		public final UUID id;
		public final int offset;
		public final int length;
		public Read(UUID id, int offset, int length){
			super(TYPE);
			this.id = id;
			this.offset = offset;
			this.length = length;
			if(length > 0xFFFF){
				throw new IllegalArgumentException(String.format("length too large %d > 0xFFFF", length));
			}
		}
		public Read(DataInput in) throws IOException {
			super(TYPE, in);
			this.id = IO.readUUID(in);
			this.offset = in.readInt();
			this.length = in.readShort() & 0xFFFF;
		}
		protected void writeTo(DataOutput out) throws IOException{
			IO.write(out, id);
			out.writeInt(offset);
			out.writeShort(length & 0xFFFF);
		}
	}

	public class Write extends Transferable {
		public static final short TYPE = 0x0103;
		public final UUID id;
		public final int offset;
		public final byte[] data;
		public Write(UUID id, int offset, byte[] data){
			super(TYPE);
			this.id = id;
			this.offset = offset;
			this.data = data;
			if(data.length > 0xFFFF){
				throw new IllegalArgumentException(String.format("length too large %d > 0xFFFF", data.length));
			}
		}
		public Write(DataInput in) throws IOException {
			super(TYPE, in);
			this.id = IO.readUUID(in);
			this.offset = in.readInt();
			this.data = IO.readUShortBinary(in);
		}
		protected void writeTo(DataOutput out) throws IOException{
			IO.write(out, id);
			out.writeInt(offset);
			IO.writeUShortBinary(out, data);
		}
	}

}
