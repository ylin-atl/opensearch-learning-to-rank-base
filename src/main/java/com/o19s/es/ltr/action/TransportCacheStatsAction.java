/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.action;

import com.o19s.es.ltr.action.CachesStatsAction.CachesStatsNodeResponse;
import com.o19s.es.ltr.action.CachesStatsAction.CachesStatsNodesRequest;
import com.o19s.es.ltr.action.CachesStatsAction.CachesStatsNodesResponse;
import com.o19s.es.ltr.feature.store.index.Caches;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.List;

public class TransportCacheStatsAction extends TransportNodesAction<CachesStatsNodesRequest, CachesStatsNodesResponse,
        TransportCacheStatsAction.CachesStatsNodeRequest, CachesStatsNodeResponse> {
    private final Caches caches;

    @Inject
    public TransportCacheStatsAction(Settings settings, ThreadPool threadPool,
                                        ClusterService clusterService, TransportService transportService,
                                        ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                        Caches caches) {
        super(CachesStatsAction.NAME, threadPool, clusterService, transportService,
            actionFilters, CachesStatsNodesRequest::new, CachesStatsNodeRequest::new,
            ThreadPool.Names.MANAGEMENT, CachesStatsAction.CachesStatsNodeResponse.class);
        this.caches = caches;
    }

    @Override
    protected CachesStatsNodesResponse newResponse(CachesStatsNodesRequest request, List<CachesStatsNodeResponse> responses,
                                                   List<FailedNodeException> failures) {
        return new CachesStatsNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected CachesStatsNodeRequest newNodeRequest(CachesStatsNodesRequest request) {
        return new CachesStatsNodeRequest();
    }

    @Override
    protected CachesStatsNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new CachesStatsNodeResponse(in);
    }

    @Override
    protected CachesStatsNodeResponse nodeOperation(CachesStatsNodeRequest request) {
        return new CachesStatsNodeResponse(clusterService.localNode()).initFromCaches(caches);
    }

    public static class CachesStatsNodeRequest extends BaseNodeRequest {
        public CachesStatsNodeRequest() {}

        public CachesStatsNodeRequest(StreamInput in) throws IOException {
            super(in);
        }

    }
}
