package com.o19s.es.ltr.feature.store;

import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;

public class StringHelper {
    public static String toString(XContentBuilder xContentBuilder) {
        return BytesReference.bytes(xContentBuilder).utf8ToString();
    }
}
