/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud;
import com.google.common.io.Resources;

import java.util.ArrayList;
import java.util.List;

import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Mean;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.windowing.SlidingWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Duration;
import org.joda.time.Instant;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;

import java.io.IOException;
import java.nio.FloatBuffer;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.regression.RegressionModelEvaluator;
import org.jpmml.model.PMMLUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A dataflow pipeline that streams transaction data and perform real time prediction
 * 
 * @author Ewan
 *
 */
public class Streaming_Prediction {

  public static interface Options extends DataflowPipelineOptions {
  }


  @SuppressWarnings("serial")
  public static void main(String[] args) {
    Streaming_Prediction.Options options = PipelineOptionsFactory.fromArgs(args).withValidation().as(Streaming_Prediction.Options.class);
    options.setStreaming(true);
    Pipeline p = Pipeline.create(options);

    String topic = "projects/" + options.getProject() + "/topics/streamingdemo";
    String avgSpeedTable = options.getProject() + ":demos.transactions";

    // Build the table schema for the output table.
    List<TableFieldSchema> fields = new ArrayList<>();
    fields.add(new TableFieldSchema().setName("trans_id").setType("INTEGER"));
    fields.add(new TableFieldSchema().setName("acct_id").setType("INTEGER"));
    fields.add(new TableFieldSchema().setName("date").setType("TIMESTAMP"));
    fields.add(new TableFieldSchema().setName("type").setType("STRING"));
    fields.add(new TableFieldSchema().setName("operation").setType("STRING"));
    fields.add(new TableFieldSchema().setName("amount").setType("FLOAT"));
    fields.add(new TableFieldSchema().setName("balance").setType("FLOAT"));
    fields.add(new TableFieldSchema().setName("k_symbol").setType("STRING"));
    fields.add(new TableFieldSchema().setName("bank").setType("STRING"));
    fields.add(new TableFieldSchema().setName("fraud_ind").setType("STRING"));
    TableSchema schema = new TableSchema().setFields(fields);

    PCollection<LaneInfo> currentConditions = p //
        .apply("GetMessages", PubsubIO.readStrings().fromTopic(topic)) //
        .apply("ExtractData", ParDo.of(new DoFn<String, LaneInfo>() {
          @ProcessElement
          public void processElement(ProcessContext c) throws Exception {
            String line = c.element();
            c.output(LaneInfo.newLaneInfo(line));
          }
        }));

    PCollection<TableRow> toBigQuery = currentConditions //
        .apply("PMML Application", new PTransform<PCollection<LaneInfo>,
          PCollection<TableRow>>() {
          // define a transform that loads the PMML specification and applies it to all of the records
          public PCollection<LaneInfo> expand(PCollection<LaneInfo> input) {

            // load the PMML model
            final ModelEvaluator<RegressionModel> evaluator;
            try {
              evaluator = new RegressionModelEvaluator(
                      PMMLUtil.unmarshal(Resources.getResource("fraud_prediction.pmml").openStream()));
            }
           catch (Exception e) {
             throw new RuntimeException(e);
           }

            // create a DoFn for applying the PMML model to instances
            return input.apply("To Predictions", ParDo.of(new DoFn<LaneInfo, TableRow>() {

              @ProcessElement
              public void processElement(ProcessContext c) throws Exception {
                TableRow row = c.element();
                HashMap<FieldName, Double> inputs = new HashMap<>();            
                for (String key : row.keySet()) {                                
                    inputs.put(FieldName.create(key), Double
                      .parseDouble(row.get(key).toString()));
                }

                // get the estimate
                Double estimate = (Double)evaluator.evaluate(inputs).get(FieldName.create("fraud_ind"));

                // create a table row with the prediction
                TableRow prediction = new TableRow();            
                prediction.set("fraud_ind", estimate);
                String line = Instant.now().toString();
                LaneInfo info = LaneInfo.newLaneInfo(line);
                row.set("date", info.getTimestamp());
                row.set("trans_id", info.getTransID());
                row.set("acct_id", info.getAcctID());
                row.set("type", info.getType());
                row.set("operation", info.getOperation());
                row.set("amount", info.getAmount());
                row.set("balance", info.getBalance());
                row.set("k_symbol", info.getKSymbol());
                row.set("bank", info.getBank());
                row.set("fraud_ind",prediction);
                c.output(row);
              }
            }));
          }});//
        toBigQuery.apply(BigQueryIO.writeTableRows().to(avgSpeedTable)//
            .withSchema(schema)//
            .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
            .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

    p.run();
  }
}