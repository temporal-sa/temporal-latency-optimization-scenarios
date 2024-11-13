## scratch (TODO formalize)

### pre-reqs
```
Temporal Service (Cloud or local)
'just' cli app
```

Before running any `just` commands, ensure your environment is clean by unsetting any existing Temporal environment variables:

```bash
unset TEMPORAL_TASK_QUEUE TEMPORAL_CONNECTION_NAMESPACE TEMPORAL_CONNECTION_TARGET TEMPORAL_CONNECTION_MTLS_KEY_FILE TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE TEMPORAL_CONNECTION_WEB_PORT CALLER_API_PORT PUBLIC_WEB_URL

### web
```
cd web
npm install
poetry install
poetry shell
```

Copy `.env.template` to `.env` and update the values as needed.

Run the web server:
```
just run_web
```

Run the Temporal caller API and worker
```
just run_temporal
```