/*
 * Copyright 2026 LY Corporation
 *
 * LY Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import com.linecorp.armeria.common.grpc.GrpcJsonMarshaller;
import com.linecorp.armeria.server.Route;
import com.linecorp.armeria.server.grpc.HttpJsonTranscoder.TranscodingSpec;

import io.grpc.ServerServiceDefinition;
import testing.grpc.HttpJsonTranscodingTestServiceGrpc;
import testing.grpc.HttpJsonTranscodingTestServiceGrpc.HttpJsonTranscodingTestServiceImplBase;

class HttpJsonTranscoderBuilderTest {

    @Test
    void protoSerializationDisabledSkipsJsonMarshallerForServiceDefinitions() {
        final ServerServiceDefinition serviceDef =
                new HttpJsonTranscodingTestServiceImplBase() {}.bindService();
        final HttpJsonTranscoder transcoder =
                new HttpJsonTranscoderBuilder()
                        .protoSerialization(false)
                        .serviceDefinitions(ImmutableList.of(serviceDef))
                        .build();
        assertThat(transcoder).isNotNull();
        assertThat(transcoder.routes()).isNotEmpty();
        for (Route route : transcoder.routes()) {
            final TranscodingSpec spec = transcoder.findSpec(route);
            assertThat(spec).isNotNull();
            assertThat(spec.method().jsonMarshaller).isNull();
        }
    }

    @Test
    void protoSerializationDisabledSkipsJsonMarshallerForServiceDescriptors() {
        final HttpJsonTranscoder transcoder =
                new HttpJsonTranscoderBuilder()
                        .protoSerialization(false)
                        .serviceDescriptors(ImmutableList.of(
                                HttpJsonTranscodingTestServiceGrpc.getServiceDescriptor()))
                        .build();
        assertThat(transcoder).isNotNull();
        assertThat(transcoder.routes()).isNotEmpty();
        for (Route route : transcoder.routes()) {
            final TranscodingSpec spec = transcoder.findSpec(route);
            assertThat(spec).isNotNull();
            assertThat(spec.method().jsonMarshaller).isNull();
        }
    }

    @Test
    void protoSerializationEnabledBuildsJsonMarshaller() {
        final ServerServiceDefinition serviceDef =
                new HttpJsonTranscodingTestServiceImplBase() {}.bindService();
        final GrpcJsonMarshaller customMarshaller = mock(GrpcJsonMarshaller.class);
        final AtomicInteger calls = new AtomicInteger();

        final HttpJsonTranscoder transcoder =
                new HttpJsonTranscoderBuilder()
                        .protoSerialization(true)
                        .jsonMarshallerFactory(sd -> {
                            calls.incrementAndGet();
                            return customMarshaller;
                        })
                        .serviceDefinitions(ImmutableList.of(serviceDef))
                        .build();
        assertThat(transcoder).isNotNull();
        assertThat(calls.get()).isOne();
        for (Route route : transcoder.routes()) {
            final TranscodingSpec spec = transcoder.findSpec(route);
            assertThat(spec).isNotNull();
            assertThat(spec.method().jsonMarshaller).isSameAs(customMarshaller);
        }
    }

    @Test
    void grpcServiceSkipsDefaultJsonMarshallerWhenTranscodingEnabled() throws Exception {
        final ServerServiceDefinition serviceDef =
                new HttpJsonTranscodingTestServiceImplBase() {}.bindService();
        final GrpcJsonMarshaller customMarshaller = mock(GrpcJsonMarshaller.class);
        final AtomicInteger calls = new AtomicInteger();

        final GrpcService service =
                GrpcService.builder()
                           .addService(serviceDef)
                           .enableHttpJsonTranscoding(true)
                           .jsonMarshallerFactory(sd -> {
                               calls.incrementAndGet();
                               return customMarshaller;
                           })
                           .build();
        assertThat(calls.get()).isOne();

        final HttpJsonTranscodingGrpcService transcodingService =
                service.as(HttpJsonTranscodingGrpcService.class);
        assertThat(transcodingService).isNotNull();

        final Field transcoderField =
                HttpJsonTranscodingGrpcService.class.getDeclaredField("transcoder");
        transcoderField.setAccessible(true);
        final HttpJsonTranscoder transcoder =
                (HttpJsonTranscoder) transcoderField.get(transcodingService);
        assertThat(transcoder.routes()).isNotEmpty();
        for (Route route : transcoder.routes()) {
            final TranscodingSpec spec = transcoder.findSpec(route);
            assertThat(spec).isNotNull();
            assertThat(spec.method().jsonMarshaller).isNull();
        }
    }
}
