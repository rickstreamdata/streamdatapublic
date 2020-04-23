#install packages needed for decision tree
library(base)
install.packages("multcomp")
library(multcomp)
install.packages("party")
library(party)

df <- read.csv("~/GCP Training/Sandbox/Streamingdemo/transaction_streaming_data_ml.csv")
#anyNA(df)
#sapply(df, class)
#Decision Tree

tree_df <- ctree(fraud_ind ~ amount + balance , data = df)
plot(tree_df)

r2pmml(tree_df, "fraud_prediction.pmml")


#lm <- lm(fraud_ind ~ ., data = df)
#summary(lm)

#cor(df$fraud_ind, predict(lm, df))
#mean(abs(df$fraud_ind - predict(lm, df))) sqrt(mean(abs(df$fraud_ind - predict(lm, df)^2)))
#library(r2pmml)
#r2pmml(lm, "fraud_prediction.pmml")
