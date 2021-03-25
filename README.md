# probation-offender-search-indexer

[![CircleCI](https://circleci.com/gh/ministryofjustice/probation-offender-search-indexer/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/probation-offender-search-indexer)
[![Docker](https://quay.io/repository/hmpps/probation-offender-search-indexer/status)](https://quay.io/repository/hmpps/probation-offender-search-indexer/status)
[![API docs](https://img.shields.io/badge/API_docs_(needs_VPN)-view-85EA2D.svg?logo=swagger)](https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/swagger-ui.html)

The purpose of this service is two fold:
* Keep the Elastic Search  (ES) probation index up to date with changes from Delius
* Rebuild the index when required without an outage

## Architecture and design

### Offender updates

This service subscribes to the probation offender events, specifically the `OFFENDER_CHANGED` event

When this event is received the latest offender record is retrieved via the `community-api` and upserted into the offender index.

### Index rebuilds

This service maintains two indexes `probation-search-green` and `probation-search-blue` also know in the code as `BLUE` and `GREEN`.

In normal running one of these indexes will be "active" while the other is dormant and not in use. 

When we are ready to rebuild the index the "other" non active index is transitioned into a `BUILDING` state.

```
    PUT /probation-index/build-index
```
 
The entire Delius offender base is retrieved and over several hours the other index is fully populated. Once the operator realises the other index is fully rebuilt the active index is switched with 
```
    PUT /probation-index/mark-complete
```

#### Index switch

Given the state of the each index is itself held in ES under the `offender-index-status` index with a single "document" when the BLUE/GREEN indexes switch there are actually two changes:
* The document in `offender-index-status` to indicate which index is currently active
* The ES alias `offender` is switched to point at the active index. This means external clients can safely use the `offender` index/alias without any knowledge of the the BLUE/GREEN indexes. 

Indexes can be switched without rebuilding, if they are both marked as `COMPLETE`
```
    PUT /probation-index/switch-index
```

If for some reason we need to switch indexes when the non active index is marked as `BUILDING` or `CANCELLED` we can force the indexes to switch with
```
    PUT /probation-index/switch-index?force=true
```
If the non active index is marked as `BUILDING` the indexing will be cancelled before the indexes are switched.

### Running

`localstack` is used to emulate the AWS SQS and Elastic Search service. When running the integration test this will be started automatically. If you want the tests to use an already running version of `localstack` run the tests with the environment `AWS_PROVIDER=localstack`. This has the benefit of running the test quicker without the overhead of starting the `localstack` container.

Any commands in `localstack/setup-sns.sh` and `localstack/setup-es.sh` will be run when `localstack` starts, so this should contain commands to create the appropriate queues.

Running all services locally:
```bash
docker-compose up 
```
Since localstack persists data between runs it maybe neccessary to delete the localstack temporary data:

Mac
```bash
rm -rf $TMPDIR/data
```
Linux
```bash
sudo rm -rf /tmp/localstack
```

*Please note the above will not work on a Mac using docker desktop since the docker network host mode is not supported on a Mac*

For a Mac it recommended running all components *except* probation-offender-search-indexer (see below) then running probation-offender-search-indexer externally:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun 
```

Queues and topics and an ES instance will automatically be created when the `localstack` container starts.

Running all services except this application (hence allowing you to run this in the IDE)

```bash
docker-compose up --scale probation-offender-search-indexer=0 
```

Depending on the speed of your machine when running all services you may need to scale `probation-offender-search-indexer=0` until localstack starts. This is a workaround for an issue whereby Spring Boot gives up trying to connect to SQS when the services first starts up.

### Running tests

#### Test containers

`./gradlew test` will run all tests and will by default use test containers to start any required docker containers, e.g localstack
Note that TestContainers will start Elastic Search in its own container rather than using the one built into localstack.

#### External localstack

`AWS_PROVIDER=localstack ./gradlew test` will override the default behaviour and will expect localstack to already be started externally. In this mode the following services must be started `sqs,sns,es`

`docker-compose up localstack` will start the required AWS services.  

## Regression test

Recommended regression tests is as follows:

* A partial build of index - see the `Rebuilding an index` instructions below. The rebuild does not need to be completed but expect the info to show something like this:
```
   "index-status": {
     "currentIndex": "GREEN",
     "currentIndexStartBuildTime": "2020-07-27T14:53:35",
     "currentIndexEndBuildTime": "2020-07-27T16:53:35",
     "currentIndexState": "COMPLETE",
     "otherIndexStartBuildTime": 2020-07-28T14:53:35,
     "otherIndexEndBuildTime": null,
     "otherIndexState": "BUILDING",
     "otherIndex": "BLUE"
   },
   "index-size": {
     "GREEN": 2010111,
     "BLUE": 256
   },
   "offender-alias": "probation-search-green",
   "index-queue-backlog": "3012765"
```
So long as the index is being populated and the ` "index-queue-backlog"` figure is decreasing after some time (e.g. 10 minutes) it demonstrates the application is working.

Check the health endpoint to show the Index DLQ is not building up with errors e.g: `https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/health`

``` 
    "indexQueueHealth": {
      "status": "UP",
      "details": {
        "MessagesOnQueue": 41834,
        "MessagesInFlight": 4,
        "dlqStatus": "UP",
        "MessagesOnDLQ": 0
      }
    }
```
would be a valid state since the `MessagesOnDLQ` would be zero

The build can either be left to run or cancelled using the following endpoint 


 ``` 
curl --location --request PUT 'https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/probation-index/cancel-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>>'

 ```  


## Support

### Raw Elastic Search access

Access to the raw Elastic Search indexes is only possible from the Cloud Platform `probation-offender-search` family of namespaces. 

For instance 

```
curl http://es-proxy:9200/_cat/indices
```
in any environment would return a list all indexes e.g.
```
green open probation-search-green l4Pf4mFcQ2eVhnqhNnCVGg 5 1 683351 366 311.1mb   155mb
green open probation-search-blue  hsuf4mFcQ2eVhnqhNnKjsw 5 1 683351 366 311.1mb   155mb
green open offender-index-status  yEhaJorgRemmWMONJjfHoQ 1 1      1   0  11.4kb   5.7kb
green open .kibana_1              jddPd_-XRBiqxOXGudoj7Q 1 1      0   0    566b    283b
```

### Rebuilding an index

To rebuild an index the credentials used must have the ROLE `PROBATION_INDEX` therefore it is recommend to use client credentials with the `ROLE_PROBATION_INDEX` added and pass in your username when getting a token.
In the test and local dev environments the `prisoner-offender-search-client` has conveniently been given the `ROLE_PROBATION_INDEX`.

The rebuilding of the index can be sped up by increasing the number of pods handling the reindex e.g.

```
kubectl -n probation-offender-search-dev scale --replicas=8 deployment/probation-offender-search-indexer
``` 
After obtaining a token for the environment invoke the reindex with a cUrl command or Postman e.g.

```
curl --location --request PUT 'https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/probation-index/build-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>>'
``` 

For production environments where access is blocked by inclusion lists this will need to be done from within a Cloud Platform pod

Next monitor the progress of the rebuilding via the info endpoint e.g. https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/info
This will return details like the following:

```
   "index-status": {
     "currentIndex": "NONE",
     "currentIndexStartBuildTime": null,
     "currentIndexEndBuildTime": null,
     "currentIndexState": "ABSENT",
     "otherIndexStartBuildTime": "2020-07-27T14:53:35",
     "otherIndexEndBuildTime": null,
     "otherIndexState": "BUILDING",
     "otherIndex": "GREEN"
   },
   "index-size": {
     "GREEN": 8,
     "BLUE": -1
   },
   "offender-alias": "",
   "index-queue-backlog": "260133"
```
 
 when `"index-queue-backlog": "0"` has reached zero then all indexing messages have been processed. Check that the dead letter queue is empty via the health check e.g https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/health
 This should show the queues DLQ count at zero, e.g.
 ``` 
    "indexQueueHealth": {
      "status": "UP",
      "details": {
        "MessagesOnQueue": 0,
        "MessagesInFlight": 0,
        "dlqStatus": "UP",
        "MessagesOnDLQ": 0
      }
    },
 ```
  
 The indexing is ready to marked as complete using another call to the service e.g
 
 ``` 
curl --location --request PUT 'https://probation-search-indexer-dev.hmpps.service.justice.gov.uk/probation-index/mark-complete' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>>'

 ```  

One last check of the info endpoint should confirm the new state, e.g.

```
   "index-status": {
     "currentIndex": "GREEN",
     "currentIndexStartBuildTime": "2020-07-27T14:53:35",
     "currentIndexEndBuildTime": "2020-07-27T16:53:35",
     "currentIndexState": "COMPLETE",
     "otherIndexStartBuildTime": null,
     "otherIndexEndBuildTime": null,
     "otherIndexState": "ABSENT",
     "otherIndex": "BLUE"
   },
   "index-size": {
     "GREEN": 2010111,
     "BLUE": -1
   },
   "offender-alias": "probation-search-green",
   "index-queue-backlog": "0"
```

Pay careful attention to `"offender-alias": "probation-search-green"` - this shows the actual index being used by clients.

### Deleting indexes in test

In rare circumstances where you need to test rebuilding indexes from scratch with an outage it is possible to delete the indexes as follows:

```
curl -X DELETE es-proxy:9200/probation-search-green/_alias/offender?pretty
curl -X DELETE es-proxy:9200/probation-search-green?pretty
curl -X DELETE es-proxy:9200/probation-search-blue?pretty
curl -X DELETE es-proxy:9200/offender-index-status?pretty
```

### Alias switch failure

It is possible for the green/blue index switching to become out of sync with the index the `offender` alias is pointing at. 
That could happen if the ES operation to update the `offender-index-status` succeeds but the ES operation to update the alias fails.

In that instance the alias must be switched manually. 

The current state of the alias can be seen from 
```
curl http://es-proxy:9200/_cat/aliases
```
e.g.
```
.kibana  .kibana_1              - - - -
offender probation-search-green - - - -
```
shows that the current index is `probation-search-green`

To update the alias:
```
curl -X POST "es-proxy:9200/_aliases?pretty" -H 'Content-Type: application/json' -d'
{
  "actions" : [
    { "add" : { "index" : "probation-search-blue", "alias" : "offender" } }
  ]
}
'
```
And then delete old alias
```
curl -X DELETE "es-proxy:9200/probation-search-green/_alias/offender?pretty"

```

Switching the alias will not update the service's understanding of what index is currently active. So that would need to be updated manually if the alias was updated:

```
curl -X PUT "http://es-proxy:9200/offender-index-status/_doc/STATUS"  -H 'Content-Type: application/json'  -d'
{
    "_class": "uk.gov.justice.digital.hmpps.indexer.model.IndexStatus",
    "id": "STATUS",
    "currentIndex": "BLUE",
    "currentIndexStartBuildTime": "2021-03-12T16:30:14",
    "currentIndexEndBuildTime": "2021-03-12T16:30:19",
    "currentIndexState": "COMPLETED",
    "otherIndexStartBuildTime": "2020-11-24T16:49:41",
    "otherIndexEndBuildTime": "2020-11-25T02:40:17",
    "otherIndexState": "COMPLETED"
}'
```

##Restore from a snapshot (if both indexes have become corrupt/empty)

If we are restoring from the snapshot it means that the current index and other index are broken, we need to delete them to be able to restore from the snapshot.
Every night we have a scheduled job that takes the snapshot of the whole cluster which is called `latest` and this should be restored

1. To restore we need to port-forward to the es instance (replace NAMESPACE with the affected namespace)

   ```kubectl -n <NAMESPACE> port-forward svc/es-proxy 9200:9200```

2. Delete the current indexes

   ```curl -XDELETE 'http://localhost:9200/_all'```

3. Then we can start the restore (SNAPSHOT_NAME for the overnight snapshot is `latest`)

   ```curl -XPOST 'http://localhost:9200/_snapshot/<NAMESPACE>/<SNAPSHOT_NAME>/_restore' --data '{"include_global_state": true}'```

The `include_global_state: true` is set true so that we copy the global state of the cluster snapshot over. The default for restoring,
however, is `include_global_state: False`. If only restoring a single index, it could be bad to overwrite the global state but as we are
restoring the full cluster we set it to true

###To view the state of the indexes while restoring from a snapshot

####cluster health

```curl -XGET 'http://localhost:9200/_cluster/health'```

The cluster health status is: green, yellow or red. On the shard level, a red status indicates that the specific shard is not allocated in the cluster, yellow means that the primary shard is allocated but replicas are not, and green means that all shards are allocated. The index level status is controlled by the worst shard status. The cluster status is controlled by the worst index status.

####Shards
```curl -XGET 'http://localhost:9200/_cat/shards'```

The shards command is the detailed view of what nodes contain which shards. It will tell you if it’s a primary or replica, the number of docs, the bytes it takes on disk, and the node where it’s located.

####Recovery
```curl -XGET 'http://localhost:9200/_cat/recovery'```

Returns information about ongoing and completed shard recoveries


###To take a manual snapshot, perform the following steps:

1. You can't take a snapshot if one is currently in progress. To check, run the following command:

   ```curl -XGET 'http://localhost:9200/_snapshot/_status'```
2. Run the following command to take a manual snapshot:

   ```curl -XPUT 'http://localhost:9200/_snapshot/<NAMESPACE>/snapshot-name'```

you can now use the restore commands above to restore the snapshot if needed

####to remove a snapshot
```curl -XDELETE 'http://localhost:9200/_snapshot/<NAMESPACE>/snapshot-name'```

###Other command which will help when looking at restoring a snapshot

To see all snapshot repositories, run the following command (normally there will only be one, as we have one per namespace):

```curl -XGET 'http://localhost:9200/_snapshot?pretty'```

To see all snapshots for the namespace run the following command:

```curl -XGET 'http://localhost:9200/_snapshot/<NAMESPACE>/_all?pretty'```

### Useful App Insights Queries
####General logs (filtering out the offender update)
``` kusto
traces
| where cloud_RoleName == "probation-offender-search-indexer"
| where message !startswith "Updating offender"
| order by timestamp desc
```

####General logs including spring startup
``` kusto
traces
| where cloud_RoleInstance startswith "probation-offender-search-indexer"
| order by timestamp desc
```

####Completed index pages and processing time
``` kusto
customEvents
| extend sortkey = toint(customDimensions.offenderPage)
| where cloud_RoleName == "probation-offender-search-indexer"
| where name == "BUILD_PAGE_MSG"
| order by sortkey desc
```

####Index page boundaries (start and end of each page)
``` kusto
customEvents
| extend sortkey = toint(customDimensions.page)
| where cloud_RoleName == "probation-offender-search-indexer"
| where name == "BUILD_PAGE_BOUNDARY"
| order by sortkey desc
```

####Offender updates
``` kusto
customEvents
| where cloud_RoleName == "probation-offender-search-indexer"
| where name == "OFFENDER_UPDATED"
| order by sortkey desc
```

####Queue admin events
``` kusto
customEvents
| where cloud_RoleName == "probation-offender-search-indexer"
| where name not in ("OFFENDER_UPDATED", "BUILD_INDEX_MSG", "BUILD_PAGE_MSG", "BUILD_PAGE_BOUNDARY")
| order by sortkey desc
```

####Interesting exceptions
``` kusto
exceptions
| where cloud_RoleName == "probation-offender-search-indexer"
| where operation_Name != "GET /health"
| where customDimensions !contains "health"
| where details !contains "HealthCheck"
| order by timestamp desc
```

####Indexing requests
``` kusto
requests
| where cloud_RoleName == "probation-offender-search-indexer"
//| where timestamp between (todatetime("2020-08-06T18:20:00") .. todatetime("2020-08-06T18:22:00"))
| order by timestamp desc 
```

####Community API requests during index build
``` kusto
requests
| where cloud_RoleName == "community-api"
//| where timestamp between (todatetime("2020-08-06T18:20:00") .. todatetime("2020-08-06T18:22:00"))
| where name == "GET OffendersResource/getOffenderIds"
| order by timestamp desc 
```
