# NOTE: If environment variables aren't being picked up from .env, run this first:
# unset TEMPORAL_TASK_QUEUE TEMPORAL_CONNECTION_NAMESPACE TEMPORAL_CONNECTION_TARGET TEMPORAL_CONNECTION_MTLS_KEY_FILE TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE TEMPORAL_CONNECTION_WEB_PORT CALLER_API_PORT PUBLIC_WEB_URL

set dotenv-load := true

codegen:
    npm run codegen --prefix web

kill_temporal:
    -@killall temporal

local_temporal:
    if test ! -n "$(lsof -i :7233)"; then $(temporal server start-dev); fi

run_web:
    @echo "To use the .env file, first unset TEMPORAL_TASK_QUEUE TEMPORAL_CONNECTION_NAMESPACE TEMPORAL_CONNECTION_TARGET TEMPORAL_CONNECTION_MTLS_KEY_FILE TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE TEMPORAL_CONNECTION_WEB_PORT CALLER_API_PORT PUBLIC_WEB_URL"
    @echo "Starting web at $PUBLIC_WEB_URL"
    cd web && poetry run python main.py

run_temporal:
    @echo "To use the .env file, first unset TEMPORAL_TASK_QUEUE TEMPORAL_CONNECTION_NAMESPACE TEMPORAL_CONNECTION_TARGET TEMPORAL_CONNECTION_MTLS_KEY_FILE TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE TEMPORAL_CONNECTION_WEB_PORT CALLER_API_PORT PUBLIC_WEB_URL"
    @echo "Starting Temporal worker and caller API on $CALLER_API_PORT"
    cd temporal-java && ./gradlew api --console=plain

run_temporal_go:
    @echo "To use the .env file, first unset TEMPORAL_TASK_QUEUE TEMPORAL_CONNECTION_NAMESPACE TEMPORAL_CONNECTION_TARGET TEMPORAL_CONNECTION_MTLS_KEY_FILE TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE TEMPORAL_CONNECTION_WEB_PORT CALLER_API_PORT PUBLIC_WEB_URL"
    @echo "Starting Temporal golang worker"
    cd temporal-go && go run ./worker/main.go

run_temporal_gostarter:
    @echo "To use the .env file, first unset TEMPORAL_TASK_QUEUE TEMPORAL_CONNECTION_NAMESPACE TEMPORAL_CONNECTION_TARGET TEMPORAL_CONNECTION_MTLS_KEY_FILE TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE TEMPORAL_CONNECTION_WEB_PORT CALLER_API_PORT PUBLIC_WEB_URL"
    @echo "Starting Temporal golang workflows"
    cd temporal-go && go run ./starter/main.go