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

package com.google.cloud.streamingdemo;

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
import org.apache.beam.sdk.transforms.windowing.SlidingWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Duration;
import org.joda.time.Instant;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;

/**
 * A dataflow pipeline that computes average speeds in each lane
 * 
 * @author ewan
 *
 */
public class AverageSpeeds {

  public static interface MyOptions extends DataflowPipelineOptions {
   /* @Description("Over how long a time period should we average? (in minutes)")
    @Default.Double(60.0)
    Double getAveragingInterval();

    void setAveragingInterval(Double d);

    @Description("Simulation speedup factor. Use 1.0 if no speedup")
    @Default.Double(60.0)
    Double getSpeedupFactor();

    void setSpeedupFactor(Double d);
    */
  }

  @SuppressWarnings("serial")
  public static void main(String[] args) {
    MyOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().as(MyOptions.class);
    options.setStreaming(true);
    Pipeline p = Pipeline.create(options);

    String topic = "projects/" + options.getProject() + "/topics/streamingdemo";
    String avgSpeedTable = options.getProject() + ":demos.transactions";
/*
    // if we need to average over 60 minutes and speedup is 30x
    // then we need to average over 2 minutes of sped-up stream
    Duration averagingInterval = Duration
        .millis(Math.round(1000 * 60 * (options.getAveragingInterval() / options.getSpeedupFactor())));
    Duration averagingFrequency = averagingInterval.dividedBy(2); // 2 times
    // in
    // window
    System.out.println("Averaging interval = " + averagingInterval);
    System.out.println("Averaging freq = " + averagingFrequency);
*/
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
/*
    PCollection<KV<String, Double>> avgSpeed = currentConditions //
        .apply("TimeWindow",
            Window.into(SlidingWindows//
                .of(averagingInterval)//
                .every(averagingFrequency))) //
        .apply("BySensor", ParDo.of(new DoFn<LaneInfo, KV<String, Double>>() {
          @ProcessElement
          public void processElement(ProcessContext c) throws Exception {
            LaneInfo info = c.element();
            String key = info.getSensorKey();
            Double speed = info.getSpeed();
            c.output(KV.of(key, speed));
          }
        })) //
        .apply("AvgBySensor", Mean.perKey());
*/
    PCollection<TableRow> toBigQuery = currentConditions //
        .apply("ToBQRow", ParDo.of(new DoFn<LaneInfo, TableRow>() {
          @ProcessElement
          public void processElement(ProcessContext c) throws Exception {
            TableRow row = new TableRow();
            LaneInfo info = c.element();
            //String stationKey = c.element().getKey();
            //Double speed = c.element().getValue();
            //line = Instant.now().toString() + "," + stationKey + "," + speed; // CSV
            //LaneInfo info = LaneInfo.newLaneInfo(line);
            row.set("date", info.getTimestamp());
            row.set("trans_id", info.getTransID());
            row.set("acct_id", info.getAcctID());
            row.set("type", info.getType());
            row.set("operation", info.getOperation());
            row.set("amount", info.getAmount());
            row.set("balance", info.getBalance());
            row.set("k_symbol", info.getKSymbol());
            row.set("bank", info.getBank());
            c.output(row);
          }
        })); //
        toBigQuery.apply(BigQueryIO.writeTableRows().to(avgSpeedTable)//
            .withSchema(schema)//
            .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
            .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

    p.run();
  }
}
