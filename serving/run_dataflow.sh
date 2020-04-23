#!/bin/bash

PROJECTID=$(gcloud config get-value project)

cd pipeline
bq mk transaction
bq rm -f transaction.predictions

mvn compile exec:java \
 -Dexec.mainClass=com.google.cloud.training.mlongcp.AddPrediction \
 -Dexec.args="--realtime --input=streamingdemo --output=transaction.predictions --project=fs-engineering-sandbox-422774"