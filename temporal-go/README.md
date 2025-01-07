### Latency-Optimization Sample

This sample demonstrates some techniques about how to do latency optimization for a Temporal workflow.

By utilizing Update-with-Start, a client can start a new workflow and synchronously receive 
a response mid-workflow, while the workflow continues to run to completion.

By utilizing local activities you can optimize further.

### Steps to run this sample:
1) Run a [Temporal service](https://github.com/temporalio/samples-go/tree/main/#how-to-use) or set up env variables to connect to cloud
2) Run the following command to start the worker
```bash
cd temporal-go/
go run worker/main.go
```
3) Run the following command to start the example
```bash
cd temporal-go/
go run starter/main.go
```

#### Alternatively instructions with just 
1) Run a [Temporal service](https://github.com/temporalio/samples-go/tree/main/#how-to-use) or set up env variables to connect to cloud
2) Run the following command to start the worker
```bash
just run_temporal_go
```
3) Run the following command to start the example
```bash
just run_temporal_gostarter
```