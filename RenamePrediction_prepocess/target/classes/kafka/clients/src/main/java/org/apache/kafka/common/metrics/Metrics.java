package org.apache.kafka.common.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.kafka.common.utils.SystemTime;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;


/**
 * A registry of sensors and metrics.
 * <p>
 * A metric is a named, numerical measurement. A sensor is a handle to record numerical measurements as they occur. Each
 * Sensor has zero or more associated metrics. For example a Sensor might represent message sizes and we might associate
 * with this sensor a metric for the average, maximum, or other statistics computed off the sequence of message sizes
 * that are recorded by the sensor.
 * <p>
 * Usage looks something like this:
 * 
 * <pre>
 * // set up metrics:
 * Metrics metrics = new Metrics(); // this is the global repository of metrics and sensors
 * Sensor sensor = metrics.sensor(&quot;message-sizes&quot;);
 * sensor.add(&quot;kafka.producer.message-sizes.avg&quot;, new Avg());
 * sensor.add(&quot;kafka.producer.message-sizes.max&quot;, new Max());
 * 
 * // as messages are sent we record the sizes
 * sensor.record(messageSize);
 * </pre>
 */
public class Metrics {

    private final MetricConfig config;
    private final ConcurrentMap<String, KafkaMetric> metrics;
    private final ConcurrentMap<String, Sensor> sensors;
    private final List<MetricsReporter> reporters;
    private final Time time;

    /**
     * Create a metrics repository with no metric reporters and default configuration.
     */
    public Metrics() {
        this(new MetricConfig());
    }

    /**
     * Create a metrics repository with no metric reporters and default configuration.
     */
    public Metrics(Time time) {
        this(new MetricConfig(), new ArrayList<MetricsReporter>(), time);
    }

    /**
     * Create a metrics repository with no reporters and the given default config. This config will be used for any
     * metric that doesn't override its own config.
     * @param defaultConfig The default config to use for all metrics that don't override their config
     */
    public Metrics(MetricConfig defaultConfig) {
        this(defaultConfig, new ArrayList<MetricsReporter>(0), new SystemTime());
    }

    /**
     * Create a metrics repository with a default config and the given metric reporters
     * @param defaultConfig The default config
     * @param reporters The metrics reporters
     * @param time The time instance to use with the metrics
     */
    public Metrics(MetricConfig defaultConfig, List<MetricsReporter> reporters, Time time) {
        this.config = defaultConfig;
        this.sensors = new ConcurrentHashMap<String, Sensor>();
        this.metrics = new ConcurrentHashMap<String, KafkaMetric>();
        this.reporters = Utils.notNull(reporters);
        this.time = time;
        for (MetricsReporter reporter : reporters)
            reporter.init(new ArrayList<KafkaMetric>());
    }

    /**
     * Create a sensor with the given unique name and zero or more parent sensors. All parent sensors will receive every
     * value recorded with this sensor.
     * @param name The name of the sensor
     * @param parents The parent sensors
     * @return The sensor that is created
     */
    public Sensor sensor(String name, Sensor... parents) {
        return sensor(name, null, parents);
    }

    /**
     * Create a sensor with the given unique name and zero or more parent sensors. All parent sensors will receive every
     * value recorded with this sensor.
     * @param name The name of the sensor
     * @param config A default configuration to use for this sensor for metrics that don't have their own config
     * @param parents The parent sensors
     * @return The sensor that is created
     */
    public synchronized Sensor sensor(String name, MetricConfig config, Sensor... parents) {
        Sensor s = this.sensors.get(Utils.notNull(name));
        if (s == null) {
            s = new Sensor(this, name, parents, config == null ? this.config : config, time);
            this.sensors.put(name, s);
        }
        return s;
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param measurable The measurable that will be measured by this metric
     */
    public void addMetric(String name, Measurable measurable) {
        addMetric(name, "", measurable);
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param description A human-readable description to include in the metric
     * @param measurable The measurable that will be measured by this metric
     */
    public void addMetric(String name, String description, Measurable measurable) {
        addMetric(name, description, null, measurable);
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param config The configuration to use when measuring this measurable
     * @param measurable The measurable that will be measured by this metric
     */
    public void addMetric(String name, MetricConfig config, Measurable measurable) {
        addMetric(name, "", config, measurable);
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param description A human-readable description to include in the metric
     * @param config The configuration to use when measuring this measurable
     * @param measurable The measurable that will be measured by this metric
     */
    public synchronized void addMetric(String name, String description, MetricConfig config, Measurable measurable) {
        KafkaMetric m = new KafkaMetric(new Object(),
                                        Utils.notNull(name),
                                        Utils.notNull(description),
                                        Utils.notNull(measurable),
                                        config == null ? this.config : config,
                                        time);
        registerMetric(m);
    }

    /**
     * Add a MetricReporter
     */
    public synchronized void addReporter(MetricsReporter reporter) {
        Utils.notNull(reporter).init(new ArrayList<KafkaMetric>(metrics.values()));
        this.reporters.add(reporter);
    }

    synchronized void registerMetric(KafkaMetric metric) {
        if (this.metrics.containsKey(metric.name()))
            throw new IllegalArgumentException("A metric named '" + metric.name() + "' already exists, can't register another one.");
        this.metrics.put(metric.name(), metric);
        for (MetricsReporter reporter : reporters)
            reporter.metricChange(metric);
    }

    /**
     * Get all the metrics currently maintained indexed by metric name
     */
    public Map<String, KafkaMetric> metrics() {
        return this.metrics;
    }

    /**
     * Close this metrics repository.
     */
    public void close() {
        for (MetricsReporter reporter : this.reporters)
            reporter.close();
    }

}
