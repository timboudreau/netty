/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns.names;

import io.netty.handler.codec.dns.wire.DnsMessageDecoder;
import io.netty.util.internal.UnstableApi;

/**
 * NameCodecFactory instances are providers of NameCodec, which can ensure you
 * always get a NameCodec that does not retain any state (such as the list of
 * offsets for compression pointers) from a previous use.  They are provided
 * to instances of {@link DnsMessageDecoder} and {@link DnsMessageEncoder} to
 * encode and decode names.
 * <p>
 * In particular, the NameCodecFactory instances returned by methods on NameCodec
 * manage thread-safety for stateful (compressing) codecs, ensuring that a codec
 * in use on one thread is not supplied to another concurrently.
 */
@UnstableApi
public interface NameCodecFactory {

    /**
     * Get a NameCodec for reading. The result of this call may not be used for
     * writing, and may throw an exception to prevent that.
     *
     * @return A namewriter
     */
    NameCodec getForRead();

    /**
     * Get a NameCodec for writing. The result of this call may not be used for
     * reading, and may throw an exception to prevent that (since it is very
     * likely a bug in the calling code if that happens).
     * <p>
     * A note about threading:  It is very important to call close() on a NameCodec
     * when you are done using it for writing - compressing NameCodecs hold a
     * state table to map strings to offsets in the buffer, which is cleared on
     * close.  Also, that returns the (thread-locally cached) NameCodec so a new
     * one is not created on the next call from the same thread.  While NameCodecs
     * are cached, do not <i>assume</i> two consecutive calls to getForWrite()
     * will return the same NameWriter unless an intervening call to close() has
     * been made.
     * <p>
     * In short, get it, write with it, close it, and everything will work right.
     *
     * @return A namewriter
     */
    NameCodec getForWrite();
}
