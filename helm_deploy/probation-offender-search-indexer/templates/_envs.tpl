    {{/* vim: set filetype=mustache: */}}
{{/*
Environment variables for web and worker containers
*/}}
{{- define "deployment.envs" }}
env:
  - name: SERVER_PORT
    value: "{{ .Values.image.port }}"

  - name: JAVA_OPTS
    value: "{{ .Values.env.JAVA_OPTS }}"

  - name: OAUTH_ENDPOINT_URL
    value: "{{ .Values.env.OAUTH_ENDPOINT_URL }}"

  - name: COMMUNITY_ENDPOINT_URL
    value: "{{ .Values.env.COMMUNITY_ENDPOINT_URL }}"

  - name: SPRING_PROFILES_ACTIVE
    value: "logstash"

  - name: APPINSIGHTS_INSTRUMENTATIONKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: APPLICATIONINSIGHTS_CONNECTION_STRING
    value: "InstrumentationKey=$(APPINSIGHTS_INSTRUMENTATIONKEY)"

  - name: OAUTH_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: OAUTH_CLIENT_ID

  - name: OAUTH_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: OAUTH_CLIENT_SECRET

  - name: EVENT_SQS_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: pose-sqs-instance-output
        key: access_key_id

  - name: EVENT_SQS_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: pose-sqs-instance-output
        key: secret_access_key

  - name: EVENT_SQS_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: pose-sqs-instance-output
        key: sqs_name

  - name: EVENT_SQS_AWS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: pose-sqs-dl-instance-output
        key: access_key_id

  - name: EVENT_SQS_AWS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: pose-sqs-dl-instance-output
        key: secret_access_key

  - name: EVENT_SQS_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: pose-sqs-dl-instance-output
        key: sqs_name

  - name: INDEX_SQS_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: poi-idx-sqs-instance-output
        key: access_key_id

  - name: INDEX_SQS_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: poi-idx-sqs-instance-output
        key: secret_access_key

  - name: INDEX_SQS_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: poi-idx-sqs-instance-output
        key: sqs_name

  - name: INDEX_SQS_AWS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: poi-idx-sqs-dl-instance-output
        key: access_key_id

  - name: INDEX_SQS_AWS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: poi-idx-sqs-dl-instance-output
        key: secret_access_key

  - name: INDEX_SQS_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: poi-idx-sqs-dl-instance-output
        key: sqs_name

  - name: ELASTICSEARCH_PORT
    value: "9200"

  - name: ELASTICSEARCH_SCHEME
    value: "http"

  - name: ELASTICSEARCH_HOST
    value: "es-proxy"

{{- end -}}
