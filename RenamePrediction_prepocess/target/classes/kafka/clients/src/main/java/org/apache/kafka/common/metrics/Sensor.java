package org.apache.kafka.common.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.kafka.common.metrics.CompoundStat.NamedMeasurable;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;


/**
 * A sensor applies a continuous sequence of numerical values to a set of associated metrics. For example a sensor on
 * message size would record a sequence of message sizes using the {@link #record(double)} api and would maintain a set
 * of metrics about request sizes such as the average or max.
 */
public final class Sensor {

    private final Metrics registry;
    private final String name;
    private final Sensor[] parents;
    private final List<Stat> stats;
    private final List<KafkaMetric> metrics;
    private final MetricConfig config;
    private final Time time;

    Sensor(Metrics registry, String name, Sensor[] parents, MetricConfig config, Time time) {
        super();
        this.registry = registry;
        this.name = Utils.notNull(name);
        this.parents = parents;
        this.metrics = new ArrayList<KafkaMetric>();
        this.stats = new ArrayList<Stat>();
        this.config = config;
        this.time = time;
        checkForest(new HashSet<Sensor>());
    }

    /* Validate that this sensor doesn't end up referencing itself */
    private void checkForest(Set<Sensor> sensors) {
        if (!sensors.add(this))
            throw new IllegalArgumentException("Circular dependency in sensors: " + name() + " is its own parent.");
        for (int i = 0; i < parents.length; i++)
            parents[i].checkForest(sensors);
    }

    /**
     * The name this sensor is registered with. This name will be unique among all registered sensors.
     */
    public String name() {
        return this.name;
    }

    /**
     * Record an occurrence, this is just short-hand for {@link #record(double) record(1.0)}
     */
    public void record() {
        record(1.0);
    }

    /**
     * Record a value with this sensor
     * @param value The value to record
     * @throws QuotaViolationException if recording this value moves a metric beyond its configured maximum or minimum
     *         bound
     */
    public void record(double value) {
        record(value, time.nanoseconds());
    }

    private void record(double value, long time) {
        synchronized (this) {
            // increment all the stats
            for (int i = 0; i < this.stats.size(); i++)
                this.stats.get(i).record(config, value, time);
            checkQuotas(time);

        }
        for (int i = 0; i < parents.length; i++)
            parents[i].record(value, time);
    }

    private void checkQuotas(long time) {
        for (int i = 0; i < this.metrics.size(); i++) {
            KafkaMetric metric = this.metrics.get(i);
            MetricConfig config = metric.config();
            if (config != null) {
                Quota quota = config.quota();
                if (quota != null)
                    if (!quota.acceptable(metric.value(time)))
                        throw new QuotaViolationException("Metric " + metric.name() + " is in violation of its quota of " + quota.bound());
            }
        }
    }

    /**
     * Register a compound statistic with this sensor with no config override
     */
    public void add(CompoundStat stat) {
        add(stat, null);
    }

    /**
     * Register a compound statistic with this sensor which yields multiple measurable quantities (like a histogram)
     * @param stat The stat to register
     * @param config The configuration for this stat. If null then the stat will use the default configuration for this
     *        sensor.
     */
    public synchronized void add(CompoundStat stat, MetricConfig config) {
        this.stats.add(Utils.notNull(stat));
        for (NamedMeasurable m : stat.stats()) {
            KafkaMetric metric = new KafkaMetric(this, m.name(), m.description(), m.stat(), config == null ? this.config : config, time);
            this.registry.registerMetric(metric);
            this.metrics.add(metric);
        }
    }

    /**
     * Add a metric with default configuration and no description. Equivalent to
     * {@link Sensor#add(String, String, MeasurableStat, MetricConfig) add(name, "", stat, null)}
     * 
     */
    public void add(String name, MeasurableStat stat) {
        add(name, stat, null);
    }

    /**
     * Add a metric with default configuration. Equivalent to
     * {@link Sensor#add(String, String, MeasurableStat, MetricConfig) add(name, description, stat, null)}
     * 
     */
    public void add(String name, String description, MeasurableStat stat) {
        add(name, description, stat, null);
    }

    /**
     * Add a metric to this sensor with no description. Equivalent to
     * {@link Sensor#add(String, String, MeasurableStat, MetricConfig) add(name, "", stat, config)}
     * @param name
     * @param stat
     * @param config
     */
    public void add(String name, MeasurableStat stat, MetricConfig config) {
        add(name, "", stat, config);
    }

    /**
     * Register a metric with this sensor
     * @param name The name of the metric
     * @param description A description used when reporting the value
     * @param stat The statistic to keep
     * @param config A special configuration for this metric. If null use the sensor default configuration.
     */
    public synchronized void add(String name, String description, MeasurableStat stat, MetricConfig config) {
        KafkaMetric metric = new KafkaMetric(this,
                                             Utils.notNull(name),
                                             Utils.notNull(description),
                                             Utils.notNull(stat),
                                             config == null ? this.config : config,
                                             time);
        this.registry.registerMetric(metric);
        this.metrics.add(metric);
        this.stats.add(stat);
    }

    synchronized List<KafkaMetric> metrics() {
        return Collections.unmodifiableList(this.metrics);
    }

}
