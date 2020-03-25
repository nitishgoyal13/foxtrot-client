package io.appform.foxtrot.client;

import io.appform.foxtrot.client.cluster.FoxtrotClusterFactory;
import io.appform.foxtrot.client.cluster.IFoxtrotCluster;
import io.appform.foxtrot.client.senders.HttpAsyncEventSender;
import io.appform.foxtrot.client.senders.HttpSyncEventSender;
import io.appform.foxtrot.client.senders.QueuedSender;
import io.appform.foxtrot.client.serialization.EventSerializationHandler;
import io.appform.foxtrot.client.serialization.JacksonJsonSerializationHandler;
import io.appform.foxtrot.client.util.TypeChecker;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class FoxtrotClient {
    private final IFoxtrotCluster foxtrotCluster;
    private final EventSender eventSender;

    public FoxtrotClient(FoxtrotClientConfig config) throws Exception {
        this(config, JacksonJsonSerializationHandler.INSTANCE);
    }

    public FoxtrotClient(FoxtrotClientConfig config, EventSerializationHandler serializationHandler) throws Exception {
        this.foxtrotCluster = new FoxtrotClusterFactory(config).getCluster(config.getEndpointType());
        Preconditions.checkNotNull(config.getTable());
        Preconditions.checkNotNull(config.getClientType());
        Preconditions.checkNotNull(config.getHost());
        Preconditions.checkArgument(config.getPort() >= 80);
        switch (config.getClientType()) {
            case sync:
                this.eventSender = new HttpSyncEventSender(config, foxtrotCluster, serializationHandler);
                break;
            case async:
                this.eventSender = new HttpAsyncEventSender(config, foxtrotCluster, serializationHandler);
                break;
            case queued:
                List<String> messages = new ArrayList<>();
                if (StringUtils.isEmpty(config.getQueuePath())) {
                    messages.add(String.format("table=%s empty_local_queue_path", config.getTable()));
                }
                if (config.getBatchSize() <= 1) {
                    messages.add(String.format("table=%s invalid_batchSize must_be_greater_than_1", config.getTable()));
                }
                if (!messages.isEmpty()) {
                    throw new Exception(messages.toString());
                }
                this.eventSender = new QueuedSender(new HttpSyncEventSender(config, foxtrotCluster, serializationHandler),
                        serializationHandler,
                        config.getQueuePath(),
                        config.getBatchSize()
                );
                break;
            default:
                throw new Exception(
                        String.format("table=%s invalid_client_type type_provided=%s allowed_types=%s",
                                config.getTable(),
                                config.getClientType(),
                                StringUtils.join(ClientType.values(), ",")
                        )
                );
        }
    }

    public FoxtrotClient(IFoxtrotCluster foxtrotCluster, EventSender eventSender) {
        this.foxtrotCluster = foxtrotCluster;
        this.eventSender = eventSender;
    }

    public void send(Document document) throws Exception {
        Preconditions.checkNotNull(document.getData());
        Preconditions.checkArgument(!TypeChecker.isPrimitive(document.getData()));
        eventSender.send(document);
    }

    public void send(final String tableName, Document document) throws Exception {
        Preconditions.checkNotNull(document.getData());
        Preconditions.checkArgument(!TypeChecker.isPrimitive(document.getData()));
        Preconditions.checkNotNull(tableName);
        eventSender.send(tableName, document);
    }

    public void send(final String tableName, List<Document> documents) throws Exception {
        Preconditions.checkNotNull(tableName);
        eventSender.send(tableName, documents);
    }

    public void send(List<Document> documents) throws Exception {
        eventSender.send(documents);
    }

    void close() throws Exception {
        eventSender.close();
        foxtrotCluster.stop();
    }
}