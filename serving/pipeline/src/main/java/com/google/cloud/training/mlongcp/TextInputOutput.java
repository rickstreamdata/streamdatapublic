package com.google.cloud.training.mlongcp;

import java.text.DecimalFormat;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;

import com.google.cloud.training.mlongcp.AddPrediction.MyOptions;

@SuppressWarnings("serial")
public class TextInputOutput extends InputOutput {
  @Override
  public PCollection<Record> readInstances(Pipeline p, MyOptions options) {
    String inputFile = options.getInput();
    LOG.info("Reading data from " + inputFile);

    PCollection<Record> records = p //
        .apply("ReadLines", TextIO.read().from(inputFile)) //
        .apply("Parse", ParDo.of(new DoFn<String, Record>() {
          @ProcessElement
          public void processElement(ProcessContext c) throws Exception {
            String line = c.element();
            Record f = Record.fromCsv(line);
            if (f != null) {
              c.output(f);
            }
          }
        }));
    return records;
  }

  @Override
  public void writePredictions(PCollection<Record> instances, MyOptions options) {
    try {
      PCollection<TransactionPred> prds = addPredictionInBatches(instances);
      PCollection<String> lines = predToCsv(prds);
      lines.apply("Write", TextIO.write().to(options.getOutput() + "flightPreds").withSuffix(".csv"));
    } catch (Throwable t) {
      LOG.warn("Inference failed", t);
    }
  }

  private PCollection<String> predToCsv(PCollection<TransactionPred> preds) {
    return preds.apply("pred->csv", ParDo.of(new DoFn<TransactionPred, String>() {
      @ProcessElement
      public void processElement(ProcessContext c) throws Exception {
        TransactionPred pred = c.element();
        String csv = String.join(",", pred.flight.getFields());
        if (pred.predictedFraudProbability >= 0) {
          csv = csv + "," + new DecimalFormat("0.0").format(pred.predictedFraudProbability);
        } else {
          csv = csv + ","; // empty string -> null
        }
        c.output(csv);
      }})) //
;
  }
}
