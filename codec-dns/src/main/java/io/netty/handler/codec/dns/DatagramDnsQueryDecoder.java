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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.dns.DnsMessageFlags.FlagSet;
import io.netty.util.internal.UnstableApi;
import static io.netty.handler.codec.dns.DnsMessageFlags.IS_REPLY;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Query decoder for DNS servers.
 */
@UnstableApi
@ChannelHandler.Sharable
public class DatagramDnsQueryDecoder extends MessageToMessageDecoder<DatagramPacket> {

    protected final DnsRecordDecoder recordDecoder;
    protected final NameCodec.Factory names;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public DatagramDnsQueryDecoder() {
        this(DnsRecordDecoder.DEFAULT, NameCodec.compressingFactory());
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}.
     */
    public DatagramDnsQueryDecoder(DnsRecordDecoder decoder) {
        this(decoder, NameCodec.compressingFactory());
    }

    public DatagramDnsQueryDecoder(DnsRecordDecoder decoder, NameCodec.Factory names) {
        this.recordDecoder = decoder;
        this.names = names;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        final InetSocketAddress sender = packet.sender();
        final ByteBuf buf = packet.content();

        final DatagramDnsQuery query = toQuery(sender, packet.recipient(), buf);
        boolean success = false;
        NameCodec nameCodec = names.getForRead();
        try {
            final int questionCount = buf.readUnsignedShort();
            final int answerCount = buf.readUnsignedShort();
            final int authorityRecordCount = buf.readUnsignedShort();
            final int additionalRecordCount = buf.readUnsignedShort();

            decodeQuestions(query, buf, questionCount, nameCodec);
            decodeRecords(query, DnsSection.ANSWER, buf, answerCount, nameCodec);
            decodeRecords(query, DnsSection.AUTHORITY, buf, authorityRecordCount, nameCodec);
            decodeRecords(query, DnsSection.ADDITIONAL, buf, additionalRecordCount, nameCodec);

            out.add(query);
            success = true;
        } finally {
            nameCodec.close();
            if (!success) {
                query.release();
            }
        }
    }

    private DatagramDnsQuery toQuery(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf buf) {
        final int id = buf.readUnsignedShort();
        short flags = buf.readShort();
        FlagSet flagSet = DnsMessageFlags.forFlags(flags);
        if (flagSet.contains(IS_REPLY)) {
            throw new CorruptedFrameException("not a question - flags " + flagSet + " for id " + id + " from "
                    + sender + " to " + recipient);
        }
        final DatagramDnsQuery query = new DatagramDnsQuery(
                sender, recipient,
                id, DnsOpCode.valueOf((byte) (flags >> 11 & 0xf)), flagSet);

        query.setZ(flags >> 4 & 0x7);
        return query;
    }

    private void decodeQuestions(DatagramDnsQuery query, ByteBuf buf, int questionCount,
            NameCodec nameCodec) throws Exception {
        for (int i = 0; i < questionCount; i++) {
            DnsQuestion question = recordDecoder.decodeQuestion(buf, nameCodec);
            query.addRecord(DnsSection.QUESTION, question);
        }
    }

    private void decodeRecords(DatagramDnsQuery query, DnsSection section, ByteBuf buf, int count,
            NameCodec nameCodec) throws Exception {
        for (int i = 0; i < count; i++) {
            DnsRecord record = recordDecoder.decodeRecord(buf, nameCodec);
            query.addRecord(section, record);
        }
    }
}
