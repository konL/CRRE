import com.google.common.collect.Iterables;
import jdk.internal.org.objectweb.asm.Handle;

import java.util.Map;

public class Example {
    private static <InputT, OutputT> TransformEvaluator<ParDo.MultiOutput<InputT, OutputT>> parDo() {
        return new TransformEvaluator<ParDo.MultiOutput<InputT, OutputT>>() {
            @Override
            public void evaluate(
                    ParDo.MultiOutput<InputT, OutputT> transform, EvaluationContext context) {
                String stepName = context.getCurrentTransform().getFullName();
                DoFn<InputT, OutputT> doFn = transform.getFn();
                rejectSplittable(doFn);
                rejectStateAndTimers(doFn);
                @SuppressWarnings("unchecked")
                JavaRDD<WindowedValue<InputT>> inRDD =
                        ((BoundedDataset<InputT>) context.borrowDataset(transform)).getRDD();
                WindowingStrategy<?, ?> windowingStrategy =
                        context.getInput(transform).getWindowingStrategy();
                Accumulator<NamedAggregators> aggAccum = AggregatorsAccumulator.getInstance();
                Accumulator<SparkMetricsContainer> metricsAccum = MetricsAccumulator.getInstance();
                Map<TupleTag<?>, KV<WindowingStrategy<?, ?>, SideInputBroadcast<?>>> sideInputs =
                        TranslationUtils.getSideInputs(transform.getSideInputs(), context);
                if (transform.getSideOutputTags().size() == 0) {
                    // Don't tag with the output and filter for a single-output ParDo, as it's additional
                    // identity transforms.
                    // Also see BEAM-1737 for failures when the two versions are condensed.
                    PCollection<OutputT> output =
                            (PCollection<OutputT>)
                                    Iterables.getOnlyElement(context.getOutputs(transform)).getValue();
                    context.putDataset(
                            output,
                            new BoundedDataset<>(
                                    inRDD.mapPartitions(
                                            new DoFnFunction<>(
                                                    aggAccum,
                                                    metricsAccum,
                                                    stepName,
                                                    doFn,
                                                    context.getRuntimeContext(),
                                                    sideInputs,
                                                    windowingStrategy))));
                } else {
                    JavaPairRDD<TupleTag<?>, WindowedValue<?>> all =
                            inRDD
                                    .mapPartitionsToPair(
                                            new MultiDoFnFunction<>(
                                                    aggAccum,
                                                    metricsAccum,
                                                    stepName,
                                                    doFn,
                                                    context.getRuntimeContext(),
                                                    transform.getMainOutputTag(),
                                                    TranslationUtils.getSideInputs(transform.getSideInputs(), context),
                                                    windowingStrategy))
                                    .cache();
                    for (TaggedPValue output : context.getOutputs(transform)) {
                        @SuppressWarnings("unchecked")
                        JavaPairRDD<TupleTag<?>, WindowedValue<?>> filtered =
                                all.filter(new TranslationUtils.TupleTagFilter(output.getTag()));
                        @SuppressWarnings("unchecked")
                        // Object is the best we can do since different outputs can have different tags
                        JavaRDD<WindowedValue<Object>> values =
                                (JavaRDD<WindowedValue<Object>>) (JavaRDD<?>) filtered.values();
                        context.putDataset(output.getValue(), new BoundedDataset<>(values));
                    }
                }
            }

        }
    }
}
