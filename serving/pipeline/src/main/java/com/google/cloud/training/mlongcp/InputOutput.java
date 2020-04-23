package com.google.cloud.training.mlongcp;

import java.io.Serializable;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.training.mlongcp.AddPrediction.MyOptions;

@SuppressWarnings("serial")
public abstract class InputOutput implements Serializable {
  protected static final Logger LOG = LoggerFactory.getLogger(InputOutput.class);
  public abstract PCollection<Record> readInstances(Pipeline p, MyOptions options);
  public abstract void writePredictions(PCollection<Record> instances, MyOptions options);

  private static class CreateBatch extends DoFn<Record, KV<String, Record>> {
    private static final int NUM_BATCHES = 2;
    @ProcessElement
    public void processElement(ProcessContext c) throws Exception {
      Record f = c.element();
      String key = " " + (System.identityHashCode(f) % NUM_BATCHES);
      c.output(KV.of(key, f));
    }
  }
  
  public static PCollection<TransactionPred> addPredictionInBatches(PCollection<Record> instances) {
    return instances //
        .apply("CreateKeys", ParDo.of(new CreateBatch())) //
        .apply("BatchByKey", GroupByKey.<String, Record> create()) // within window
        .apply("Inference", ParDo.of(new DoFn<KV<String, Iterable<Record>>, TransactionPred>() {
          @ProcessElement
          public void processElement(ProcessContext c) throws Exception {
            Iterable<Record> instances = c.element().getValue();

            // do ml inference for wheelsoff events, but as batch
            double[] result = TransactionFraudMLService.mock_batchPredict(instances);
            int resultno = 0;
            for (Record f : instances) {
              double ontime = result[resultno++];
              c.output(new TransactionPred(f, ontime));
            }
          }
        }));
  }
  


  @SuppressWarnings("unused")
  private static PCollection<TransactionPred> addPredictionOneByOne(PCollection<Record> instances) {
    return instances //
        .apply("Inference", ParDo.of(new DoFn<Record, TransactionPred>() {
          @ProcessElement
          public void processElement(ProcessContext c) throws Exception {
            Record f = c.element();
            double predfd = TransactionFraudMLService.predict(f, -5.0);
            c.output(new TransactionPred(f, predfd));
          }
        }));
  }
}
