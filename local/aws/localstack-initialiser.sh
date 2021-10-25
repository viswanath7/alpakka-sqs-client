#!/usr/bin/env bash
# Run the app using local stack SQS and S3

set -euo pipefail
echo "========================================================="
echo " Configuring SQS by pre-creating queues ..."
echo "========================================================="

LOCALSTACK_HOST=localhost
AWS_REGION=eu-west-1
LOCALSTACK_DUMMY_ID=000000000000

get_all_queues() {
    awslocal --endpoint-url=http://${LOCALSTACK_HOST}:4566 sqs list-queues
}

create_queue() {
    local QUEUE_NAME_TO_CREATE=$1
    awslocal --endpoint-url=http://${LOCALSTACK_HOST}:4566 sqs create-queue --queue-name ${QUEUE_NAME_TO_CREATE}
}

guess_queue_arn_from_name() {
    local LEADS_QUEUE_NAME=$1
    echo "arn:aws:sns:${AWS_REGION}:${LOCALSTACK_DUMMY_ID}:$LEADS_QUEUE_NAME"
}

create_dynamo_table() {
  local TABLE_NAME=$1
  local ATTRIBUTE_NAME=$2
  awslocal dynamodb create-table --table-name ${TABLE_NAME}  --attribute-definitions AttributeName=${ATTRIBUTE_NAME},AttributeType=S --key-schema AttributeName=${ATTRIBUTE_NAME},KeyType=HASH --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5
}

get_dynamo_tables() {
  awslocal --endpoint-url=http://${LOCALSTACK_HOST}:4566 dynamodb list-tables
}

LEADS_QUEUE_NAME="leads-incoming-queue"
echo "Creating queue $LEADS_QUEUE_NAME ..."
QUEUE_URL=$(create_queue ${LEADS_QUEUE_NAME})
echo "Created queue: $QUEUE_URL ..."
QUEUE_ARN=$(guess_queue_arn_from_name $LEADS_QUEUE_NAME)
echo "ARN of created queue: $QUEUE_ARN"

echo "Available queues are:"
echo "$(get_all_queues)"


TABLE_NAME="my_dynamo_table"
ATTRIBUTE_NAME="myAttributeName"
echo "Creating dynamoDb table ..."
create_dynamo_table $TABLE_NAME $ATTRIBUTE_NAME
echo "DynamoDb created:"
echo "$(get_dynamo_tables)"