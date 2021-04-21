#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4566 sns publish \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:probation_offender_events \
    --message-attributes '{"eventType" : { "DataType":"String", "StringValue":"OFFENDER_CHANGED"}}' \
    --message '{"offenderId":11,"crn":"CRN11","nomsNumber":"AN1234Z"}'
