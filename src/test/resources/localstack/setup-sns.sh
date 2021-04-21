#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2
export PAGER=

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name probation_offender_search_index_dl_queue
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name probation_offender_search_index_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/probation_offender_search_index_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:probation_offender_search_index_dl_queue\"}"}'

aws --endpoint-url=http://localhost:4566 sns create-topic --name probation_offender_events

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name probation_offender_search_event_dl_queue
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name probation_offender_search_event_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/probation_offender_search_event_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:probation_offender_search_event_dl_queue\"}"}'
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:probation_offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/probation_offender_search_event_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[ \"OFFENDER_CHANGED\"] }"}'
echo All Ready
