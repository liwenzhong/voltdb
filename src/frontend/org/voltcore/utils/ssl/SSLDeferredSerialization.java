/* This file is part of VoltDB.
 * Copyright (C) 2008-2016 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltcore.utils.ssl;

import org.voltcore.utils.DeferredSerialization;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SSLDeferredSerialization implements DeferredSerialization {

    private final SSLEngine m_sslEngine;
    private final ByteBuffer m_src;

    public SSLDeferredSerialization(SSLEngine sslEngine, ByteBuffer src) {
        this.m_sslEngine = sslEngine;
        this.m_src = src;
    }

    @Override
    public ByteBuffer serialize(ByteBuffer dst) throws IOException {
        int initialDstPosition = dst.position();
        dst.position(dst.position() + 4);
        ByteBuffer encryptInto = dst.slice();
        ByteBuffer allocated = encrypt(m_src, encryptInto);
        if (allocated == null) {
            int amountEncrypted = encryptInto.position();
            dst.position(initialDstPosition);
            dst.putInt(amountEncrypted);
            dst.position(dst.position() + amountEncrypted);
        } else {
            dst.position(initialDstPosition);
            int amountEncrypted = allocated.position() - 4;
            allocated.position(0);
            allocated.putInt(amountEncrypted);
            allocated.position(4 + amountEncrypted);
            allocated.flip();
        }
        return allocated;
    }

    @Override
    public void cancel() {

    }

    @Override
    public int getSerializedSize() throws IOException {
        throw new UnsupportedOperationException("getSerializedSize is not supported by SSLDeferredSerialization");
    }

    private ByteBuffer encrypt(ByteBuffer src, ByteBuffer dst) throws IOException {
        SSLEngineResult result = m_sslEngine.wrap(src, dst);
        switch (result.getStatus()) {
            case OK:
                return null;
            case BUFFER_OVERFLOW:
                ByteBuffer allocated = ByteBuffer.allocate(dst.remaining() * 2);
                return encryptWithAllocated(src, allocated);
            case BUFFER_UNDERFLOW:
                throw new IOException("Underflow on ssl wrap of buffer.");
            case CLOSED:
                throw new IOException("SSL engine is closed on ssl wrap of buffer.");
            default:
                throw new IOException("Unexpected SSLEngineResult.Status");
        }
    }

    private ByteBuffer encryptWithAllocated(ByteBuffer src, ByteBuffer dst) throws IOException {
        ByteBuffer allocated = dst;
        while (true) {
            allocated.position(4);
            ByteBuffer encryptInto = allocated.slice();
            SSLEngineResult result = m_sslEngine.wrap(src, encryptInto);
            switch (result.getStatus()) {
                case OK:
                    allocated.position(encryptInto.position() + 4);
                    return allocated;
                case BUFFER_OVERFLOW:
                    allocated = ByteBuffer.allocate(allocated.capacity() * 2);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new IOException("Underflow on ssl wrap of buffer.");
                case CLOSED:
                    throw new IOException("SSL engine is closed on ssl wrap of buffer.");
                default:
                    throw new IOException("Unexpected SSLEngineResult.Status");
            }
        }
    }
}