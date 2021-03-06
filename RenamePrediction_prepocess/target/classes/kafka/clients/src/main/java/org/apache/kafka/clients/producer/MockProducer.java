package org.apache.kafka.clients.producer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.internals.FutureRecordMetadata;
import org.apache.kafka.clients.producer.internals.Partitioner;
import org.apache.kafka.clients.producer.internals.ProduceRequestResult;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;


/**
 * A mock of the producer interface you can use for testing code that uses Kafka.
 * <p>
 * By default this mock will synchronously complete each send call successfully. However it can be configured to allow
 * the user to control the completion of the call and supply an optional error for the producer to throw.
 */
public class MockProducer implements Producer {

    private final Cluster cluster;
    private final Partitioner partitioner = new Partitioner();
    private final List<ProducerRecord> sent;
    private final Deque<Completion> completions;
    private boolean autoComplete;
    private Map<TopicPartition, Long> offsets;

    /**
     * Create a mock producer
     * 
     * @param cluster The cluster holding metadata for this producer
     * @param autoComplete If true automatically complete all requests successfully and execute the callback. Otherwise
     *        the user must call {@link #completeNext()} or {@link #errorNext(RuntimeException)} after
     *        {@link #send(ProducerRecord) send()} to complete the call and unblock the @{link
     *        java.util.concurrent.Future Future&lt;RecordMetadata&gt;} that is returned.
     */
    public MockProducer(Cluster cluster, boolean autoComplete) {
        this.cluster = cluster;
        this.autoComplete = autoComplete;
        this.offsets = new HashMap<TopicPartition, Long>();
        this.sent = new ArrayList<ProducerRecord>();
        this.completions = new ArrayDeque<Completion>();
    }

    /**
     * Create a new mock producer with invented metadata the given autoComplete setting.
     * 
     * Equivalent to {@link #MockProducer(Cluster, boolean) new MockProducer(null, autoComplete)}
     */
    public MockProducer(boolean autoComplete) {
        this(Cluster.empty(), autoComplete);
    }

    /**
     * Create a new auto completing mock producer
     * 
     * Equivalent to {@link #MockProducer(boolean) new MockProducer(true)}
     */
    public MockProducer() {
        this(true);
    }

    /**
     * Adds the record to the list of sent records. The {@link RecordMetadata} returned will be immediately satisfied.
     * 
     * @see #history()
     */
    @Override
    public synchronized Future<RecordMetadata> send(ProducerRecord record) {
        return send(record, null);
    }

    /**
     * Adds the record to the list of sent records.
     * 
     * @see #history()
     */
    @Override
    public synchronized Future<RecordMetadata> send(ProducerRecord record, Callback callback) {
        int partition = 0;
        if (this.cluster.partitionsFor(record.topic()) != null)
            partition = partitioner.partition(record, this.cluster);
        ProduceRequestResult result = new ProduceRequestResult();
        FutureRecordMetadata future = new FutureRecordMetadata(result, 0);
        TopicPartition topicPartition = new TopicPartition(record.topic(), partition);
        long offset = nextOffset(topicPartition);
        Completion completion = new Completion(topicPartition, offset, new RecordMetadata(topicPartition, offset), result, callback);
        this.sent.add(record);
        if (autoComplete)
            completion.complete(null);
        else
            this.completions.addLast(completion);
        return future;
    }

    /**
     * Get the next offset for this topic/partition
     */
    private long nextOffset(TopicPartition tp) {
        Long offset = this.offsets.get(tp);
        if (offset == null) {
            this.offsets.put(tp, 1L);
            return 0L;
        } else {
            Long next = offset + 1;
            this.offsets.put(tp, next);
            return offset;
        }
    }

    public List<PartitionInfo> partitionsFor(String topic) {
        return this.cluster.partitionsFor(topic);
    }

    public Map<String, Metric> metrics() {
        return Collections.emptyMap();
    }

    @Override
    public void close() {
    }

    /**
     * Get the list of sent records since the last call to {@link #clear()}
     */
    public synchronized List<ProducerRecord> history() {
        return new ArrayList<ProducerRecord>(this.sent);
    }

    /**
     * Clear the stored history of sent records
     */
    public synchronized void clear() {
        this.sent.clear();
        this.completions.clear();
    }

    /**
     * Complete the earliest uncompleted call successfully.
     * 
     * @return true if there was an uncompleted call to complete
     */
    public synchronized boolean completeNext() {
        return errorNext(null);
    }

    /**
     * Complete the earliest uncompleted call with the given error.
     * 
     * @return true if there was an uncompleted call to complete
     */
    public synchronized boolean errorNext(RuntimeException e) {
        Completion completion = this.completions.pollFirst();
        if (completion != null) {
            completion.complete(e);
            return true;
        } else {
            return false;
        }
    }

    private static class Completion {
        private final long offset;
        private final RecordMetadata metadata;
        private final ProduceRequestResult result;
        private final Callback callback;
        private final TopicPartition topicPartition;

        public Completion(TopicPartition topicPartition,
                          long offset,
                          RecordMetadata metadata,
                          ProduceRequestResult result,
                          Callback callback) {
            this.metadata = metadata;
            this.offset = offset;
            this.result = result;
            this.callback = callback;
            this.topicPartition = topicPartition;
        }

        public void complete(RuntimeException e) {
            result.done(topicPartition, e == null ? offset : -1L, e);
            if (callback != null) {
                if (e == null)
                    callback.onCompletion(metadata, null);
                else
                    callback.onCompletion(null, e);
            }
        }
    }

}
