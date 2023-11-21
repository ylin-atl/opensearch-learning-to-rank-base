package com.o19s.es.ltr.feature.store.index;

import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.cbor.CborXContent;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.common.xcontent.smile.SmileXContent;
import org.opensearch.common.xcontent.yaml.YamlXContent;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

public class XContentFactoryHelper {
    public static XContentBuilder contentBuilder(XContentType type) throws IOException {
        if (type == XContentType.JSON) {
            return JsonXContent.contentBuilder();
        } else if (type == XContentType.SMILE) {
            return SmileXContent.contentBuilder();
        } else if (type == XContentType.YAML) {
            return YamlXContent.contentBuilder();
        } else if (type == XContentType.CBOR) {
            return CborXContent.contentBuilder();
        }
        throw new IllegalArgumentException("No matching content type for " + type);
    }
}
